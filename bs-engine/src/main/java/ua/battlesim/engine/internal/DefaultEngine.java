package ua.battlesim.engine.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ua.battlesim.api.Attack;
import ua.battlesim.api.AttackEvent;
import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.Engine;
import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitDestroyedEvent;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherModel;

/**
 * The default deterministic engine implementation (internal to bs-engine).
 *
 * <p>Per tick sequence: advance tick, save prev values, weather.step,
 * combat.resolve + events, movement.step, check end conditions.
 *
 * <p>Ownership: the engine deep-copies the round's units (mutating the
 * {@link RoundSetup}'s unit list never happens), owns the combat and
 * movement systems handed to it by the engine factory and drives the round's
 * own weather model — an engine must therefore only interleave with another
 * engine that owns its own fresh round setup (fresh units and weather).
 *
 * <p>Events: one {@link AttackEvent} per shot; exactly one
 * {@link UnitDestroyedEvent} per alive-to-destroyed transition, with the
 * killer being the lowest attacker id among the unit's killers this tick.
 *
 * <p>Determinism: units processed in ascending id order; no wall clock.
 */
public final class DefaultEngine implements Engine {

    private final int roundNumber;
    private final SimConfig config;
    private final Terrain terrain;
    private final WeatherModel weather;
    private final List<UnitData> units;
    private final CombatSystem combatSystem;
    private final MovementSystem movementSystem;

    private int tick = 0;
    private boolean finished = false;
    private Outcome outcome = null;
    private final List<SimEvent> pendingEvents = new ArrayList<>();

    private final double[] prevX;
    private final double[] prevZ;
    private final double[] prevHeading;
    private final double[] prevAim;

    public DefaultEngine(RoundSetup setup, SimConfig config, CombatSystem combatSystem,
            MovementSystem movementSystem) {
        this.roundNumber = setup.number();
        this.config = config;
        this.terrain = setup.terrain();
        this.weather = setup.weather();
        this.combatSystem = combatSystem;
        this.movementSystem = movementSystem;

        this.units = new ArrayList<>(setup.units().size());
        for (UnitData source : setup.units()) {
            this.units.add(copyOf(source));
        }
        Collections.sort(this.units, (a, b) -> Integer.compare(a.id(), b.id()));

        int n = units.size();
        this.prevX = new double[n];
        this.prevZ = new double[n];
        this.prevHeading = new double[n];
        this.prevAim = new double[n];
        for (int i = 0; i < n; i++) {
            prevX[i] = units.get(i).x();
            prevZ[i] = units.get(i).z();
            prevHeading[i] = units.get(i).heading();
            prevAim[i] = units.get(i).aim();
        }
    }

    @Override
    public void tick() {
        if (finished) {
            throw new IllegalStateException("Engine is already finished (tick " + tick + ")");
        }

        tick++;

        for (int i = 0; i < units.size(); i++) {
            prevX[i] = units.get(i).x();
            prevZ[i] = units.get(i).z();
            prevHeading[i] = units.get(i).heading();
            prevAim[i] = units.get(i).aim();
        }

        weather.step(tick);

        if (tick > config.readyTicks()) {
            List<Attack> attacks = combatSystem.resolve(tick, units, terrain, weather);
            for (Attack a : attacks) {
                pendingEvents.add(
                        new AttackEvent(tick, a.attackerId(), a.targetId(), a.damage(), a.killed()));
            }
            // Exactly one destruction event per victim destroyed this tick,
            // killer = the lowest attacker id among its killers.
            int n = units.size();
            int[] killerFor = new int[n];
            java.util.Arrays.fill(killerFor, -1);
            for (Attack a : attacks) {
                if (!a.killed()) {
                    continue;
                }
                int idx = indexById(a.targetId());
                if (killerFor[idx] < 0 || a.attackerId() < killerFor[idx]) {
                    killerFor[idx] = a.attackerId();
                }
            }
            for (int i = 0; i < n; i++) {
                if (killerFor[i] >= 0) {
                    UnitData victim = units.get(i);
                    UnitData killer = findUnitById(killerFor[i]);
                    pendingEvents.add(new UnitDestroyedEvent(tick, victim.id(),
                            killerFor[i], killer.side()));
                }
            }
            movementSystem.step(tick, units, terrain, weather);
        }

        checkEnd();
    }

