package com.steelrain.entity;

import com.steelrain.util.Ballistics;
import com.steelrain.util.Blast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Surface-to-air missile fired by the Pantsir. Chases one flying target. */
public class InterceptorEntity extends FlyingWeaponEntity {
    private final boolean patriot;
    private final double speed;
    private int targetId = -1;
    private Vec3 dir = new Vec3(0, 1, 0);
    private int age;

    public InterceptorEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.patriot = net.minecraft.world.entity.EntityType.getKey(type).getPath().equals("patriot_missile");
        this.speed = patriot ? 6.0 : 4.5;
    }

    public void setTarget(Entity target, Vec3 launchDir) {
        targetId = target.getId();
        dir = launchDir.normalize();
        faceAlong(dir);
    }

    @Override public boolean interceptable() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    protected void serverTick(ServerLevel level) {
        age++;
        Entity e = level.getEntity(targetId);
        FlyingWeaponEntity ours = e instanceof FlyingWeaponEntity f ? f : null;
        Entity target = e;
        if (target == null || target.isRemoved() || !target.isAlive() || age > (patriot ? 220 : 130)) {
            Blast.airburst(level, position());
            discard();
            return;
        }
        Vec3 pos = position();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 targetMove = Ballistics.velocity(target);
        double dist = pos.distanceTo(targetPos);

        // Steer for where the target will be when we get there, not where it is now. The turn rate is limited,
        // so a target that is fast, close or crossing can still get away.
        Vec3 aim = Ballistics.intercept(pos, targetPos, targetMove, speed);
        Vec3 desired = (aim != null ? aim : targetPos).subtract(pos).normalize();
        dir = turnTowards(dir, desired, age < 4 ? 4 : (patriot ? 9 : 14));
        Vec3 move = dir.scale(speed);

        // Proximity fuse: only goes off if this tick's flight really passes close enough.
        double miss = Ballistics.closestApproach(pos, move, targetPos, targetMove);
        if (miss <= (patriot ? 4.0 : 2.5)) {
            setPos(targetPos);
            Blast.airburst(level, targetPos);
            target.hurt(level.damageSources().explosion(this, ownerPlayer()), patriot ? 60f : 25f);
            discard();
            return;
        }
        // Flew past: it is not coming back round.
        if (dist > lastDist + 0.5 && age > 6 && ++opening > 6) {
            if (ours != null) ours.engagedUntil = 0;
            Blast.airburst(level, pos);
            discard();
            return;
        }
        lastDist = dist;

        Vec3 next = pos.add(move);
        keepChunksLoaded(level, next, move);
        if (age > 3 && blockHit(pos, next) != null) {
            if (ours != null) ours.engagedUntil = 0;
            Blast.airburst(level, pos);
            discard();
            return;
        }
        setPos(next);
        faceAlong(dir);
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.01);
    }

    private double lastDist = Double.MAX_VALUE;
    private int opening;

    /** Rotates {@code from} towards {@code to} by at most {@code maxDegrees}. */
    private static Vec3 turnTowards(Vec3 from, Vec3 to, double maxDegrees) {
        double dot = Mth.clamp(from.dot(to), -1, 1);
        double angle = Math.acos(dot);
        double max = Math.toRadians(maxDegrees);
        if (angle <= max || angle < 1e-4) return to;
        double t = max / angle;
        Vec3 v = from.scale(Math.sin((1 - t) * angle)).add(to.scale(Math.sin(t * angle))).scale(1 / Math.sin(angle));
        return v.lengthSqr() < 1e-8 ? to : v.normalize();
    }
}
