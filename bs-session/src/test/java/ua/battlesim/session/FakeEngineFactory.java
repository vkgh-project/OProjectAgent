package ua.battlesim.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherModel;

/** Engine factory fake that records which rounds were started. */
final class FakeEngineFactory implements EngineFactory {

    final List<Integer> started = Collections.synchronizedList(new ArrayList<>());
    private final WeatherModel weather;
    private final IntFunction<Integer> finishAfterByRound;
    private final IntFunction<Outcome> outcomeByRound;
    private final List<UnitView> units;

    FakeEngineFactory(WeatherModel weather, IntFunction<Integer> finishAfterByRound,
            IntFunction<Outcome> outcomeByRound, List<UnitView> units) {
        this.weather = weather;
        this.finishAfterByRound = finishAfterByRound;
        this.outcomeByRound = outcomeByRound;
        this.units = units;
    }

    @Override
    public Engine start(RoundSetup setup, SimConfig cfg) {
        started.add(setup.number());
        return new FakeEngine(setup.number(), weather,
                finishAfterByRound.apply(setup.number()), outcomeByRound.apply(setup.number()), units);
    }

    List<Integer> started() {
        synchronized (started) {
            return List.copyOf(started);
        }
    }
}