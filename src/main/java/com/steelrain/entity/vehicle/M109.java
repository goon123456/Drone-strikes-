package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * M109A6 Paladin: tracked 155 mm self-propelled howitzer. Seat 0 drives, seat 1 is the gunner up in the turret,
 * seat 2 a crewman. Three charges reach about 180, 350 and 600 blocks.
 */
public class M109 extends SrGun {
    private static final Vec3 TURRET_PIVOT = new Vec3(0, 1.7, -0.9), BARREL_PIVOT = new Vec3(0, 2.3, 0.9);
    private static final Vec3 MUZZLE = new Vec3(0, 2.3, 6.1);
    private static final Vec3[] SEATS = {new Vec3(-0.95, 0.5, 1.6), new Vec3(0.0, 1.58, -1.5), new Vec3(-0.5, 0.4, -2.0)};

    public M109(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override public float maxHealth() { return 500; }
    @Override public float tankLitres() { return 150; }
    @Override public int batteryFe() { return 200_000; }
    @Override protected Vec3[] seats() { return SEATS; }
    @Override public boolean tracked() { return true; }
    @Override protected double topSpeed() { return 0.5; }
    @Override protected double reverseSpeed() { return 0.2; }
    @Override protected double acceleration() { return 0.009; }
    @Override public double wheelRadius() { return 0.31; }
    @Override protected double length() { return 6.6; }
    @Override protected double width() { return 3.1; }
    @Override protected double litresPerBlock() { return 1.0 / 35; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.M109_ITEM.get()); }
    @Override public String modelName() { return "m109"; }

    @Override public int gunnerSeat() { return 1; }
    @Override public double[] charges() { return new double[]{3.0, 4.2, 5.5}; }
    @Override public double minElevation() { return -3; }
    @Override public double maxElevation() { return 70; }
    @Override protected float traverseRate() { return 2.5f; }
    @Override protected float elevateRate() { return 1.5f; }
    @Override protected Vec3 turretPivot() { return TURRET_PIVOT; }
    @Override protected Vec3 barrelPivot() { return BARREL_PIVOT; }
    @Override protected Vec3 muzzle() { return MUZZLE; }
    @Override protected int reloadTicks() { return 120; }
    @Override public int minRange() { return 40; }

    @Override
    public List<Item> shells() {
        return List.of(ModRegistry.SHELL_155MM.get(), ModRegistry.SHELL_CLUSTER.get(), ModRegistry.SHELL_SMOKE.get(), ModRegistry.SHELL_INCENDIARY.get());
    }

    @Override
    protected MissileProfile profileOf(ItemStack round) { return ModRegistry.profileOfAmmo(round, MissileProfile.SHELL); }

    @Override
    protected void fireEffects(ServerLevel level, Vec3 muzzle, Vec3 dir) {
        com.steelrain.util.Sfx.gunfire(level, muzzle, "plz_05_fire_3p", "plz_05_far", "plz_05_veryfar");
    }
}
