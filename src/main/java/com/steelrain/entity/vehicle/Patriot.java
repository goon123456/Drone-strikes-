package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import com.steelrain.entity.InterceptorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * MIM-104 Patriot launching station: missiles only, no crew position. It works on its own once automatic mode is
 * on (right-click it) and only takes on missiles and aircraft, leaving small drones to the guns.
 */
public class Patriot extends SrAirDefence {
    public Patriot(EntityType<?> type, Level level) { super(type, level); }

    @Override public float maxHealth() { return 300; }
    @Override public float tankLitres() { return 60; }
    @Override public int batteryFe() { return 1_000_000; }
    @Override protected int alternatorFe() { return 500; }
    @Override protected boolean hasPowerUnit() { return true; }
    @Override public Vec3[] seats() { return new Vec3[0]; }
    @Override public boolean drivable() { return false; }
    @Override protected double length() { return 9.0; }
    @Override protected double width() { return 2.6; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.PATRIOT_ITEM.get()); }
    @Override public String modelName() { return "patriot"; }
    @Override protected Vec3 turretPivot() { return new Vec3(0, 1.3, -1.0); }
    @Override protected Vec3 barrelPivot() { return new Vec3(0, 1.9, -2.0); }

    @Override public int gunnerSeat() { return -1; }
    @Override public boolean hasRadar() { return true; }
    @Override protected Vec3[] muzzles() { return new Vec3[0]; }
    @Override protected int roundsPerMinute() { return 0; }
    @Override protected float shellDamage() { return 0; }
    @Override protected float fuseReach() { return 0; }
    @Override protected float fuseChance() { return 0; }
    @Override public double gunRange() { return 0; }
    @Override public double missileRange() { return 400; }
    @Override protected int missileCooldown() { return 60; }
    @Override protected int missileEnergy() { return 25_000; }
    @Override protected Item missileItem() { return ModRegistry.PATRIOT_MISSILE.get(); }
    @Override protected EntityType<InterceptorEntity> interceptor() { return ModRegistry.PATRIOT_INTERCEPTOR.get(); }
    @Override protected Vec3 missileTube() { return new Vec3(0, 2.3, 3.2); }
    @Override protected boolean highValueOnly() { return true; }
    @Override protected boolean hasCeiling() { return false; }
    @Override protected Item heBelt() { return null; }
    @Override protected Item apBelt() { return null; }
    @Override public double minPitch() { return 10; }
    @Override public double maxPitch() { return 80; }
    @Override protected float traverseRate() { return 5f; }
    @Override protected float elevateRate() { return 3f; }
    @Override protected String[] gunSounds() { return new String[]{"", "", ""}; }
}
