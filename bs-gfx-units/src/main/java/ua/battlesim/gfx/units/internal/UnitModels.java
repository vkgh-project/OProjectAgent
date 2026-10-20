package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.UnitType;

/**
 * The three cached procedural unit models, and a dispatcher from a
 * {@link UnitType} to its model.
 *
 * <p>The models are built once (pure CPU) and shared by every instance of the
 * matching unit type, so {@link #forType(UnitType)} is cheap to call per unit.
 *
 * <p>Immutable and thread-safe.
 */
public final class UnitModels {

    /** Tank: hull, two tracks, four road wheels, turret and gun. */
    public static final UnitModel TANK = TankModel.build();
    /** APC with 8 wheels, a turret and three seated soldiers. */
    public static final UnitModel APC = ApcModel.build();
    /** Infantry squad of four soldiers with walking legs. */
    public static final UnitModel INFANTRY = InfantryModel.build();

    private UnitModels() {
    }

    /**
     * @param type the unit class
     * @return the shared procedural model for that class
     */
    public static UnitModel forType(UnitType type) {
        switch (type) {
            case TANK:
                return TANK;
            case APC_INFANTRY:
                return APC;
            case INFANTRY:
                return INFANTRY;
            default:
                throw new IllegalArgumentException("unknown unit type: " + type);
        }
    }
}
