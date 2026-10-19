package ua.battlesim.session;

import java.util.List;

import ua.battlesim.api.Engine;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherModel;

/** Engine fake that finishes after a configurable number of ticks. */
final class FakeEngine implements Engine {

    private final int round;
    private final WeatherModel weather;
    private final int finishAfter;
    private final Outcome outcome;
    private final List<UnitView> units;
    private int tick;

    FakeEngine(int round, WeatherModel weather, int finishAfter, Outcome outcome, List<UnitView> units) {
        this.round = round;
        this.weather = weather;
        this.finishAfter = finishAfter;
        this.outcome = outcome;
        this.units = units;
    }

    @Override
    public void tick() {
        tick++;
    }

    @Override
    public boolean finished() {
        return tick >= finishAfter;
    }

    @Override
    public Outcome outcome() {
        return outcome;
    }

    @Override
    public RenderSnapshot snapshot() {
        return new RenderSnapshot(round, tick, tick * 0.1, units, weather.state());
    }

    @Override
    public List<SimEvent> drainEvents() {
        return List.of();
    }

    @Override
    public long stateHash() {
        return round * 1_000_003L + tick;
    }
}