package ua.battlesim.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitInfo;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WeatherModel;

/**
 * Standard deterministic movement rules (bs-api {@link MovementSystem}).
 *
 * <p>Per tick (units are always processed in ascending id order, independent
 * of the order the engine passes them in):
 * <ol>
 * <li>Every active unit that is not {@link UnitState#ATTACKING} pursues a
 *     target: the nearest enemy (opposite side) on the same nav component
 *     for which the terrain planner has a path. The chosen target is kept
 *     until another enemy is at least 40 percent closer (hysteresis), so
 *     small distance changes do not make units flip-flop between targets.</li>
 * <li>The path is replanned when the unit first acquires or switches the
 *     target, when the target moved more than 150 m since the last plan, or
 *     when the unit's personal replan interval has elapsed. The interval is
 *     100-140 ticks (10-14 s), chosen deterministically from the unit id
 *     (100 + id % 5 x 10). Waypoints are considered reached within 6 m.</li>
 * <li>A unit within weapon range of its target (min of the type range x
 *     weather combat factor and the weather visibility, the same effective
 *     range the combat system uses) holds position
 *     ({@link UnitState#READY}); a unit without a reachable enemy holds
 *     position ({@link UnitState#WAITING}); a unit on a path is
 *     {@link UnitState#MOVING}.</li>
 * <li>Speed = baseSpeed x slope factor x road factor (1.35 for vehicles,
 *     1.1 for infantry, 1.0 off-road) x {@code weather.speedFactor} x
 *     alignment (max(0, cos(heading - move direction))). The heading turns
 *     toward the actual move direction at the unit type's turn rate
 *     ({@link UnitInfo}) per tick. The odometer accumulates the distance
 *     actually travelled (for animation).</li>
 * <li>Units never overlap each other (wrecks included): a step that would
 *     touch another unit is rejected and replaced by deflection angles up to
 *     77 degrees (7.7 degree steps, positive then negative alternately),
 *     then a full 360 degree sweep (36 steps of 10 degrees). The first
 *     collision-free, walkable candidate is taken; if none exists the unit
 *     holds its position this tick.</li>
 * <li>Stuck detection: less than 4 m of progress within 80 ticks triggers a
 *     replan that starts from the navigation cell centre; after 6 consecutive
 *     stuck windows the unit ignores wrecks when resolving overlaps.</li>
 * </ol>
 *
 * <p>Determinism: a pure function of its arguments; ascending id order;
 * only {@link StrictMath} for transcendental calls; no wall clock, no
 * unseeded randomness, no hash-order dependence (the per-unit motion map is
 * accessed by unit id only).
 *
 * <p>Thread-safety: mutable only through the caller-owned {@link UnitData}
 * list; call {@link #step} from the single simulation worker thread, before
 * the combat step of the same tick.
 */
public final class StandardMovementSystem implements MovementSystem {

    /** Simulation step: time = tick x 0.1 s. */
    private static final double STEP_SECONDS = 0.1;

    /** A waypoint is considered reached within this radius (metres). */
    private static final double ARRIVAL_RADIUS = 6.0;

    /** Switch to another enemy only when it is at most this factor x current distance away. */
    private static final double HYSTERISIS_FACTOR = 0.6;

    /** Replan when the target moved more than this since the last plan (metres). */
    private static final double TARGET_MOVE_REPLAN_METRES = 150.0;

    /** Shortest personal replan interval (ticks; 100 = 10 s). */
    private static final int REPLAN_MIN_TICKS = 100;

    /** Per-unit replan jitter step (ticks), derived deterministically from the unit id. */
    private static final int REPLAN_JITTER_TICKS = 10;

    /** Stuck window length (ticks). */
    private static final int STUCK_WINDOW_TICKS = 80;

    /** Progress below this within the window counts as stuck (metres). */
    private static final double STUCK_PROGRESS_METRES = 4.0;

    /** After this many consecutive stuck windows the unit ignores wrecks. */
    private static final int STUCK_REPEATS_IGNORE_WRECKS = 6;

    /** Largest deflection angle before the 360 degree sweep (radians). */
    private static final double MAX_DEFLECTION = StrictMath.toRadians(77.0);

