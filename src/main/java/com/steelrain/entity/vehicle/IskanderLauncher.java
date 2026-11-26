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
 * 9P78-1 Iskander-M launcher on its 8x8 chassis. Drives with both front axles steering; on a fire order it stops,
 * opens the roof doors over the missile bay and raises the missile to the vertical before it fires.
 */
public class IskanderLauncher extends SrErector {
    private static final Vec3 PIVOT = new Vec3(0, 2.25, -5.5);
    private static final Vec3[] TUBES = {new Vec3(0, 2.78, 2.2)};
    private static final Vec3[] SEATS = {new Vec3(0.6, 1.13, 5.4), new Vec3(-0.6, 1.13, 5.4)};

    public IskanderLauncher(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override public float maxHealth() { return 350; }
    @Override public float tankLitres() { return 200; }
    @Override public int batteryFe() { return 800_000; }
    @Override protected int alternatorFe() { return 400; }
    @Override public Vec3[] seats() { return SEATS; }
    @Override protected double wheelbase() { return 7.2; }
    @Override protected double topSpeed() { return 0.6; }
    @Override protected double acceleration() { return 0.009; }
    @Override public double wheelRadius() { return 0.65; }
    @Override protected double length() { return 13.0; }
    @Override protected double width() { return 3.0; }
    @Override protected double litresPerBlock() { return 1.0 / 40; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.ISKANDER_LAUNCHER_ITEM.get()); }
    @Override public String modelName() { return "iskander_launcher"; }

    @Override protected Item ammo() { return ModRegistry.ISKANDER_MISSILE.get(); }
    @Override protected MissileProfile profile() { return MissileProfile.ISKANDER; }
    @Override public int minRange() { return 150; }
    @Override protected int maxRange() { return 8000; }
    @Override protected int energyPerShot() { return 250_000; }
    @Override protected int cooldownTicks() { return 100; }
    @Override protected float fireElevation() { return 88f; }
    @Override protected float elevateRate() { return 1.1f; }
    @Override protected Vec3 erectorPivot() { return PIVOT; }
    @Override protected Vec3[] tubes() { return TUBES; }

    @Override
    protected void launchSound(ServerLevel level, Vec3 at) {
        com.steelrain.util.Sfx.gunfire(level, at, "missile_start", "javelin_far", "rpg_veryfar");
    }
}
