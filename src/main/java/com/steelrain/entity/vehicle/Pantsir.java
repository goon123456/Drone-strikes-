package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import com.steelrain.entity.InterceptorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Pantsir-S1 on its 8x8 truck: two 30 mm guns and 57E6 interceptor missiles under one radar. */
public class Pantsir extends SrAirDefence {
    private static final Vec3[] MUZZLES = {new Vec3(0.85, 2.55, 0.2), new Vec3(-0.85, 2.55, 0.2)};
    private static final Vec3[] SEATS = {new Vec3(0.55, 0.9, 4.0), new Vec3(0, 2.48, -1.0)};

    public Pantsir(EntityType<?> type, Level level) { super(type, level); }

    @Override public float maxHealth() { return 400; }
    @Override public float tankLitres() { return 200; }
    @Override public int batteryFe() { return 600_000; }
    @Override protected Vec3[] seats() { return SEATS; }
    @Override protected double wheelbase() { return 5.4; }
    @Override protected double topSpeed() { return 0.65; }
    @Override protected double acceleration() { return 0.01; }
    @Override public double wheelRadius() { return 0.6; }
    @Override protected double length() { return 10.5; }
    @Override protected double width() { return 2.6; }
    @Override protected double litresPerBlock() { return 1.0 / 45; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.PANTSIR_ITEM.get()); }
    @Override public String modelName() { return "pantsir"; }
    @Override protected Vec3 turretPivot() { return new Vec3(0, 2.2, -1.5); }
    @Override protected Vec3 barrelPivot() { return new Vec3(0, 3.1, -1.5); }

    @Override public int gunnerSeat() { return 1; }
    @Override public boolean hasRadar() { return true; }
    @Override protected Vec3[] muzzles() { return MUZZLES; }
    @Override protected int roundsPerMinute() { return 1000; }
    @Override protected float shellDamage() { return 12; }
    @Override protected float fuseReach() { return 1.5f; }
    @Override protected float fuseChance() { return 0.05f; }
    @Override public double gunRange() { return 64; }
    @Override protected double spread() { return 0.008; }
    @Override public double missileRange() { return 128; }
    @Override protected Item missileItem() { return ModRegistry.PANTSIR_MISSILE.get(); }
    @Override protected EntityType<InterceptorEntity> interceptor() { return ModRegistry.INTERCEPTOR.get(); }
    @Override protected Vec3 missileTube() { return new Vec3(0, 3.6, -0.5); }
    @Override protected Item heBelt() { return ModRegistry.AMMO_30MM.get(); }
    @Override protected Item apBelt() { return ModRegistry.AMMO_30MM_AP.get(); }
    @Override protected float traverseRate() { return 5f; }
    @Override protected float elevateRate() { return 3.5f; }
    @Override protected String[] gunSounds() { return new String[]{"ah_6_cannon_fire_3p", "ah_6_cannon_far", "ah_6_cannon_veryfar"}; }
}