    /** Number of deflection steps up to {@link #MAX_DEFLECTION}. */
    private static final int DEFLECTION_STEPS = 10;

    /** Number of steps of the full 360 degree sweep. */
    private static final int SWEEP_STEPS = 36;

    /** Road speed factor for vehicles (TANK, APC_INFANTRY). */
    private static final double ROAD_VEHICLE_FACTOR = 1.35;

    /** Road speed factor for INFANTRY. */
    private static final double ROAD_INFANTRY_FACTOR = 1.1;

    /** Ascending grade penalty: factor = max(0.5, 1 - grade x this). */
    private static final double SLOPE_PENALTY = 2.0;

    /** Minimum slope factor (at grades of 25 percent and steeper). */
    private static final double MIN_SLOPE_FACTOR = 0.5;

    private static final double TWO_PI = 2.0 * StrictMath.PI;

    /** Per-unit pursuit state, keyed by unit id (accessed by id only). */
    private final Map<Integer, Motion> motions = new HashMap<>();

    @Override
    public void step(int tick, List<UnitData> units, Terrain t, WeatherModel w) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(t, "terrain");
        Objects.requireNonNull(w, "weather");
        List<UnitData> ordered = new ArrayList<>(units);
        ordered.sort(Comparator.comparingInt(UnitData::id));
        dropStaleMotions(ordered);
        for (UnitData unit : ordered) {
            if (unit.state() == UnitState.ATTACKING || !isActive(unit)) {
                continue;
            }
            stepUnit(tick, unit, ordered, t, w);
        }
    }

    private void stepUnit(int tick, UnitData unit, List<UnitData> ordered, Terrain t, WeatherModel w) {
        NavGrid nav = t.nav();
        PathPlanner planner = t.planner();
        Motion m = motions.computeIfAbsent(unit.id(), id -> new Motion(unit));

        // 1) Resolve the pursuit target (hysteresis: keep it until another
        //    enemy is at least 40 percent closer and reachable).
        boolean targetChanged = false;
        UnitData target = currentTarget(m, ordered);
        if (target == null || !sameComponent(nav, unit, target)) {
            target = bestEnemy(unit, ordered, nav, planner, Double.POSITIVE_INFINITY);
            if (target != null) {
                m.setTarget(target.id());
                targetChanged = true;
            } else {
                m.clearTarget();
            }
        } else {
            double currentDistance = distance(unit.x(), unit.z(), target.x(), target.z());
            UnitData closer = bestEnemy(unit, ordered, nav, planner, currentDistance * HYSTERISIS_FACTOR);
            if (closer != null) {
                m.setTarget(closer.id());
                target = closer;
                targetChanged = true;
            }
        }
        if (target == null) {
            unit.setState(UnitState.WAITING);
            refreshStuckReference(unit, m, tick);
            return;
        }

        // 2) In weapon range: hold position (combat resolves the engagement).
        if (distance(unit.x(), unit.z(), target.x(), target.z()) <= stopRange(unit, w)) {
            unit.setState(UnitState.READY);
            refreshStuckReference(unit, m, tick);
            return;
        }

        // 3) (Re)plan when the target changed, when stuck, or on schedule.
        if (targetChanged) {
            m.clearWaypoints();
            refreshStuckReference(unit, m, tick);
            planDirectly(unit, target, m, tick, planner);
        } else if (stuckThisWindow(tick, unit, m)) {
            planFromCellCenter(nav, unit, target, m, tick, planner);
        } else if (replanDue(tick, target, m)) {
            planDirectly(unit, target, m, tick, planner);
        }
        if (m.waypoints.isEmpty()) {
            m.clearTarget();
            unit.setState(UnitState.WAITING);
            refreshStuckReference(unit, m, tick);
            return;
        }

        // 4) Advance along the path (turn rate, speed factors, no overlap).
        moveAlong(unit, m, ordered, t, w);
    }

    private void moveAlong(UnitData unit, Motion m, List<UnitData> ordered, Terrain t, WeatherModel w) {
        NavGrid nav = t.nav();
        while (m.wpIndex < m.waypoints.size()) {
            Vec2 wp = m.waypoints.get(m.wpIndex);
            double dx = wp.x() - unit.x();
            double dz = wp.z() - unit.z();
            double len = StrictMath.hypot(dx, dz);
            if (len <= ARRIVAL_RADIUS) {
                m.wpIndex++;
                continue;
            }
            double desired = StrictMath.atan2(dz, dx);
            boolean ignoreWrecks = m.stuckRepeats >= STUCK_REPEATS_IGNORE_WRECKS;
            Vec2 next = firstClearCandidate(unit, ordered, nav, t, w, desired, len, ignoreWrecks);
            if (next != null) {
                double ax = next.x() - unit.x();
                double az = next.z() - unit.z();
                double moved = StrictMath.hypot(ax, az);
                unit.setX(next.x());
                unit.setZ(next.z());
                unit.setOdometer(unit.odometer() + moved);
                if (moved > 0.0) {
                    turnToward(unit, StrictMath.atan2(az, ax));
                }
            }
            unit.setState(UnitState.MOVING);
            return;
        }
        unit.setState(UnitState.MOVING);
    }

    private static Vec2 firstClearCandidate(UnitData unit, List<UnitData> ordered, NavGrid nav, Terrain t,
            WeatherModel w, double desired, double maxStep, boolean ignoreWrecks) {
        double deflectionStep = MAX_DEFLECTION / DEFLECTION_STEPS;
        for (int k = 0; k <= DEFLECTION_STEPS; k++) {
            Vec2 c = candidatePosition(unit, ordered, nav, t, w,
                    desired + deflectionStep * k, desired, maxStep, ignoreWrecks);
            if (c != null) {
                return c;
            }
            if (k > 0) {
                c = candidatePosition(unit, ordered, nav, t, w,
                        desired - deflectionStep * k, desired, maxStep, ignoreWrecks);
                if (c != null) {
                    return c;
                }
            }
        }
        double sweepStep = TWO_PI / SWEEP_STEPS;
        for (int k = 1; k <= SWEEP_STEPS; k++) {
            Vec2 c = candidatePosition(unit, ordered, nav, t, w,
                    desired + sweepStep * k, desired, maxStep, ignoreWrecks);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static Vec2 candidatePosition(UnitData unit, List<UnitData> ordered, NavGrid nav, Terrain t,
            WeatherModel w, double angle, double desired, double maxStep, boolean ignoreWrecks) {
        double dx = StrictMath.cos(angle);
        double dz = StrictMath.sin(angle);
        double alignment = StrictMath.max(0.0, StrictMath.cos(desired - angle));
        if (alignment <= 0.0) {
            return null;
        }
        double base = UnitInfo.of(unit.type()).speed();
        double slope = slopeFactor(t, unit.x(), unit.z(), dx, dz, base * STEP_SECONDS);
        double road = roadFactor(nav, unit);
        double speed = base * slope * road * w.speedFactor(dx, dz) * alignment;
        if (speed <= 0.0) {
            return null;
        }
        double dist = StrictMath.min(speed * STEP_SECONDS, maxStep);
        double x = clamp(unit.x() + dx * dist, 0.0, t.size());
        double z = clamp(unit.z() + dz * dist, 0.0, t.size());
        if (!nav.walkable(x, z, UnitInfo.of(unit.type()).radius())) {
            return null;
        }
        if (overlaps(unit, x, z, ordered, ignoreWrecks)) {
            return null;
        }
        return new Vec2(x, z);
    }

    /** @return true if a unit of the mover's radius at (x, z) would touch another unit */
    private static boolean overlaps(UnitData unit, double x, double z, List<UnitData> ordered,
            boolean ignoreWrecks) {
        double r = UnitInfo.of(unit.type()).radius();
        for (UnitData other : ordered) {
            if (other == unit) {
                continue;
            }
            if (other.state() == UnitState.DESTROYED && ignoreWrecks) {
                continue;
            }
            if (StrictMath.hypot(x - other.x(), z - other.z()) <= r + UnitInfo.of(other.type()).radius()) {
                return true;
            }
        }
        return false;
    }

    /** @return the slope speed factor in [0.5, 1] (1 = flat or descending) */
    private static double slopeFactor(Terrain t, double x, double z, double dx, double dz, double step) {
        if (step <= 0.0) {
            return 1.0;
        }
        double grade = (t.heightAt(x + dx * step, z + dz * step) - t.heightAt(x, z)) / step;
        if (grade <= 0.0) {
            return 1.0;
        }
        return StrictMath.max(MIN_SLOPE_FACTOR, 1.0 - grade * SLOPE_PENALTY);
    }

    /** @return the road speed factor at the unit's position (1.0 off-road) */
    private static double roadFactor(NavGrid nav, UnitData unit) {
        if (!nav.isRoad(unit.x(), unit.z())) {
            return 1.0;
        }
        return unit.type() == UnitType.INFANTRY ? ROAD_INFANTRY_FACTOR : ROAD_VEHICLE_FACTOR;
    }

    /** The effective weapon range the unit stops at (the combat system's range). */
    private static double stopRange(UnitData unit, WeatherModel w) {
        return StrictMath.min(UnitInfo.of(unit.type()).range() * w.combatFactor(), w.visibility());
    }

    /** @return whether the unit still acts (alive and not destroyed) */
    private static boolean isActive(UnitData unit) {
        return unit.state() != UnitState.DESTROYED && unit.health() > 0;
    }

    /** Turns the unit's heading toward {@code desired} along the shortest arc. */
    private static void turnToward(UnitData unit, double desired) {
        double maxStep = UnitInfo.of(unit.type()).turnRate() * STEP_SECONDS;
        double delta = normalizeAngle(desired - unit.heading());
        if (StrictMath.abs(delta) <= maxStep) {
            unit.setHeading(desired);
        } else {
            unit.setHeading(unit.heading() + StrictMath.copySign(maxStep, delta));
        }
    }

    /** Normalizes an angle in radians to {@code (-pi, pi]}. */
    private static double normalizeAngle(double angle) {
        double a = angle % TWO_PI;
        if (a <= -StrictMath.PI) {
            a += TWO_PI;
        } else if (a > StrictMath.PI) {
            a -= TWO_PI;
        }
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        return v > hi ? hi : v;
    }

    private static double distance(double x1, double z1, double x2, double z2) {
        return StrictMath.hypot(x2 - x1, z2 - z1);
    }

    /**
     * @param maxDistance the largest distance at which an enemy is considered
     *        (all of them when {@link Double#POSITIVE_INFINITY})
     * @return the nearest reachable enemy within {@code maxDistance}, or null;
     *         distance ties resolve to the lowest enemy id
     */
    private static UnitData bestEnemy(UnitData unit, List<UnitData> ordered, NavGrid nav, PathPlanner planner,
            double maxDistance) {
        UnitData best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int ownComponent = componentOf(nav, unit);
        for (UnitData candidate : ordered) {
            if (candidate.side() == unit.side() || !isActive(candidate)) {
                continue;
            }
            if (componentOf(nav, candidate) != ownComponent) {
                continue;
            }
            double d = distance(unit.x(), unit.z(), candidate.x(), candidate.z());
            if (d > maxDistance || d >= bestDistance) {
                continue;
            }
            if (planner.plan(new Vec2(unit.x(), unit.z()), new Vec2(candidate.x(), candidate.z()),
                    unit.type(), UnitInfo.of(unit.type()).radius()).isEmpty()) {
                continue;
            }
            best = candidate;
            bestDistance = d;
        }
        return best;
    }

    /** @return the unit's current pursuit target from the ordered list, or null */
    private static UnitData currentTarget(Motion m, List<UnitData> ordered) {
        if (m.targetId < 0) {
            return null;
        }
        for (UnitData unit : ordered) {
            if (unit.id() == m.targetId) {
                return unit;
            }
        }
        return null;
    }

    private static boolean sameComponent(NavGrid nav, UnitData a, UnitData b) {
        return componentOf(nav, a) == componentOf(nav, b);
    }

    private static int componentOf(NavGrid nav, UnitData unit) {
        return nav.component(cellIndex(unit.x(), nav), cellIndex(unit.z(), nav));
    }

    private static int cellIndex(double coordinate, NavGrid nav) {
        int cells = nav.cells();
        int i = (int) StrictMath.floor(coordinate / nav.cellSize());
        if (i < 0) {
            return 0;
        }
        return i >= cells ? cells - 1 : i;
    }

    /** Plans a fresh path from the unit's position to the target. */
    private static void planDirectly(UnitData unit, UnitData target, Motion m, int tick, PathPlanner planner) {
        Optional<List<Vec2>> path = planner.plan(new Vec2(unit.x(), unit.z()), new Vec2(target.x(), target.z()),
                unit.type(), UnitInfo.of(unit.type()).radius());
        m.setWaypoints(path.orElse(List.of()));
        applyPlan(m, target, tick);
    }

    /** Plans a fresh path via the navigation cell centre (stuck recovery). */
    private static void planFromCellCenter(NavGrid nav, UnitData unit, UnitData target, Motion m, int tick,
            PathPlanner planner) {
        Vec2 center = new Vec2((cellIndex(unit.x(), nav) + 0.5) * nav.cellSize(),
                (cellIndex(unit.z(), nav) + 0.5) * nav.cellSize());
        Optional<List<Vec2>> path = planner.plan(center, new Vec2(target.x(), target.z()),
                unit.type(), UnitInfo.of(unit.type()).radius());
        List<Vec2> waypoints = new ArrayList<>(1 + path.orElse(List.of()).size());
        waypoints.add(center);
        waypoints.addAll(path.orElse(List.of()));
        m.setWaypoints(List.copyOf(waypoints));
        applyPlan(m, target, tick);
    }

    private static void applyPlan(Motion m, UnitData target, int tick) {
        m.wpIndex = 0;
        m.lastPlanTick = tick;
        m.targetSeenX = target.x();
        m.targetSeenZ = target.z();
    }

    private static boolean replanDue(int tick, UnitData target, Motion m) {
        if (tick - m.lastPlanTick >= m.replanEvery) {
            return true;
        }
        return distance(m.targetSeenX, m.targetSeenZ, target.x(), target.z()) > TARGET_MOVE_REPLAN_METRES;
    }

    /**
     * Checks the 80-tick stuck window and refreshes the reference position.
     *
     * @return true when the unit made less than 4 m of progress in the window
     */
    private static boolean stuckThisWindow(int tick, UnitData unit, Motion m) {
        if (tick - m.refTick < STUCK_WINDOW_TICKS) {
            return false;
        }
        double progress = distance(m.refX, m.refZ, unit.x(), unit.z());
        boolean stuck = progress < STUCK_PROGRESS_METRES;
        m.stuckRepeats = stuck ? m.stuckRepeats + 1 : 0;
        refreshStuckReference(unit, m, tick);
        return stuck;
    }

    private static void refreshStuckReference(UnitData unit, Motion m, int tick) {
        m.refX = unit.x();
        m.refZ = unit.z();
        m.refTick = tick;
    }

    private void dropStaleMotions(List<UnitData> ordered) {
        Iterator<Integer> it = motions.keySet().iterator();
        while (it.hasNext()) {
            int id = it.next();
            boolean present = false;
            for (UnitData unit : ordered) {
                if (unit.id() == id) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                it.remove();
            }
        }
    }

    /** Per-unit pursuit state (mutable, owned exclusively by this system). */
    private static final class Motion {

        private int targetId = -1;
        private double targetSeenX;
        private double targetSeenZ;
        private int lastPlanTick = Integer.MIN_VALUE / 2;
        private final int replanEvery;
        private List<Vec2> waypoints = List.of();
        private int wpIndex;
        private double refX;
        private double refZ;
        private int refTick;
        private int stuckRepeats;

        private Motion(UnitData unit) {
            this.replanEvery = REPLAN_MIN_TICKS + (unit.id() % 5) * REPLAN_JITTER_TICKS;
            this.refX = unit.x();
            this.refZ = unit.z();
        }

        private void setTarget(int id) {
            this.targetId = id;
        }

        private void clearTarget() {
            this.targetId = -1;
        }

        private void setWaypoints(List<Vec2> newWaypoints) {
            this.waypoints = newWaypoints;
        }

        private void clearWaypoints() {
            this.waypoints = List.of();
            this.wpIndex = 0;
        }
    }
}