package ua.battlesim.engine;

import java.util.ArrayList;
import java.util.List;

import ua.battlesim.api.Attack;
import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.WeatherModel;

/**
 * Test fake: records call order and applies kills to units when attacks
 * are configured with {@code killed=true}.
 */
final class FakeCombatSystem implements CombatSystem {

    final List<String> callLog = new ArrayList<>();
    private List<Attack> nextAttacks = List.of();

    void setNextAttacks(List<Attack> attacks) {
        this.nextAttacks = attacks;
    }

    @Override
    public List<Attack> resolve(int tick, List<UnitData> units, Terrain t, WeatherModel w) {
        callLog.add("combat:" + tick);
        // Apply kills to units (mimicking real combat system behaviour).
        for (Attack a : nextAttacks) {
            if (a.killed()) {
                for (UnitData u : units) {
                    if (u.id() == a.targetId()) {
                        u.setHealth(0);
                        u.setState(UnitState.DESTROYED);
                        u.setDestroyedTick(tick);
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(nextAttacks);
    }
}