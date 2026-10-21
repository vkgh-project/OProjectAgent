package ua.battlesim.api;

import java.util.List;
import java.util.Optional;

/**
 * Minimal deterministic fakes of the neighbouring-module contracts used by
 * the bs-api tests (bs-terrain and bs-weather live in other modules).
 */
final class TestFakes {

    private TestFakes() {
    }

    /** A flat, empty, dry 2000 m terrain fake. */
    static Terrain terrain() {
        return new Terrain() {
            public double size() {
                return 2000.0;
            }

            public double heightAt(double x, double z) {
                return 0.0;
            }

            public WaterField water() {
                return new WaterField() {
                    public double depthAt(double x, double z) {
                        return 0.0;
                    }

                    public Vec2 flowAt(double x, double z) {
                        return new Vec2(0.0, 0.0);
                    }

                    public double swampAt(double x, double z) {
                        return 0.0;
                    }
                };
            }

            public NavGrid nav() {
                throw new UnsupportedOperationException("not needed by bs-api tests");
            }

            public LineOfSight los() {
                throw new UnsupportedOperationException("not needed by bs-api tests");
            }

            public PathPlanner planner() {
                return (from, to, type, radius) -> Optional.of(List.of(from, to));
            }

            public List<Road> roads() {
                return List.of();
            }

            public List<Bridge> bridges() {
                return List.of();
            }

            public List<Prop> trees() {
                return List.of();
            }

            public List<Prop> rocks() {
                return List.of();
            }

            public double[] sunDirection() {
                return new double[] { 0.0, 1.0, 0.0 };
            }

            public double shadeAt(double x, double z) {
                return 1.0;
            }
        };
    }

    /** A clear, calm, dry weather fake. */
    static WeatherModel weather() {
        return new WeatherModel() {
            public void step(int tick) {
                // no-op
            }

            public WeatherState state() {
                return new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            }

            public double speedFactor(double dirX, double dirZ) {
                return 1.0;
            }

            public double visibilityMetres() {
                return 1000.0;
            }

            public double combatFactor() {
                return 1.0;
            }
        };
    }
}
