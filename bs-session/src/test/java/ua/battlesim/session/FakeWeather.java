package ua.battlesim.session;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/** Deterministic weather fake: fully clear, no penalties. */
final class FakeWeather implements WeatherModel {

    @Override
    public void step(int tick) {
        // fixed state, nothing to advance
    }

    @Override
    public WeatherState state() {
        return new WeatherState(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public double speedFactor(double dirX, double dirZ) {
        return 1.0;
    }

    @Override
    public double visibility() {
        return 1.0;
    }

    @Override
    public double combatFactor() {
        return 1.0;
    }
}