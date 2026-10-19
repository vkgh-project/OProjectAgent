package ua.battlesim.engine;

import java.util.ArrayList;
import java.util.List;

import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.WeatherModel;

/**
 * Test fake: records call order; optionally moves units by a fixed offset.
 */
final class FakeMovementSystem implements MovementSystem {

    final List<String> callLog = new ArrayList<>();
    private double moveOffset = 0.0;

    void setMoveOffset(double offset) {
        this.moveOffset = offset;
    }

    @Override
    public void step(int tick, List<UnitData> units, Terrain t, WeatherModel w) {
        callLog.add("movement:" + tick);
        if (moveOffset != 0.0) {
            for (UnitData u : units) {
                if (u.state() != ua.battlesim.api.UnitState.DESTROYED) {
                    u.setX(u.x() + moveOffset);
                }
            }
        }
    }
}