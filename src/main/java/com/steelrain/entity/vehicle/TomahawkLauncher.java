package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tomahawk launcher trailer: a four-cell container that tips up almost vertical about its rear end and fires its
 * cruise missiles one after another. Parked where it is put; its own diesel power unit charges the launch battery.
 */
public class TomahawkLauncher extends SrErector {
    private static final Vec3 PIVOT = new Vec3(0, 1.2, -3.9);
    private static final Vec3[] TUBES = {new Vec3(-0.5, 1.7, 3.4), new Vec3(0.5, 2.56, 3.4), new Vec3(0.5, 1.7, 3.4), new Vec3(-0.5, 2.56, 3.4)};

    public TomahawkLauncher(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override public float maxHealth() { return 250; }
    @Override public float tankLitres() { return 60; }
    @Override public int batteryFe() { return 600_000; }
    @Override protected int alternatorFe() { return 400; }
    @Override protected Vec3[] seats() { return new Vec3[0]; }
    @Override public boolean drivable() { return false; }
    @Override protected double length() { return 9.0; }
    @Override protected double width() { return 2.6; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.TOMAHAWK_LAUNCHER_ITEM.get()); }
    @Override public String modelName() { return "tomahawk_launcher"; }

    @Override protected Item ammo() { return ModRegistry.TOMAHAWK_MISSILE.get(); }
    @Override protected MissileProfile profile() { return MissileProfile.TOMAHAWK; }
    @Override public int minRange() { return 100; }
    @Override protected int maxRange() { return 6000; }
    @Override protected int energyPerShot() { return 125_000; }
    @Override protected int cooldownTicks() { return 60; }
    @Override protected float fireElevation() { return 85f; }
    @Override protected float elevateRate() { return 1.2f; }
    @Override protected Vec3 erectorPivot() { return PIVOT; }
    @Override protected Vec3[] tubes() { return TUBES; }

    @Override
    protected void launchSound(ServerLevel level, Vec3 at) {
        com.steelrain.util.Sfx.gunfire(level, at, "missile_start", "javelin_far", "rpg_veryfar");
    }
}
