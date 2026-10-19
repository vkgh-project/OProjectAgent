package ua.battlesim.nav;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic fake terrain for bs-nav tests: optional linear ramp
 * (slope in m/m), a Gaussian hill, water rectangles, swamp rectangles and
 * an annular water ring (island maker). Flows are always still.
 */
final class FakeTerrain implements HeightField, WaterField {

    private final double size;
    private double slopeX;
    private double slopeZ;
    private double hillAmp;
    private double hillX;
    private double hillZ;
    private double hillSigma;
    private final List<double[]> waterRects = new ArrayList<>();
    private final List<double[]> swampRects = new ArrayList<>();
    private boolean ring;
    private double ringCx;
    private double ringCz;
    private double ringInner;
    private double ringOuter;

    FakeTerrain(double size) {
        this.size = size;
    }

    FakeTerrain slope(double x, double z) {
        this.slopeX = x;
        this.slopeZ = z;
        return this;
    }

    FakeTerrain hill(double amp, double cx, double cz, double sigma) {
        this.hillAmp = amp;
        this.hillX = cx;
        this.hillZ = cz;
        this.hillSigma = sigma;
        return this;
    }

    FakeTerrain water(double x0, double z0, double x1, double z1) {
        waterRects.add(new double[]{x0, z0, x1, z1});
        return this;
    }

    FakeTerrain swamp(double x0, double z0, double x1, double z1) {
        swampRects.add(new double[]{x0, z0, x1, z1});
        return this;
    }

    FakeTerrain waterRing(double cx, double cz, double inner, double outer) {
        this.ring = true;
        this.ringCx = cx;
        this.ringCz = cz;
        this.ringInner = inner;
        this.ringOuter = outer;
        return this;
    }

    @Override
    public double size() {
        return size;
    }

    @Override
    public double heightAt(double x, double z) {
        double h = slopeX * x + slopeZ * z;
        if (hillAmp > 0.0) {
            double dx = x - hillX;
            double dz = z - hillZ;
            h += hillAmp * StrictMath.exp(-(dx * dx + dz * dz) / (2.0 * hillSigma * hillSigma));
        }
        return h;
    }

    @Override
    public double depthAt(double x, double z) {
        for (double[] r : waterRects) {
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
                return 1.5;
            }
        }
        if (ring) {
            double d = StrictMath.hypot(x - ringCx, z - ringCz);
            if (d >= ringInner && d <= ringOuter) {
                return 2.0;
            }
        }
        return 0.0;
    }

    @Override
    public Vec2 flowAt(double x, double z) {
        return new Vec2(0.0, 0.0);
    }

    @Override
    public double swampAt(double x, double z) {
        for (double[] r : swampRects) {
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
                return 0.8;
            }
        }
        return 0.0;
    }
}
