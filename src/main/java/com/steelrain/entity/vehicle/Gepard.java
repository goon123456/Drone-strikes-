package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Flakpanzer Gepard: tracked, radar-laid twin 35 mm. Seat 0 drives, seat 1 commands the guns from the turret. */
public class Gepard extends SrAirDefence {
    private static final Vec3[] MUZZLES = {new Vec3(1.31, 2.74, 3.4), new Vec3(-1.31, 2.74, 3.4)};
    private static final Vec3[] SEATS = {new Vec3(0.6, 0.45, 2.4), new Vec3(0, 2.3, 0.3)};

    public Gepard(EntityType<?> type, Level level) { super(type, level); }

    @Override public float maxHealth() { return 500; }
    @Override public float tankLitres() { return 150; }
    @Override public int batteryFe() { return 400_000; }
    @Override public Vec3[] seats() { return SEATS; }
    @Override public boolean tracked() { return true; }
    @Override protected double topSpeed() { return 0.6; }
    @Override protected double reverseSpeed() { return 0.25; }
    @Override protected double acceleration() { return 0.011; }
    @Override public double wheelRadius() { return 0.35; }
    @Override protected double length() { return 7.0; }
    @Override protected double width() { return 3.2; }
    @Override protected double litresPerBlock() { return 1.0 / 35; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.GEPARD_ITEM.get()); }
    @Override public String modelName() { return "gepard"; }
    @Override protected Vec3 turretPivot() { return new Vec3(0, 2.0, -0.3); }
    @Override protected Vec3 barrelPivot() { return new Vec3(0, 2.8, -0.1); }

    @Override public int gunnerSeat() { return 1; }
    @Override public boolean hasRadar() { return true; }
    @Override protected Vec3[] muzzles() { return MUZZLES; }
    @Override protected int roundsPerMinute() { return 1100; }
    @Override protected float shellDamage() { return 14; }
    @Override protected float fuseReach() { return 1.5f; }
    @Override protected float fuseChance() { return 0.08f; }
    @Override public double gunRange() { return 96; }
    @Override protected Item heBelt() { return ModRegistry.AMMO_35MM.get(); }
    @Override protected Item apBelt() { return ModRegistry.AMMO_35MM_AP.get(); }
    @Override protected int roundsPerBelt() { return 40; }
    @Override protected float traverseRate() { return 4.5f; }
    @Override protected float elevateRate() { return 3f; }
    @Override protected String[] gunSounds() { return new String[]{"bradley_cannon_fire_3p", "bradley_cannon_far", "bradley_cannon_veryfar"}; }
}
