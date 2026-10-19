package ua.battlesim.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import ua.battlesim.api.Attack;
import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitInfo;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WeatherModel;

/**
 * Standard deterministic combat rules (bs-api {@link CombatSystem}).
 *
 * <p>Behaviour per tick (units are always processed in ascending id order,
 * independent of the order the engine passes them in):
 * <ol>
 * <li>Every active unit (state not {@link UnitState#DESTROYED} and health
 *     &gt; 0) decrements its cooldown (clamped at 0) and picks a target:
 *     the nearest enemy (opposite side, active) whose distance is at most
 *     {@code min(UnitInfo.range(type) x weather.combatFactor(),
 *     weather.visibility())} and that is visible through
 *     {@code terrain.los()} (eye heights from {@link UnitInfo}); on an exact
 *     distance tie the lowest enemy id wins.</li>
 * <li>Units whose cooldown is now 0 shoot. All shots are collected first and
 *     their damage applied simultaneously (a unit killed this tick still
 *     shoots), each victim's health clamped at 0; a unit whose health
 *     reaches 0 becomes {@link UnitState#DESTROYED} with
 *     {@code destroyedTick = tick}. Every shooter's cooldown is then set to
 *     the attack interval, so consecutive shots of one unit are separated by
 *     exactly the interval ticks.</li>
 * <li>The result is the list of {@link Attack} records (with the
 *     {@code killed} flag), in ascending attacker-id order.</li>
 * </ol>
 *
 * <p>A unit that has a target is set to {@link UnitState#ATTACKING} and
 * turns its aim toward that target at 3 rad/s for tanks and 4 rad/s
 * otherwise (per tick: rate x 0.1 s, taking the shortest arc and snapping to
 * the target direction once within one step). Units without a target are not
 * touched in state (movement/waiting states set earlier in the tick are
 * preserved).
 *
 * <p>Damage per hit is looked up in the shared {@link UnitInfo} table
 * (attacker type x target type); this class never redefines the table.
 *
 * <p>Determinism: a pure function of its arguments; units are processed in
 * ascending id order; only {@link StrictMath} is used for transcendental
 * calls; no wall clock, no unseeded randomness, no hash-order dependence
 * (plain lists and arrays only).
 *
 * <p>Thread-safety: mutable only through the caller-owned {@link UnitData}
 * list; call {@link #resolve} from the single simulation worker thread,
 * after the movement step of the same tick.
 */
public final class StandardCombatSystem implements CombatSystem {

    /** Simulation step: time = tick x 0.1 s. */
    private static final double STEP_SECONDS = 0.1;

    /** Aim turn rate of a TANK in rad/s. */
    private static final double TANK_AIM_RATE = 3.0;

    /** Aim turn rate of every non-tank unit in rad/s. */
    private static final double OTHER_AIM_RATE = 4.0;

    private static final double TWO_PI = 2.0 * StrictMath.PI;

    /** Interval between consecutive shots of one unit, in ticks. */
    private final int attackIntervalTicks;

    /**
     * Builds the standard combat system with the default attack interval of
     * {@link SimConfig#defaults()} (20 ticks).
     */
    public StandardCombatSystem() {
        this(SimConfig.defaults().attackIntervalTicks());
    }

    /**
     * @param attackIntervalTicks the interval between consecutive shots of
     *            one unit in ticks, {@code >= 1}
     * @throws IllegalArgumentException if the interval is not {@code >= 1}
     */
    public StandardCombatSystem(int attackIntervalTicks) {
        if (attackIntervalTicks < 1) {
            throw new IllegalArgumentException(
                    "attackIntervalTicks must be >= 1 (ticks): " + attackIntervalTicks);
        }
        this.attackIntervalTicks = attackIntervalTicks;
    }

    /** @return the interval between consecutive shots of one unit in ticks */
    public int attackIntervalTicks() {
        return attackIntervalTicks;
    }

    @Override
    public List<Attack> resolve(int tick, List<UnitData> units, Terrain terrain, WeatherModel weather) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        Objects.requireNonNull(units, "units");
        for (UnitData unit : units) {
            Objects.requireNonNull(unit, "unit");
        }
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(weather, "weather");

        List<UnitData> ordered = new ArrayList<>(units);
        ordered.sort(Comparator.comparingInt(UnitData::id));
        LineOfSight los = terrain.los();

        // Phase 1: decrement cooldowns and pick a target for every active unit.
        // targets[i] is the index (in `ordered`) of the unit's target or -1.
        int n = ordered.size();
        int[] targets = new int[n];
        for (int i = 0; i < n; i++) {
            UnitData unit = ordered.get(i);
            targets[i] = -1;
            if (!isActive(unit)) {
                continue;
            }
            unit.setCooldown(Math.max(0, unit.cooldown() - 1));
            double range = Math.min(UnitInfo.range(unit.type()) * weather.combatFactor(),
                    weather.visibility());
            targets[i] = pickTargetIndex(unit, ordered, range, los);
        }

