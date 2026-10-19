package ua.battlesim.api;

import static java.lang.Double.isFinite;

/**
 * Static per-unit-type parameters: physical size, kinematics, eye height,
 * weapon range and the damage table.
 *
 * <p>Units: radius, eyeHeight and range in metres; speed in m/s; turnRate in
 * rad/s; damage in health points per hit ({@code [0, 100]}).
 *
 * <p>Immutable and thread-safe. The values are part of the shared contract
 * (see the bs-api README); implementations must look them up here instead of
 * redefining their own tables.
 */
public record UnitInfo(UnitType type, double radius, double speed, double turnRate, double eyeHeight,
        double range, int damageVsTank, int damageVsInfantry, int damageVsApcInfantry) {

    /**
     * Standard parameters of a TANK: 2.0 m radius, 14 m/s, 0.5 rad/s turn
     * rate, 2.5 m eye height, 500 m range, damage 20 (tank) / 50 (infantry) /
     * 25 (APC infantry) per hit.
     */
    public static final UnitInfo TANK = new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 2.5, 500.0, 20, 50, 25);

    /**
     * Standard parameters of an INFANTRY: 0.5 m radius, 2 m/s, 2.0 rad/s turn
     * rate, 1.7 m eye height, 200 m range, damage 10 (tank) / 50 (infantry) /
     * 20 (APC infantry) per hit.
     */
    public static final UnitInfo INFANTRY = new UnitInfo(UnitType.INFANTRY, 0.5, 2.0, 2.0, 1.7, 200.0, 10, 50, 20);

    /**
     * Standard parameters of an APC_INFANTRY: 2.0 m radius, 10 m/s, 0.4
     * rad/s turn rate, 2.2 m eye height, 300 m range, damage 10 (tank) / 50
     * (infantry) / 20 (APC infantry) per hit.
     */
    public static final UnitInfo APC_INFANTRY = new UnitInfo(UnitType.APC_INFANTRY, 2.0, 10.0, 0.4, 2.2, 300.0, 10, 50, 20);

    public UnitInfo {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (!isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and > 0 (metres): " + radius);
        }
        if (!isFinite(speed) || speed < 0.0) {
            throw new IllegalArgumentException("speed must be finite and >= 0 (m/s): " + speed);
        }
        if (!isFinite(turnRate) || turnRate < 0.0) {
            throw new IllegalArgumentException("turnRate must be finite and >= 0 (rad/s): " + turnRate);
        }
        if (!isFinite(eyeHeight) || eyeHeight <= 0.0) {
            throw new IllegalArgumentException("eyeHeight must be finite and > 0 (metres): " + eyeHeight);
        }
        if (!isFinite(range) || range <= 0.0) {
            throw new IllegalArgumentException("range must be finite and > 0 (metres): " + range);
        }
        if (damageVsTank < 0 || damageVsTank > 100) {
            throw new IllegalArgumentException("damageVsTank must be in [0,100]: " + damageVsTank);
        }
        if (damageVsInfantry < 0 || damageVsInfantry > 100) {
            throw new IllegalArgumentException("damageVsInfantry must be in [0,100]: " + damageVsInfantry);
        }
        if (damageVsApcInfantry < 0 || damageVsApcInfantry > 100) {
            throw new IllegalArgumentException("damageVsApcInfantry must be in [0,100]: " + damageVsApcInfantry);
        }
    }

    /**
     * @param type the unit class (non-null)
     * @return the standard parameters of the given unit type
     */
    public static UnitInfo of(UnitType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return switch (type) {
            case TANK -> TANK;
            case INFANTRY -> INFANTRY;
            case APC_INFANTRY -> APC_INFANTRY;
        };
    }

    /**
     * @param type the unit class (non-null)
     * @return the weapon range in metres of the given unit type
     */
    public static double range(UnitType type) {
        return of(type).range();
    }

    /**
     * Base damage in health points per hit that {@code attacker} deals to
     * {@code target}:
     *
     * <table border="1">
     * <caption>Damage table (attacker &rarr; target)</caption>
     * <tr><td>&nbsp;</td><td>TANK</td><td>INFANTRY</td><td>APC_INFANTRY</td></tr>
     * <tr><td>TANK</td><td>20</td><td>50</td><td>25</td></tr>
     * <tr><td>INFANTRY</td><td>10</td><td>50</td><td>20</td></tr>
     * <tr><td>APC_INFANTRY</td><td>10</td><td>50</td><td>20</td></tr>
     * </table>
     *
     * @param attacker the attacking unit class (non-null)
     * @param target   the target unit class (non-null)
     * @return the base damage in health points per hit
     */
    public static int damage(UnitType attacker, UnitType target) {
        if (attacker == null) {
            throw new IllegalArgumentException("attacker must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        UnitInfo info = of(attacker);
        return switch (target) {
            case TANK -> info.damageVsTank();
            case INFANTRY -> info.damageVsInfantry();
            case APC_INFANTRY -> info.damageVsApcInfantry();
        };
    }
}
