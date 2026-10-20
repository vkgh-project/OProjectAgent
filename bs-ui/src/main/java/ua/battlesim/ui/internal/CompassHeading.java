package ua.battlesim.ui.internal;

/**
 * Converts a heading in radians to a Ukrainian compass direction string.
 *
 * <p>Heading convention: angle of the (cos, sin) vector in the (x, z) plane,
 * x east, z south. 0 = east, PI/2 = south, PI = west, 3*PI/2 = north.
 *
 * <p>Deterministic: no randomness, no wall clock.
 */
public final class CompassHeading {

    private CompassHeading() {}

    /**
     * Converts a heading in radians to an 8-point compass direction in Ukrainian.
     *
     * @param headingRadians heading in radians (0 = east, PI/2 = south, PI = west, 3*PI/2 = north)
     * @return the compass direction in Ukrainian
     * @throws IllegalArgumentException if the heading is not finite
     */
    public static String toCompass(double headingRadians) {
        if (!Double.isFinite(headingRadians)) {
            throw new IllegalArgumentException("headingRadians must be finite: " + headingRadians);
        }
        double angle = headingRadians % (2.0 * StrictMath.PI);
        if (angle < 0) {
            angle += 2.0 * StrictMath.PI;
        }
        // 8 sectors of 45 degrees each
        // 0: E (0 deg), 1: SE (45 deg), 2: S (90 deg), 3: SW (135 deg)
        // 4: W (180 deg), 5: NW (225 deg), 6: N (270 deg), 7: NE (315 deg)
        int sector = (int) Math.round(angle / (StrictMath.PI / 4.0)) % 8;
        return switch (sector) {
            case 0 -> "Схід";
            case 1 -> "Пд-Сх";
            case 2 -> "Південь";
            case 3 -> "Пд-Зх";
            case 4 -> "Захід";
            case 5 -> "Пн-Зх";
            case 6 -> "Північ";
            default -> "Пн-Сх";
        };
    }

    /**
     * Formats a heading in radians as degrees (0-359).
     *
     * @param headingRadians heading in radians
     * @return the heading in degrees, 0-359
     */
    public static int toDegrees(double headingRadians) {
        if (!Double.isFinite(headingRadians)) {
            throw new IllegalArgumentException("headingRadians must be finite: " + headingRadians);
        }
        int deg = (int) Math.round(headingRadians * 180.0 / StrictMath.PI);
        deg %= 360;
        if (deg < 0) {
            deg += 360;
        }
        return deg;
    }
}
