package com.steelrain.weapon;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Every placeable weapon system. One block + block entity class serves them all. */
public enum WeaponType {
    TOMAHAWK_LAUNCHER("tomahawk_launcher", Kind.LAUNCHER, 100, 6000, 500, 60, 4, 0),
    ISKANDER_LAUNCHER("iskander_launcher", Kind.LAUNCHER, 150, 8000, 1000, 100, 2, 0),
    HIMARS("himars", Kind.LAUNCHER, 60, 1200, 100, 12, 6, 0),
    M109("m109", Kind.LAUNCHER, 40, 600, 50, 40, 32, 0),
    SHAHED_LAUNCHER("shahed_launcher", Kind.LAUNCHER, 100, 4000, 250, 60, 5, 0),
    REAPER_PAD("reaper_pad", Kind.LAUNCHER, 50, 3000, 1000, 100, 1, 4),
    PANTSIR("pantsir", Kind.AIR_DEFENCE, 0, 0, 0, 30, 12, 0),
    JAMMER("jammer", Kind.JAMMER, 0, 0, 0, 0, 0, 0);

    public enum Kind { LAUNCHER, AIR_DEFENCE, JAMMER }

    public final String id;
    public final Kind kind;
    private final int minRange;
    private final int maxRange;
    /** mB of fuel burned per launch. */
    public final int fuelPerShot;
    /** Ticks between two shots. */
    public final int cooldown;
    public final int primaryLimit;
    public final int secondaryLimit;

    WeaponType(String id, Kind kind, int minRange, int maxRange, int fuelPerShot, int cooldown,
               int primaryLimit, int secondaryLimit) {
        this.id = id;
        this.kind = kind;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.fuelPerShot = fuelPerShot;
        this.cooldown = cooldown;
        this.primaryLimit = primaryLimit;
        this.secondaryLimit = secondaryLimit;
    }

    public int minRange() { return minRange; }

    public int maxRange() { return (int) Math.round(maxRange * Config.RANGE_MULTIPLIER.get()); }

    public boolean laptopControlled() { return kind == Kind.LAUNCHER; }

    public Supplier<? extends Item> primaryAmmo() {
        return switch (this) {
            case TOMAHAWK_LAUNCHER -> ModRegistry.TOMAHAWK_MISSILE;
            case ISKANDER_LAUNCHER -> ModRegistry.ISKANDER_MISSILE;
            case HIMARS -> ModRegistry.GMLRS_ROCKET;
            case M109 -> ModRegistry.SHELL_155MM;
            case SHAHED_LAUNCHER -> ModRegistry.SHAHED_136;
            case REAPER_PAD -> ModRegistry.MQ9_REAPER;
            case PANTSIR -> ModRegistry.PANTSIR_MISSILE;
            case JAMMER -> null;
        };
    }

    public Supplier<? extends Item> secondaryAmmo() {
        return this == REAPER_PAD ? ModRegistry.HELLFIRE_MISSILE : null;
    }

    public boolean acceptsPrimary(ItemStack stack) {
        Supplier<? extends Item> s = primaryAmmo();
        return s != null && stack.is(s.get());
    }

    public boolean acceptsSecondary(ItemStack stack) {
        Supplier<? extends Item> s = secondaryAmmo();
        return s != null && stack.is(s.get());
    }

    public String translationKey() { return "block.steelrain." + id; }
}