    private void checkEnd() {
        int blueAlive = 0;
        int redAlive = 0;
        for (UnitData u : units) {
            if (u.state() != UnitState.DESTROYED) {
                if (u.side() == Side.BLUE) {
                    blueAlive++;
                } else {
                    redAlive++;
                }
            }
        }

        if (blueAlive == 0 && redAlive == 0) {
            outcome = Outcome.DRAW;
            finished = true;
        } else if (blueAlive == 0) {
            outcome = Outcome.RED_WIN;
            finished = true;
        } else if (redAlive == 0) {
            outcome = Outcome.BLUE_WIN;
            finished = true;
        } else if (tick >= config.maxRoundTicks()) {
            outcome = Outcome.DRAW_TIME;
            finished = true;
        }
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public Outcome outcome() {
        if (!finished) {
            throw new IllegalStateException("Engine is not finished yet (tick " + tick + ")");
        }
        return outcome;
    }

    @Override
    public RenderSnapshot snapshot() {
        List<UnitView> views = new ArrayList<>(units.size());
        for (int i = 0; i < units.size(); i++) {
            UnitData u = units.get(i);
            views.add(new UnitView(
                    u.id(), u.side(), u.type(),
                    u.x(), u.z(),
                    prevX[i], prevZ[i],
                    u.heading(), prevHeading[i],
                    u.aim(), prevAim[i],
                    u.health(), u.state(),
                    u.odometer(), u.destroyedTick()));
        }
        double timeSeconds = tick * config.stepSeconds();
        return new RenderSnapshot(roundNumber, tick, timeSeconds, views, weather.state());
    }

    @Override
    public List<SimEvent> drainEvents() {
        List<SimEvent> result = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return result;
    }

    @Override
    public long stateHash() {
        long hash = 0xcbf29ce484222325L;
        String state = canonicalState();
        for (int i = 0; i < state.length(); i++) {
            hash ^= state.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private String canonicalState() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(tick).append('\0');
        for (UnitData u : units) {
            sb.append(u.id()).append(',')
              .append(u.side().ordinal()).append(',')
              .append(u.type().ordinal()).append(',')
              .append(u.x()).append(',')
              .append(u.z()).append(',')
              .append(u.heading()).append(',')
              .append(u.aim()).append(',')
              .append(u.health()).append(',')
              .append(u.state().ordinal()).append(',')
              .append(u.cooldown()).append(',')
              .append(u.odometer()).append(',')
              .append(u.destroyedTick()).append('\0');
        }
        var ws = weather.state();
        sb.append(ws.rain()).append(',')
          .append(ws.heavyRain()).append(',')
          .append(ws.snow()).append(',')
          .append(ws.windSpeed()).append(',')
          .append(ws.windDirection()).append(',')
          .append(ws.cloud()).append(',')
          .append(ws.wetness()).append(',')
          .append(ws.puddles()).append(',')
          .append(ws.snowCover()).append('\0');
        return sb.toString();
    }

    private UnitData findUnitById(int id) {
        for (UnitData u : units) {
            if (u.id() == id) {
                return u;
            }
        }
        throw new IllegalArgumentException("No unit with id " + id);
    }

    private int indexById(int id) {
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).id() == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("No unit with id " + id);
    }

    /** Copies a unit so the engine never mutates the setup's unit objects. */
    private static UnitData copyOf(UnitData u) {
        return new UnitData(u.id(), u.side(), u.type(), u.x(), u.z(), u.heading(), u.aim(),
                u.health(), u.state(), u.cooldown(), u.odometer(), u.destroyedTick());
    }

    // ---- package-private accessors for testing ----

    int currentTick() {
        return tick;
    }

    List<UnitData> unitList() {
        return units;
    }
}