package ua.battlesim.api;

import static java.lang.Double.isFinite;

/**
 * The mutable per-unit state owned exclusively by the engine (consumed by
 * {@link CombatSystem} and {@link MovementSystem}).
 *
 * <p>Units: x, z, odometer in metres; heading and aim in radians (angle of
 * the (cos, sin) vector in the (x, z) plane, x east, z south); health in
 * {@code [0, 100]}; cooldown and destroyedTick in ticks
 * ({@code destroyedTick} is {@code -1} while the unit is alive).
 *
 * <p>Thread-safety: all fields are only touched on the single simulation
 * worker thread; the engine publishes immutable {@link UnitView} copies to
 * the render thread.
 *
 * <p>The id is immutable (units are processed in ascending id order); every
 * other field has a validating setter that throws
 * {@link IllegalArgumentException} on invalid values.
 */
public final class UnitData {

    private final int id;
    private Side side;
    private UnitType type;
    private double x;
    private double z;
    private double heading;
    private double aim;
    private int health;
    private UnitState state;
    private int cooldown;
    private double odometer;
    private int destroyedTick;

    /**
     * @param id            the immutable unit id, {@code >= 0}
     * @param side          the owning side (non-null)
     * @param type          the unit class (non-null)
     * @param x             position in metres (east), finite
     * @param z             position in metres (south), finite
     * @param heading       heading in radians, finite
     * @param aim           aim in radians, finite
     * @param health        health in {@code [0, 100]}
     * @param state         initial state (non-null)
     * @param cooldown      remaining cooldown in ticks, {@code >= 0}
     * @param odometer      distance travelled in metres, {@code >= 0}, finite
     * @param destroyedTick destruction tick in ticks, {@code >= -1}
     */
    public UnitData(int id, Side side, UnitType type, double x, double z,
            double heading, double aim, int health, UnitState state, int cooldown,
            double odometer, int destroyedTick) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        this.id = id;
        setSide(side);
        setType(type);
        setX(x);
        setZ(z);
        setHeading(heading);
        setAim(aim);
        setHealth(health);
        setState(state);
        setCooldown(cooldown);
        setOdometer(odometer);
        setDestroyedTick(destroyedTick);
    }

    /** @return the immutable unit id */
    public int id() {
        return id;
    }

    /** @return the owning side */
    public Side side() {
        return side;
    }

    /** @return the unit class */
    public UnitType type() {
        return type;
    }

    /** @return the x position in metres (east) */
    public double x() {
        return x;
    }

    /** @return the z position in metres (south) */
    public double z() {
        return z;
    }

    /** @return the heading in radians */
    public double heading() {
        return heading;
    }

    /** @return the aim in radians */
    public double aim() {
        return aim;
    }

    /** @return the health in {@code [0, 100]} */
    public int health() {
        return health;
    }

    /** @return the current state */
    public UnitState state() {
        return state;
    }

    /** @return the remaining cooldown in ticks */
    public int cooldown() {
        return cooldown;
    }

    /** @return the distance travelled in metres */
    public double odometer() {
        return odometer;
    }

    /** @return the destruction tick in ticks, {@code -1} while alive */
    public int destroyedTick() {
        return destroyedTick;
    }

    public void setSide(Side side) {
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        this.side = side;
    }

    public void setType(UnitType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
    }

    public void setX(double x) {
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite (metres): " + x);
        }
        this.x = x;
    }

    public void setZ(double z) {
        if (!isFinite(z)) {
            throw new IllegalArgumentException("z must be finite (metres): " + z);
        }
        this.z = z;
    }

    public void setHeading(double heading) {
        if (!isFinite(heading)) {
            throw new IllegalArgumentException("heading must be finite (radians): " + heading);
        }
        this.heading = heading;
    }

    public void setAim(double aim) {
        if (!isFinite(aim)) {
            throw new IllegalArgumentException("aim must be finite (radians): " + aim);
        }
        this.aim = aim;
    }

    public void setHealth(int health) {
        if (health < 0 || health > 100) {
            throw new IllegalArgumentException("health must be in [0,100]: " + health);
        }
        this.health = health;
    }

    public void setState(UnitState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
    }

    public void setCooldown(int cooldown) {
        if (cooldown < 0) {
            throw new IllegalArgumentException("cooldown must be >= 0 (ticks): " + cooldown);
        }
        this.cooldown = cooldown;
    }

    public void setOdometer(double odometer) {
        if (!isFinite(odometer) || odometer < 0.0) {
            throw new IllegalArgumentException("odometer must be finite and >= 0 (metres): " + odometer);
        }
        this.odometer = odometer;
    }

    public void setDestroyedTick(int destroyedTick) {
        if (destroyedTick < -1) {
            throw new IllegalArgumentException("destroyedTick must be >= -1 (ticks): " + destroyedTick);
        }
        this.destroyedTick = destroyedTick;
    }
}
