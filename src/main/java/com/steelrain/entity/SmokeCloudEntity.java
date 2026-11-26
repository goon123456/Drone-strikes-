package com.steelrain.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A smoke screen: a minute of thick smoke that recon drones cannot see through. */
public class SmokeCloudEntity extends Entity {
    public static final double RADIUS = 10;
    private int life = 20 * 60;

    public SmokeCloudEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Is this spot hidden under a smoke screen? */
    public static boolean hides(ServerLevel level, double x, double z) {
        for (SmokeCloudEntity c : level.getEntitiesOfClass(SmokeCloudEntity.class,
                new AABB(x - RADIUS, level.getMinBuildHeight(), z - RADIUS, x + RADIUS, level.getMaxBuildHeight(), z + RADIUS))) {
            double dx = c.getX() - x, dz = c.getZ() - z;
            if (dx * dx + dz * dz <= RADIUS * RADIUS) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (--life <= 0) {
            discard();
            return;
        }
        if (life % 3 == 0) {
            double spread = RADIUS * 0.45;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + 1.5, getZ(), 10, spread, 1.2, spread, 0.004);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 6, spread, 1.0, spread, 0.01);
        }
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { life = tag.contains("Life") ? tag.getInt("Life") : life; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Life", life); }
}
