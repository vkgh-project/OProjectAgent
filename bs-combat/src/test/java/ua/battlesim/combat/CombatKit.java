package ua.battlesim.combat;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/** Shared test helpers: unit builders and default fakes. */
final class CombatKit {

    private CombatKit() {
    }

    /**
     * A ready unit with full health and no cooldown.
     *
     * @param id the unit id
     * @param side the owning side
     * @param type the unit class
     * @param x position in metres (east)
     * @param z position in metres (south)
     * @return the unit (aim and heading 0 rad)
     */
    static UnitData unit(int id, Side side, UnitType type, double x, double z) {
        return new UnitData(id, side, type, x, z, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1);
    }

    /**
     * A destroyed unit (health 0, state DESTROYED, destroyedTick -1).
     *
     * @param id the unit id
     * @param side the owning side
     * @param type the unit class
     * @param x position in metres (east)
     * @param z position in metres (south)
     * @return the destroyed unit
     */
    static UnitData destroyed(int id, Side side, UnitType type, double x, double z) {
        UnitData unit = new UnitData(id, side, type, x, z, 0.0, 0.0, 0, UnitState.READY, 0, 0.0, -1);
        unit.setHealth(0);
        unit.setState(UnitState.DESTROYED);
        return unit;
    }

    /** @return the flat fake terrain where every line of sight is clear */
    static FakeTerrain clearTerrain() {
        return new FakeTerrain(new FakeLineOfSight(true));
    }

    /** @return the flat fake terrain where every line of sight is blocked */
    static FakeTerrain blockedTerrain() {
        return new FakeTerrain(new FakeLineOfSight(false));
    }
}