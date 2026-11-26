package com.steelrain.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Fire control maths: aim where the target will be, not where it is. */
public final class Ballistics {
    /** How far an entity moved during its last tick. */
    public static Vec3 velocity(Entity e) {
        return new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
    }

    /**
     * Point at which something leaving {@code from} at {@code speed} blocks per tick meets a target that keeps its
     * current velocity. Null if it can never catch up.
     */
    @Nullable
    public static Vec3 intercept(Vec3 from, Vec3 target, Vec3 targetVelocity, double speed) {
        Vec3 r = target.subtract(from);
        double a = targetVelocity.lengthSqr() - speed * speed;
        double b = 2 * r.dot(targetVelocity);
        double c = r.lengthSqr();
        double t;
        if (Math.abs(a) < 1e-6) {
            if (Math.abs(b) < 1e-9) return null;
            t = -c / b;
        } else {
            double disc = b * b - 4 * a * c;
            if (disc < 0) return null;
            double s = Math.sqrt(disc);
            double t1 = (-b - s) / (2 * a), t2 = (-b + s) / (2 * a);
            t = t1 > 0 && t2 > 0 ? Math.min(t1, t2) : Math.max(t1, t2);
        }
        if (t <= 0 || t > 400) return null;
        return target.add(targetVelocity.scale(t));
    }

    /** Smallest distance between two things during one tick, given where each starts and how far each moves. */
    public static double closestApproach(Vec3 a0, Vec3 aMove, Vec3 b0, Vec3 bMove) {
        Vec3 rel = b0.subtract(a0);
        Vec3 relMove = bMove.subtract(aMove);
        double len = relMove.lengthSqr();
        double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, -rel.dot(relMove) / len));
        return rel.add(relMove.scale(t)).length();
    }

    private Ballistics() {}
}
