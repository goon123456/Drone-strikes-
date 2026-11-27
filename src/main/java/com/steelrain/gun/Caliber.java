package com.steelrain.gun;

/**
 * A cartridge: how fast its bullet leaves a standard barrel, how quickly the air slows it, how hard it hits and how
 * much cover it goes through. Distances are in blocks (metres), times in ticks.
 */
public enum Caliber {
    //       name         m/s   drag/m   damage  wood it pierces (blocks)  pellets
    R545("5.45x39", "ammo_545", 880, 0.00130, 7.5f, 0.40f, 1),
    R556("5.56x45", "ammo_556", 890, 0.00145, 7.5f, 0.40f, 1),
    R762("7.62x54R", "ammo_762", 830, 0.00100, 12.5f, 1.10f, 1),
    P9("9x19", "ammo_9mm", 375, 0.00220, 5.0f, 0.12f, 1),
    G12("12 gauge", "ammo_12g", 400, 0.00900, 3.4f, 0.05f, 8),
    B50("12.7x99", "ammo_50", 853, 0.00050, 32f, 3.2f, 1),
    /** RPG-7 rockets: thrown out at 115 m/s, then the motor takes them to their flying speed. */
    PG7V("PG-7V HEAT", "rocket_pg7v", 115, 0.00010, 70f, 0f, 1, 295, 3.5f, 14f),
    OG7V("OG-7V frag", "rocket_og7v", 115, 0.00010, 18f, 0f, 1, 250, 7.5f, 24f),
    R76251("7.62x51", "ammo_76251", 838, 0.00105, 11.5f, 0.95f, 1),
    R76239("7.62x39", "ammo_76239", 715, 0.00160, 10.5f, 0.75f, 1),
    R58("5.8x42", "ammo_58", 930, 0.00130, 8.0f, 0.55f, 1),
    LAPUA338(".338 Lapua", "ammo_338", 900, 0.00060, 22f, 2.0f, 1),
    P57("5.7x28", "ammo_57", 715, 0.00170, 4.5f, 0.45f, 1),
    ACP45(".45 ACP", "ammo_45", 260, 0.00240, 7.5f, 0.07f, 1);

    /** Gravity in blocks per tick squared (9.81 m/s2). */
    public static final double GRAVITY = 9.81 / 400.0;

    public final String label, ammoId;
    public final int metresPerSecond, pellets;
    public final double drag;
    public final float damage, pierce;
    /** Rockets only: speed under power (m/s), and the radius and strength of the burst. Zero for bullets. */
    public final int cruise;
    public final float blastRadius, blastDamage;

    Caliber(String label, String ammoId, int mps, double drag, float damage, float pierce, int pellets) {
        this(label, ammoId, mps, drag, damage, pierce, pellets, 0, 0f, 0f);
    }

    Caliber(String label, String ammoId, int mps, double drag, float damage, float pierce, int pellets, int cruise, float blastRadius, float blastDamage) {
        this.cruise = cruise; this.blastRadius = blastRadius; this.blastDamage = blastDamage;
        this.label = label; this.ammoId = ammoId; this.metresPerSecond = mps; this.drag = drag;
        this.damage = damage; this.pierce = pierce; this.pellets = pellets;
    }

    /**
     * How much one solid hit takes off a vehicle's armour (before the damage config scale). Armour shrugs off
     * ordinary rifle and pistol rounds; only the heavy anti-materiel round bites deep. Rockets are zero here and
     * do their work through the warhead instead.
     */
    public float vehicleDamage() {
        return switch (this) {
            case B50 -> 26f;
            case LAPUA338 -> 9f;
            case R762, R76251, R76239 -> 4f;
            case R545, R556, R58 -> 1.5f;
            case P57 -> 1f;
            case G12 -> 1f;
            case P9 -> 0.5f;
            default -> 0f;
        };
    }

    /** Muzzle velocity in blocks per tick. */
    public double speed() { return metresPerSecond / 20.0; }

    public boolean rocket() { return cruise > 0; }

    /** How many of these stack. */
    public int stack() { return rocket() ? 4 : 64; }

    /** The velocity one tick later: air drag takes a fixed share per metre flown, gravity pulls down. */
    public net.minecraft.world.phys.Vec3 advance(net.minecraft.world.phys.Vec3 v) {
        double speed = v.length();
        double keep = Math.exp(-drag * speed);
        // a rocket's motor pushes it along its path until it is up to speed, and holds it there
        if (cruise > 0 && speed > 1e-6 && speed < cruise / 20.0) keep = Math.min(cruise / 20.0, speed + 0.9) / speed;
        return new net.minecraft.world.phys.Vec3(v.x * keep, v.y * keep - GRAVITY, v.z * keep);
    }

    /**
     * How far above the line of sight (radians) the barrel must point for the bullet to cross it again at
     * {@code range} blocks, found by flying the same steps the real bullet does.
     */
    public double elevation(double range, double velocityFactor) {
        long key = Math.round(range) * 1000 + Math.round(velocityFactor * 100);
        Double known = ELEVATION[ordinal()].get(key);
        if (known != null) return known;
        double lo = 0, hi = Math.toRadians(8);
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(range, mid, velocityFactor) < 0) lo = mid; else hi = mid;
        }
        ELEVATION[ordinal()].put(key, hi);
        return hi;
    }

    /** Height of the bullet relative to the line of sight when it has gone {@code range} blocks forward. */
    public double heightAt(double range, double angle, double velocityFactor) {
        double s = speed() * velocityFactor, x = 0, y = 0;
        net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(Math.cos(angle) * s, Math.sin(angle) * s, 0);
        for (int t = 0; t < 400; t++) {
            if (x + v.x >= range) return y + v.y * (range - x) / v.x;
            x += v.x; y += v.y;
            v = advance(v);
            if (v.x < 0.5) break;
        }
        return -1e6;
    }

    /** Remaining speed (m/s) and time of flight (s) at a range, for the tooltips and the range card. */
    public double[] at(double range, double velocityFactor) {
        double s = speed() * velocityFactor, x = 0;
        int t = 0;
        while (x < range && t < 400 && s > 0.5) { x += s; s *= Math.exp(-drag * s); t++; }
        return new double[]{s * 20, t / 20.0};
    }

    @SuppressWarnings("unchecked")
    private static final java.util.Map<Long, Double>[] ELEVATION = new java.util.Map[values().length];
    static { for (int i = 0; i < ELEVATION.length; i++) ELEVATION[i] = new java.util.concurrent.ConcurrentHashMap<>(); }
}
