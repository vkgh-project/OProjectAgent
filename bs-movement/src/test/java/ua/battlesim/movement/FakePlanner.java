package ua.battlesim.movement;

import java.util.List;
import java.util.Optional;

import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;

/**
 * Test fake of the path planner: returns either the straight segment
 * (from, to), a path through a fixed intermediate point (from, via, to), or
 * no path at all. After a configurable number of plan calls it can switch to
 * the straight path and run an on-switch action (simulating an opened road).
 */
final class FakePlanner implements PathPlanner {

    private boolean viaPoint;
    private Vec2 via;
    private boolean noPath;
    private int calls;
    private int switchAfterCalls = -1;
    private Runnable onSwitch;

    /** @return a planner that always returns the straight segment */
    static FakePlanner straight() {
        return new FakePlanner();
    }

    /** @return a planner that routes through the given intermediate point */
    static FakePlanner via(Vec2 via) {
        FakePlanner planner = new FakePlanner();
        planner.viaPoint = true;
        planner.via = via;
        return planner;
    }

    /** @return a planner that reports no path */
    static FakePlanner noPath() {
        FakePlanner planner = new FakePlanner();
        planner.noPath = true;
        return planner;
    }

    /** Switches to the straight path after the given number of plan calls. */
    void switchAfter(int calls, Runnable onSwitch) {
        this.switchAfterCalls = calls;
        this.onSwitch = onSwitch;
    }

    /** @return how often plan was called */
    int calls() {
        return calls;
    }

    @Override
    public Optional<List<Vec2>> plan(Vec2 from, Vec2 to, UnitType type, double radius) {
        calls++;
        if (switchAfterCalls >= 0 && calls > switchAfterCalls) {
            if (onSwitch != null) {
                onSwitch.run();
            }
            viaPoint = false;
            noPath = false;
        }
        if (noPath) {
            return Optional.empty();
        }
        if (viaPoint) {
            return Optional.of(List.of(from, via, to));
        }
        return Optional.of(List.of(from, to));
    }
}