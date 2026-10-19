package ua.battlesim.movement;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/** Test fake of the weather model: fixed movement, combat and visibility factors. */
final class FakeWeather implements WeatherModel {

    private final double speedFactor;
    private final double combatFactor;
    private final double visibility;

    FakeWeather(double speedFactor, double combatFactor, double visibility) {
        if (speedFactor < 0.0 || speedFactor > 1.0) {
            throw new IllegalArgumentException("speedFactor must be in [0,1]: " + speedFactor);
        }
        if (combatFactor < 0.0 || combatFactor > 1.0) {
            throw new IllegalArgumentException("combatFactor must be in [0,1]: " + combatFactor);
        }
        if (visibility <= 0.0) {
            throw new IllegalArgumentException("visibility must be > 0 (metres): " + visibility);
        }
        this.speedFactor = speedFactor;
        this.combatFactor = combatFactor;
        this.visibility = visibility;
    }

    /** @return the fully clear weather: no speed or combat penalty, 25 000 m visibility */
    static FakeWeather clear() {
        return new FakeWeather(1.0, 1.0, 25_000.0);
    }

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
        return speedFactor;
    }

    @Override
    public double visibility() {
        return visibility;
    }

    @Override
    public double combatFactor() {
        return combatFactor;
    }
}