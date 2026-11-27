package com.steelrain.entity;

import com.steelrain.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A hand grenade in flight. It arcs, bounces off whatever it hits, and after its fuse runs down it goes off: a
 * fragmentation grenade bursts and hurts everything close that is not behind cover; a smoke grenade lays a screen.
 */
public class GrenadeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> SMOKE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.BOOLEAN);
    private int fuse = 55;                                   // a little under three seconds
    private static final float BLAST_RADIUS = 4.5f, BLAST_DAMAGE = 11f;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(Level level, LivingEntity thrower, boolean smoke) {
        super(ModRegistry.HAND_GRENADE.get(), thrower, level);
        entityData.set(SMOKE, smoke);
        setItem(new ItemStack(smoke ? ModRegistry.SMOKE_GRENADE.get() : ModRegistry.FRAG_GRENADE.get()));
    }

    @Override protected Item getDefaultItem() { return ModRegistry.FRAG_GRENADE.get(); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SMOKE, false);
    }

    public boolean smoke() { return entityData.get(SMOKE); }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && --fuse <= 0) detonate(level);
        else if (level().isClientSide && (fuse & 1) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
        }
    }

    /** Grenades do not stick where they land: they bounce off and roll, shedding speed each time. */
    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.BLOCK) {
            Direction face = ((BlockHitResult) result).getDirection();
            Vec3 m = getDeltaMovement();
            m = switch (face.getAxis()) {
                case X -> new Vec3(-m.x * 0.45, m.y * 0.7, m.z * 0.7);
                case Y -> new Vec3(m.x * 0.7, -m.y * 0.4, m.z * 0.7);
                case Z -> new Vec3(m.x * 0.7, m.y * 0.7, -m.z * 0.45);
            };
            if (m.horizontalDistanceSqr() < 0.004) m = new Vec3(0, m.y, 0);     // let a slow one settle instead of jittering
            setDeltaMovement(m);
            if (!level().isClientSide && m.lengthSqr() > 0.02) level().playSound(null, getX(), getY(), getZ(),
                    net.minecraft.sounds.SoundEvents.METAL_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.5f);
        } else if (result.getType() == HitResult.Type.ENTITY) {
            setDeltaMovement(getDeltaMovement().scale(0.25));                   // it just bonks off and drops
        }
    }

    private void detonate(ServerLevel level) {
        Vec3 at = position().add(0, 0.1, 0);
        Entity owner = getOwner();
        if (smoke()) {
            SmokeCloudEntity cloud = new SmokeCloudEntity(ModRegistry.SMOKE.get(), level);
            cloud.setPos(at.x, at.y, at.z);
            level.addFreshEntity(cloud);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 0.4, 0.3, 0.4, 0.02);
            com.steelrain.util.Sfx.explosion(level, at, 1);
            discard();
            return;
        }
        float scale = com.steelrain.Config.GUN_DAMAGE.get().floatValue();
        var source = level.damageSources().explosion(this, owner);
        for (Entity e : level.getEntities(this, new AABB(at, at).inflate(BLAST_RADIUS + 1), x -> x.isAlive() && !x.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(at);
            if (d >= BLAST_RADIUS) continue;
            if (d > 1.5 && level.clip(new net.minecraft.world.level.ClipContext(at, c, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;  // cover shields
            e.hurt(source, (float) (BLAST_DAMAGE * scale * (1 - d / BLAST_RADIUS)) + 1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 0.5, 0.4, 0.5, 0.04);
        com.steelrain.util.Sfx.explosion(level, at, 0);
        discard();
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Fuse", fuse); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Fuse")) fuse = tag.getInt("Fuse"); }
}
