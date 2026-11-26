package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** ZU-23-2: towed twin 23 mm, laid by hand from the seat between the guns. No radar, no lead computer. */
public class Zu23 extends SrAirDefence {
    private static final Vec3[] MUZZLES = {new Vec3(0.18, 1.15, 2.5), new Vec3(-0.18, 1.15, 2.5)};
    private static final Vec3[] SEATS = {new Vec3(0, 0.35, -0.6)};

    public Zu23(EntityType<?> type, Level level) { super(type, level); }

    @Override public float maxHealth() { return 120; }
    @Override public float tankLitres() { return 0; }
    @Override public int batteryFe() { return 100_000; }
    @Override protected Vec3[] seats() { return SEATS; }
    @Override public boolean drivable() { return false; }
    @Override public float cameraDistance() { return 5f; }
    @Override public boolean hidesSeat(int seat) { return false; }
    @Override protected double length() { return 3.0; }
    @Override protected double width() { return 2.0; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.ZU23_ITEM.get()); }
    @Override public String modelName() { return "zu_23_2"; }
    @Override protected Vec3 turretPivot() { return new Vec3(0, 0.5, 0); }
    @Override protected Vec3 barrelPivot() { return new Vec3(0, 1.15, 0); }

    @Override public int gunnerSeat() { return 0; }
    @Override public boolean hasRadar() { return false; }
    @Override protected Vec3[] muzzles() { return MUZZLES; }
    @Override protected int roundsPerMinute() { return 900; }
    @Override protected int beltChangeTicks() { return 40; }   // two 50-round magazines changed by hand
    @Override protected float shellDamage() { return 8; }
    @Override protected float fuseReach() { return 1.2f; }
    @Override protected float fuseChance() { return 0.02f; }
    @Override public double gunRange() { return 64; }
    @Override protected double spread() { return 0.014; }
    @Override protected Item heBelt() { return ModRegistry.AMMO_23MM.get(); }
    @Override protected Item apBelt() { return ModRegistry.AMMO_23MM_AP.get(); }
    @Override public double minPitch() { return -5; }
    @Override protected float traverseRate() { return 4f; }
    @Override protected float elevateRate() { return 3f; }
    @Override protected String[] gunSounds() { return new String[]{"lav_150_cannon_fire_3p", "lav_150_cannon_far", "lav_150_cannon_veryfar"}; }
}