        // Phase 2: collect the shots of the ready units.
        List<Shot> shots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (targets[i] < 0 || ordered.get(i).cooldown() != 0) {
                continue;
            }
            UnitData attacker = ordered.get(i);
            UnitData victim = ordered.get(targets[i]);
            shots.add(new Shot(i, targets[i], UnitInfo.damage(attacker.type(), victim.type())));
        }

        // Phase 3: apply all damage simultaneously; clamp health at 0.
        int[] damage = new int[n];
        for (Shot shot : shots) {
            damage[shot.victim()] += shot.damage();
        }
        for (int i = 0; i < n; i++) {
            if (damage[i] == 0) {
                continue;
            }
            UnitData victim = ordered.get(i);
            int health = Math.max(0, victim.health() - damage[i]);
            victim.setHealth(health);
            if (health == 0) {
                victim.setState(UnitState.DESTROYED);
                victim.setDestroyedTick(tick);
            }
        }

        // Phase 4: aim and state of every unit that has a target (a unit
        // killed this tick keeps DESTROYED).
        for (int i = 0; i < n; i++) {
            if (targets[i] < 0) {
                continue;
            }
            UnitData unit = ordered.get(i);
            if (!isActive(unit)) {
                continue;
            }
            unit.setState(UnitState.ATTACKING);
            double rate = unit.type() == UnitType.TANK ? TANK_AIM_RATE : OTHER_AIM_RATE;
            aimToward(unit, ordered.get(targets[i]), rate * STEP_SECONDS);
        }

        // Phase 5: shooters reload; consecutive shots are exactly the
        // interval ticks apart.
        for (Shot shot : shots) {
            ordered.get(shot.attacker()).setCooldown(attackIntervalTicks);
        }

        List<Attack> attacks = new ArrayList<>(shots.size());
        for (Shot shot : shots) {
            UnitData victim = ordered.get(shot.victim());
            attacks.add(new Attack(ordered.get(shot.attacker()).id(), victim.id(),
                    shot.damage(), victim.health() == 0));
        }
        return List.copyOf(attacks);
    }

    /**
     * @param unit an active unit looking for a target
     * @param ordered all units in ascending id order
     * @param range the effective weapon range in metres
     * @param los the terrain line-of-sight queries
     * @return the index (in {@code ordered}) of the nearest visible enemy in
     *         range, or {@code -1}; exact distance ties resolve to the lowest
     *         enemy id (iteration is ascending, a replacement needs a
     *         strictly shorter distance)
     */
    private static int pickTargetIndex(UnitData unit, List<UnitData> ordered, double range, LineOfSight los) {
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double ux = unit.x();
        double uz = unit.z();
        double eye = UnitInfo.of(unit.type()).eyeHeight();
        for (int j = 0; j < ordered.size(); j++) {
            UnitData candidate = ordered.get(j);
            if (candidate == unit || candidate.side() == unit.side() || !isActive(candidate)) {
                continue;
            }
            double dx = candidate.x() - ux;
            double dz = candidate.z() - uz;
            double distance = StrictMath.hypot(dx, dz);
            if (distance > range || (best >= 0 && distance >= bestDistance)) {
                continue;
            }
            if (!los.visible(new Vec2(ux, uz), eye,
                    new Vec2(candidate.x(), candidate.z()), UnitInfo.of(candidate.type()).eyeHeight())) {
                continue;
            }
            best = j;
            bestDistance = distance;
        }
        return best;
    }

    /**
     * Turns {@code unit}'s aim toward {@code target} along the shortest arc,
     * by at most {@code maxStep} radians (snapping exactly to the target
     * direction once the remainder is within one step).
     */
    private static void aimToward(UnitData unit, UnitData target, double maxStep) {
        double desired = StrictMath.atan2(target.z() - unit.z(), target.x() - unit.x());
        double delta = normalizeAngle(desired - unit.aim());
        if (StrictMath.abs(delta) <= maxStep) {
            unit.setAim(desired);
        } else {
            unit.setAim(unit.aim() + StrictMath.copySign(maxStep, delta));
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

    /** @return whether the unit still acts (alive and not destroyed) */
    private static boolean isActive(UnitData unit) {
        return unit.state() != UnitState.DESTROYED && unit.health() > 0;
    }

    /** One resolved shot (indices into the ascending-id unit list). */
    private record Shot(int attacker, int victim, int damage) {
    }
}

