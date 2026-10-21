package ua.battlesim.combat;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/**
 * Test fake of the weather module: fixed combat factor and visibility,
 * no state changes.
 */
final class FakeWeather implements WeatherModel {

    private final double combatFactor;
    private final double visibility;

    FakeWeather(double combatFactor, double visibility) {
        if (combatFactor < 0.0 || combatFactor > 1.0) {
            throw new IllegalArgumentException("combatFactor must be in [0,1]: " + combatFactor);
        }
        if (visibility <= 0.0) {
            throw new IllegalArgumentException("visibility must be > 0 (metres): " + visibility);
        }
        this.combatFactor = combatFactor;
        this.visibility = visibility;
    }

    /** The fully clear weather: no combat penalty, 25 000 m visibility. */
    static FakeWeather clear() {
        return new FakeWeather(1.0, 25_000.0);
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
        return 1.0;
    }

    @Override
    public double visibilityMetres() {
        return visibility;
    }

    @Override
    public double combatFactor() {
        return combatFactor;
    }
}