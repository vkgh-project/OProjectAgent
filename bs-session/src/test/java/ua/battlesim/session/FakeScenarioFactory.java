package ua.battlesim.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.ScenarioFactory;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.WeatherModel;

/** Scenario factory fake that records which rounds were created. */
final class FakeScenarioFactory implements ScenarioFactory {

    final List<Integer> created = Collections.synchronizedList(new ArrayList<>());
    private final Terrain terrain;
    private final WeatherModel weather;

    FakeScenarioFactory(Terrain terrain, WeatherModel weather) {
        this.terrain = terrain;
        this.weather = weather;
    }

    @Override
    public RoundSetup create(long masterSeed, int roundNumber) {
        created.add(roundNumber);
        List<UnitData> units = List.of(
                new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                        UnitState.READY, 0, 0.0, -1),
                new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, 0.0, 0.0, 100,
                        UnitState.READY, 0, 0.0, -1));
        return new RoundSetup(roundNumber, masterSeed + roundNumber, terrain, units, weather);
    }

    List<Integer> created() {
        synchronized (created) {
            return List.copyOf(created);
        }
    }
}