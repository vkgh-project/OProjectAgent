package ua.battlesim.api;

/**
 * Water on the map: depth, surface current and swamp.
 *
 * <p>Units: positions in metres (x east, z south); {@code depthAt} in metres
 * ({@code 0} = dry); {@code flowAt} as a current velocity in m/s;
 * {@code swampAt} in {@code [0, 1]} (1 = maximum slow-down).
 *
 * <p>Deterministic: the same coordinates always yield the same values.
 */
public interface WaterField {

    /**
     * @return the water depth in metres at (x, z); 0 when dry
     */
    double depthAt(double x, double z);

    /**
     * @return the surface current velocity at (x, z) in m/s; a zero vector
     *         when there is no flow
     */
    Vec2 flowAt(double x, double z);

    /**
     * @return the swamp factor at (x, z) in {@code [0, 1]}; 1 = fully swampy
     */
    double swampAt(double x, double z);
}
