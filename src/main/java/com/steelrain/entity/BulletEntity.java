package com.steelrain.entity;

import com.steelrain.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * A cannon round. It is a real object that flies through the air with a travel time and a little drop, and it
 * only does damage if its path actually passes through something. Nothing in this mod is hitscan.
 */
public class BulletEntity extends Entity {
    private static final EntityDataAccessor<Vector3f> VELOCITY = SynchedEntityData.defineId(BulletEntity.class, EntityDataSerializers.VECTOR3);
    public static final double GRAVITY = 0.012;

    private Vec3 velocity = Vec3.ZERO;
    private float damage = 4f;
    private int life = 45;
    @Nullable private UUID ownerId;
    private boolean started;

    public BulletEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static BulletEntity fire(ServerLevel level, Vec3 from, Vec3 velocity, float damage, @Nullable UUID owner) {
        BulletEntity b = new BulletEntity(ModRegistry.BULLET.get(), level);
        b.velocity = velocity;
        b.damage = damage;
        b.ownerId = owner;
        b.setPos(from);
        b.entityData.set(VELOCITY, velocity.toVector3f());
        b.aim(velocity);
        level.addFreshEntity(b);
        return b;
    }

    private void aim(Vec3 v) {
        setRot((float) (Math.atan2(-v.x, v.z) * 180 / Math.PI),
                (float) (-Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180 / Math.PI));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VELOCITY, new Vector3f());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // The path is fully determined by the start velocity, so the client flies it itself: smooth tracers.
            if (!started) {
                velocity = new Vec3(entityData.get(VELOCITY));
                if (velocity.lengthSqr() < 1e-6) return; // start velocity has not arrived yet
                started = true;
            }
            velocity = velocity.add(0, -GRAVITY, 0);
            setPos(position().add(velocity));
            aim(velocity);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (--life <= 0) {
            discard();
            return;
        }
        velocity = velocity.add(0, -GRAVITY, 0);
        Vec3 from = position(), to = from.add(velocity);

        // What does this tick's stretch of flight pass through first: a block or an entity?
        Vec3 end = to;
        boolean hitBlock = false;
        if (life < 43) { // clear of the gun that fired it
            BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
                hitBlock = true;
            }
        }
        Entity victim = null;
        double best = from.distanceToSqr(end);
        for (Entity e : level.getEntities(this, new AABB(from, end).inflate(1.5), BulletEntity::canHit)) {
            if (e.getUUID().equals(ownerId)) continue; // nobody shoots themselves with their own gun
            Optional<Vec3> at = e.getBoundingBox().inflate(0.25).clip(from, end);
            if (at.isPresent() && from.distanceToSqr(at.get()) < best) {
                best = from.distanceToSqr(at.get());
                victim = e;
                end = at.get();
            }
        }
        if (victim != null) {
            Player owner = ownerId == null ? null : level.getPlayerByUUID(ownerId);
            victim.hurt(level.damageSources().mobProjectile(this, owner), damage);
            level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.1);
            discard();
            return;
        }
        if (hitBlock) {
            level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.01);
            discard();
            return;
        }
        setPos(to);
        aim(velocity);
    }

    private static boolean canHit(Entity e) {
        if (e instanceof InterceptorEntity || e instanceof SeatEntity || e.isSpectator() || !e.isAlive()) return false;
        return e instanceof FlyingWeaponEntity || e instanceof LivingEntity;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // ignore server position updates: see tick()
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 200.0 * 200.0; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
