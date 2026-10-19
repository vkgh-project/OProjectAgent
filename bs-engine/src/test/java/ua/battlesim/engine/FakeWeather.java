package ua.battlesim.engine;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/**
 * Test fake: records step calls and returns a fixed clear weather state.
 */
final class FakeWeather implements WeatherModel {

    private final int[] steppedTicks = new int[0];
    private int lastTick = -1;
    private int stepCount = 0;

    @Override
    public void step(int tick) {
        this.lastTick = tick;
        this.stepCount++;
    }

    public int lastSteppedTick() {
        return lastTick;
    }

    public int stepCount() {
        return stepCount;
    }

    @Override
    public WeatherState state() {
        return new WeatherState(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public double speedFactor(double dirX, double dirZ) { return 1.0; }

    @Override
    public double visibility() { return 1.0; }

    @Override
    public double combatFactor() { return 1.0; }
}