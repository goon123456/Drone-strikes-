package com.steelrain.entity;

import com.steelrain.ModRegistry;
import com.steelrain.util.Teams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One anti-aircraft cannon round: it flies at its muzzle velocity, drops a little, and does damage only where its
 * path actually meets something. HE rounds carry a proximity fuse that may burst them as they pass close to a hostile
 * drone or missile and pepper it with fragments; AP rounds have to hit, but hit harder. Some rounds are tracers.
 */
public class CannonShellEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> TRACER = SynchedEntityData.defineId(CannonShellEntity.class, EntityDataSerializers.BOOLEAN);

    /** Damage from one proximity burst. A Shahed has 6 health, so it takes eight. */
    public static final float FRAGMENT_DAMAGE = 0.75f;

    private float damage = 8f, gravity = 0.03f, fuseReach, fuseChance;
    private boolean armourPiercing;
    private int life = 40;
    @Nullable private UUID ownerId;
    @Nullable private String ownerName;
    @Nullable private Entity shooter;

    public CannonShellEntity(EntityType<? extends CannonShellEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Fires one round from a gun. {@code launcher} (the vehicle) and its crew are never hit by it. */
    public static void fire(ServerLevel level, Entity launcher, @Nullable Entity gunner, Vec3 from, Vec3 velocity, float damage,
                            float gravity, boolean ap, boolean tracer, float fuseReach, float fuseChance,
                            @Nullable UUID ownerId, @Nullable String ownerName) {
        CannonShellEntity s = new CannonShellEntity(ModRegistry.CANNON_SHELL.get(), level);
        s.setOwner(launcher);
        s.shooter = gunner;
        s.setPos(from);
        s.setDeltaMovement(velocity);
        s.damage = ap ? damage * 1.8f : damage;
        s.gravity = gravity;
        s.armourPiercing = ap;
        s.fuseReach = ap ? 0 : fuseReach;
        s.fuseChance = fuseChance;
        s.ownerId = ownerId;
        s.ownerName = ownerName;
        s.entityData.set(TRACER, tracer);
        s.face(velocity);
        level.addFreshEntity(s);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TRACER, false);
    }

    public boolean tracer() { return entityData.get(TRACER); }

    private void face(Vec3 v) {
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 move = getDeltaMovement();
        if (level() instanceof ServerLevel level) {
            if (--life < 0) { discard(); return; }
            if (fuseReach > 0 && proximityFuse(level, move)) return;
            HitResult hit = hitAlong(level, move);
            if (hit.getType() != HitResult.Type.MISS) {
                impact(level, hit);
                return;
            }
        }
        setPos(position().add(move));
        setDeltaMovement(move.add(0, -gravity, 0));
        face(move);
    }

    /** The first block or body the round's path this tick actually passes through (no padding round targets). */
    private HitResult hitAlong(ServerLevel level, Vec3 move) {
        Vec3 from = position(), to = from.add(move);
        HitResult block = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        EntityHitResult body = ProjectileUtil.getEntityHitResult(level, this, from, to, getBoundingBox().expandTowards(move).inflate(1.0),
                this::canHitEntity, 0f);
        return body != null ? body : block;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity launcher = getOwner();
        if (launcher != null && (e == launcher || launcher.hasPassenger(e))) return false;
        return e.isPickable() || e instanceof FlyingWeaponEntity;
    }

    private void impact(ServerLevel level, HitResult hit) {
        Vec3 at = hit.getLocation();
        if (hit instanceof EntityHitResult eh) {
            eh.getEntity().hurt(source(level), damage);
        } else if (hit instanceof BlockHitResult && armourPiercing) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.1);
        }
        if (!armourPiercing) {
            // small high-explosive burst: hurts what is close by, leaves the blocks alone
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            for (Entity e : level.getEntities(this, new AABB(at, at).inflate(2.5), x -> x != getOwner() && x.isAlive())) {
                double d = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
                if (d < 2.5) e.hurt(level.damageSources().explosion(this, shooter), (float) (4 * (1 - d / 2.5)) + 1);
            }
            if (level.random.nextInt(4) == 0) com.steelrain.util.Sfx.play(level, at, "explosion_air", 1.5f, 1.6f);
        }
        discard();
    }

    private DamageSource source(ServerLevel level) {
        return armourPiercing ? level.damageSources().thrown(this, shooter) : level.damageSources().explosion(this, shooter);
    }

    /** Bursts next to a hostile drone or missile it is passing, if the fuse works this time. */
    private boolean proximityFuse(ServerLevel level, Vec3 move) {
        AABB sweep = getBoundingBox().expandTowards(move).inflate(fuseReach + 1);
        for (Entity e : level.getEntities(this, sweep, x -> x instanceof FlyingWeaponEntity)) {
            FlyingWeaponEntity f = (FlyingWeaponEntity) e;
            if (!f.isAlive() || f.falling() || !f.interceptable() || Teams.friendly(level, ownerId, ownerName, f.ownerId(), f.ownerName())) continue;
            Vec3 targetPos = f.position().add(0, f.getBbHeight() / 2, 0), targetMove = com.steelrain.util.Ballistics.velocity(f);
            Vec3 rel = position().subtract(targetPos), relMove = move.subtract(targetMove);
            double t = Mth.clamp(-rel.dot(relMove) / Math.max(1e-9, relMove.lengthSqr()), 0, 1);
            Vec3 miss = rel.add(relMove.scale(t));
            if (miss.lengthSqr() > fuseReach * fuseReach || level.random.nextDouble() > fuseChance) continue;
            Vec3 at = targetPos.add(miss);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
            if (level.random.nextInt(3) == 0) com.steelrain.util.Sfx.play(level, at, "explosion_air", 3f, 1.3f);
            f.hurt(level.damageSources().explosion(this, shooter), FRAGMENT_DAMAGE);
            discard();
            return true;
        }
        return false;
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 300 * 300; }
}
