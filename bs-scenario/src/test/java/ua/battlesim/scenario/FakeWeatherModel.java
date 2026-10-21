package ua.battlesim.scenario;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/**
 * Deterministic test weather fake: its state is a pure function of the seed
 * it was constructed with (rain derived from the seed), so the tests can
 * verify that the scenario factory derives the weather seed from the round
 * seed.
 */
final class FakeWeatherModel implements WeatherModel {

    private final long seed;

    FakeWeatherModel(long seed) {
        this.seed = seed;
    }

    /** @return the seed this fake model was constructed with */
    long seed() {
        return seed;
    }

    @Override
    public void step(int tick) {
        // fixed state, nothing to advance
    }

    @Override
    public WeatherState state() {
        double rain = (seed % 97L) / 97.0;
        return new WeatherState(rain, 0.0, 0.0, 3.0, 0.0, rain, 0.0, 0.0, 0.0);
    }

    @Override
    public double speedFactor(double dirX, double dirZ) {
        return 1.0;
    }

    @Override
    public double visibilityMetres() {
        return 1000.0;
    }

    @Override
    public double combatFactor() {
        return 1.0;
    }
}