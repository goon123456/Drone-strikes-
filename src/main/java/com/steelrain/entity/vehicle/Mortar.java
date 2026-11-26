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
 * 120 mm mortar: baseplate, bipod and tube, worked by one crewman kneeling beside it. High-angle fire only
 * (45 to 85 degrees); three charges reach about 80, 180 and 400 blocks. No engine, no battery: carry it.
 */
public class Mortar extends SrGun {
    private static final Vec3 TURRET_PIVOT = new Vec3(0, 0.1, -0.1), BARREL_PIVOT = new Vec3(0, 0.22, -0.1);
    private static final Vec3 MUZZLE = new Vec3(0, 0.22, 1.6);
    private static final Vec3[] SEATS = {new Vec3(0.75, -0.45, -0.55)};

    public Mortar(EntityType<?> type, Level level) {
        super(type, level);
        setGunPitch(45);
    }

    @Override public float maxHealth() { return 40; }
    @Override public float tankLitres() { return 0; }
    @Override public int batteryFe() { return 0; }
    @Override protected Vec3[] seats() { return SEATS; }
    @Override public boolean drivable() { return false; }
    @Override public float cameraDistance() { return 4f; }
    @Override public boolean hidesSeat(int seat) { return false; }
    @Override protected double length() { return 1.2; }
    @Override protected double width() { return 1.0; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.MORTAR_ITEM.get()); }
    @Override public String modelName() { return "mortar_120"; }

    @Override public int gunnerSeat() { return 0; }
    @Override public double[] charges() { return new double[]{2.0, 3.0, 4.5}; }
    @Override public double minElevation() { return 45; }
    @Override public double maxElevation() { return 85; }
    @Override protected float traverseRate() { return 3f; }
    @Override protected float elevateRate() { return 2f; }
    @Override protected Vec3 turretPivot() { return TURRET_PIVOT; }
    @Override protected Vec3 barrelPivot() { return BARREL_PIVOT; }
    @Override protected Vec3 muzzle() { return MUZZLE; }
    @Override protected int reloadTicks() { return 60; }
    @Override public int minRange() { return 30; }
    @Override public int rangeStep() { return 35; }

    @Override public List<Item> shells() { return List.of(ModRegistry.MORTAR_SHELL.get()); }

    @Override protected MissileProfile profileOf(ItemStack round) { return MissileProfile.MORTAR_SHELL; }

    @Override
    protected void fireEffects(ServerLevel level, Vec3 muzzle, Vec3 dir) {
        com.steelrain.util.Sfx.gunfire(level, muzzle, "mortar_fire", "mortar_distant", "mortar_distant");
    }
}
