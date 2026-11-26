package com.steelrain.weapon;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Every placeable weapon system. One core block + block entity class serves them all; the rest of the
 * full-size footprint (width x height x length in blocks, real-world scale) is filled with invisible part blocks.
 * Model space: origin at the rear-centre-bottom of the footprint, +Z towards the front, +Y up.
 */
public enum WeaponType {
    //                 id                   kind            min  max   fuel cool prim sec  W  H  L  yaw   pitch rest fire
    TOMAHAWK_LAUNCHER("tomahawk_launcher", Kind.LAUNCHER, 100, 6000, 500, 60, 4, 0, 3, 3, 9, 0f, 1.5f, 0f, 85f),
    ISKANDER_LAUNCHER("iskander_launcher", Kind.LAUNCHER, 150, 8000, 1000, 100, 2, 0, 3, 3, 13, 0f, 1.2f, 0f, 88f),
    HIMARS("himars", Kind.LAUNCHER, 60, 1200, 100, 12, 6, 0, 3, 3, 7, 3f, 2f, 0f, 50f),
    M109("m109", Kind.LAUNCHER, 40, 600, 50, 40, 32, 0, 3, 3, 7, 2.5f, 1.5f, 3f, 45f),
    SHAHED_LAUNCHER("shahed_launcher", Kind.LAUNCHER, 100, 4000, 0, 60, 5, 0, 3, 2, 5, 0f, 0f, 0f, 0f),
    GROUND_STATION("ground_station", Kind.LAUNCHER, 50, 10000, 0, 100, 1, 4, 3, 3, 6, 0f, 0f, 0f, 0f),
    ORLAN_CATAPULT("orlan_catapult", Kind.LAUNCHER, 50, 1500, 0, 100, 1, 0, 1, 1, 4, 0f, 0f, 0f, 0f),
    JAMMER("jammer", Kind.JAMMER, 0, 0, 0, 0, 0, 0, 3, 3, 3, 0f, 0f, 0f, 0f),
    TRENCH_JAMMER("trench_jammer", Kind.JAMMER, 0, 0, 0, 0, 0, 0, 1, 2, 1, 0f, 0f, 0f, 0f),
    KRASUKHA("krasukha", Kind.JAMMER, 0, 0, 0, 0, 0, 0, 3, 4, 11, 0f, 0f, 0f, 0f);

    public enum Kind { LAUNCHER, JAMMER }

    public final String id;
    public final Kind kind;
    private final int minRange;
    private final int maxRange;
    /** FE drawn per launch (aircraft bring their own figure). */
    public final int energyPerShot;
    /** Ticks between two shots. */
    public final int cooldown;
    public final int primaryLimit;
    public final int secondaryLimit;
    public final int width, height, length;
    /** Degrees per tick the turret turns / the barrel elevates. 0 = that axis is fixed. */
    public final float yawSpeed, pitchSpeed;
    public final float restPitch, firePitch;

    WeaponType(String id, Kind kind, int minRange, int maxRange, int fuelPerShot, int cooldown,
               int primaryLimit, int secondaryLimit, int width, int height, int length,
               float yawSpeed, float pitchSpeed, float restPitch, float firePitch) {
        this.id = id;
        this.kind = kind;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.energyPerShot = fuelPerShot * 250;
        this.cooldown = cooldown;
        this.primaryLimit = primaryLimit;
        this.secondaryLimit = secondaryLimit;
        this.width = width;
        this.height = height;
        this.length = length;
        this.yawSpeed = yawSpeed;
        this.pitchSpeed = pitchSpeed;
        this.restPitch = restPitch;
        this.firePitch = firePitch;
    }

    public int minRange() { return minRange; }

    public int maxRange() { return (int) Math.round(maxRange * Config.RANGE_MULTIPLIER.get()); }

    public boolean laptopControlled() { return kind == Kind.LAUNCHER; }

    public boolean launchesAircraft() { return this == GROUND_STATION || this == ORLAN_CATAPULT; }

    /** Everything with an engine or powered systems needs charging. Bare launch rails are worked by hand. */
    public boolean usesEnergy() {
        return this != SHAHED_LAUNCHER && this != ORLAN_CATAPULT;
    }

    /** Furthest this launcher can throw the given munition. */
    public int maxRangeFor(ItemStack ammo) {
        if (this == HIMARS && ammo.is(ModRegistry.ATACMS_MISSILE.get())) return (int) Math.round(3000 * Config.RANGE_MULTIPLIER.get());
        return maxRange();
    }

