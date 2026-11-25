package com.steelrain.entity;

import com.steelrain.Config;
import com.steelrain.util.Blast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Surface-to-air missile fired by the Pantsir. Chases one flying target. */
public class InterceptorEntity extends FlyingWeaponEntity {
    private static final double SPEED = 4.5;
    private int targetId = -1;
    private Vec3 dir = new Vec3(0, 1, 0);
    private int age;

    public InterceptorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setTarget(Entity target) {
        targetId = target.getId();
        dir = target.position().subtract(position()).normalize().add(0, 0.6, 0).normalize();
        faceAlong(dir);
    }

    @Override public boolean interceptable() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    protected void serverTick(ServerLevel level) {
        age++;
        Entity e = level.getEntity(targetId);
        if (!(e instanceof FlyingWeaponEntity target) || target.isRemoved() || age > 120) {
            Blast.airburst(level, position());
            discard();
            return;
        }
        Vec3 pos = position();
        Vec3 to = target.position().subtract(pos);
        double dist = to.length();
        if (dist <= SPEED + 1.5) {
            setPos(target.position());
            if (random.nextDouble() < Config.AIR_DEFENCE_HIT_CHANCE.get()) {
                target.shotDown();
            } else {
                target.engagedUntil = 0; // missed: let the launcher try again
                Blast.airburst(level, position());
            }
            discard();
            return;
        }
        dir = steer(dir, to.scale(1 / dist), age < 4 ? 0.2 : 0.6);
        Vec3 next = pos.add(dir.scale(SPEED));
        if (age > 3 && blockHit(pos, next) != null) {
            Blast.airburst(level, pos);
            discard();
            return;
        }
        setPos(next);
        faceAlong(dir);
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.01);
    }
}
