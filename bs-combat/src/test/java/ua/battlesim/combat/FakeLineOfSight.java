package ua.battlesim.combat;

import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.Vec2;

/** Test fake of the terrain line-of-sight: everything visible or nothing. */
final class FakeLineOfSight implements LineOfSight {

    private final boolean visible;

    FakeLineOfSight(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean visible(Vec2 from, double eyeFrom, Vec2 to, double eyeTo) {
        return visible;
    }
}