    public Supplier<? extends Item> primaryAmmo() {
        return switch (this) {
            case TOMAHAWK_LAUNCHER -> ModRegistry.TOMAHAWK_MISSILE;
            case ISKANDER_LAUNCHER -> ModRegistry.ISKANDER_MISSILE;
            case HIMARS -> ModRegistry.GMLRS_ROCKET;
            case M109 -> ModRegistry.SHELL_155MM;
            case SHAHED_LAUNCHER -> ModRegistry.SHAHED_136;
            case GROUND_STATION -> ModRegistry.MQ9_REAPER;
            case ORLAN_CATAPULT -> ModRegistry.ORLAN_10;
            case JAMMER, TRENCH_JAMMER, KRASUKHA -> null;
        };
    }

    public Supplier<? extends Item> secondaryAmmo() {
        return this == GROUND_STATION ? ModRegistry.HELLFIRE_MISSILE : null;
    }

    public boolean acceptsPrimary(ItemStack stack) {
        if (this == GROUND_STATION) {
            AircraftType a = AircraftType.of(stack);
            return a != null && a.runway > 0;
        }
        return switch (this) {
            case M109 -> stack.is(ModRegistry.SHELL_155MM.get()) || stack.is(ModRegistry.SHELL_CLUSTER.get())
                    || stack.is(ModRegistry.SHELL_SMOKE.get()) || stack.is(ModRegistry.SHELL_INCENDIARY.get());
            case HIMARS -> stack.is(ModRegistry.GMLRS_ROCKET.get()) || stack.is(ModRegistry.ATACMS_MISSILE.get());
            default -> {
                Supplier<? extends Item> s = primaryAmmo();
                yield s != null && stack.is(s.get());
            }
        };
    }

    public boolean acceptsSecondary(ItemStack stack) {
        Supplier<? extends Item> s = secondaryAmmo();
        return s != null && stack.is(s.get());
    }

    // ---- aiming geometry (model space) ----

    /** Axis the turret turns around. */
    public Vec3 turretPivot() {
        return switch (this) {
            case HIMARS -> new Vec3(0, 1.2, 2.0);
            case M109 -> new Vec3(0, 1.7, 2.2);
            default -> gunPivot();
        };
    }

    /** Hinge the barrel / pod / missile elevates around. */
    public Vec3 gunPivot() {
        return switch (this) {
            case HIMARS -> new Vec3(0, 1.7, 1.0);
            case M109 -> new Vec3(0, 2.3, 4.0);
            case TOMAHAWK_LAUNCHER -> new Vec3(0, 1.2, 0.6);
            case ISKANDER_LAUNCHER -> new Vec3(0, 2.2, 1.0);
            case SHAHED_LAUNCHER -> new Vec3(0, 0.4, 0.3);
            case ORLAN_CATAPULT -> new Vec3(0, 0.3, 0.2);
            default -> new Vec3(0, height, length / 2.0);
        };
    }

    /** Where rounds leave, relative to the gun pivot with the barrel level. */
    public Vec3 muzzle() {
        return switch (this) {
            case HIMARS -> new Vec3(0, 0.35, 3.6);
            case M109 -> new Vec3(0, 0, 5.3);
            case TOMAHAWK_LAUNCHER -> new Vec3(0, 0.9, 7.8);
            case ISKANDER_LAUNCHER -> new Vec3(0, 0.5, 8.2);
            case SHAHED_LAUNCHER -> new Vec3(0, 0.6, 4.6);
            case ORLAN_CATAPULT -> new Vec3(0, 0.4, 3.6);
            default -> Vec3.ZERO;
        };
    }

    /** Radius of the jamming bubble for jammer systems. */
    public double jamRadius() {
        return switch (this) {
            case JAMMER -> Config.JAMMER_RANGE.get();
            case TRENCH_JAMMER -> Config.TRENCH_JAMMER_RANGE.get();
            case KRASUKHA -> Config.STRATEGIC_JAMMER_RANGE.get();
            default -> 0;
        };
    }

    /** FE a running jammer draws every half second. */
    public int jamEnergy() { return this == KRASUKHA ? 1250 : 250; }

    /** Fixed incline of rails that do not move. */
    public float fixedPitch() {
        return this == SHAHED_LAUNCHER ? 18f : this == ORLAN_CATAPULT ? 15f : 0f;
    }

    public String translationKey() { return "block.steelrain." + id; }
}
