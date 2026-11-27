package com.steelrain.gun;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Every bullet in flight. A bullet is not an entity: it is a point with a velocity that the server moves each tick,
 * slowed by the air and pulled down by gravity, and tested along its path against blocks and bodies. It can go
 * through thin or soft cover and lose speed doing it, glance off hard surfaces at a shallow angle, and it hits
 * harder the faster it is still going. Clients fly their own copies for the tracers and the crack of a near miss.
 */
public final class Bullets {
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "bullet"));

    private static final class Bullet {
        Vec3 pos, vel;
        final Vec3 origin;
        final Caliber caliber;
        final double muzzleSpeed;
        final float damageScale;
        @Nullable final Entity shooter;
        int age;

        Bullet(Vec3 pos, Vec3 vel, Caliber caliber, float damageScale, @Nullable Entity shooter) {
            this.pos = pos; this.origin = pos; this.vel = vel; this.caliber = caliber; this.muzzleSpeed = vel.length();
            this.damageScale = damageScale; this.shooter = shooter;
        }
    }

    private static final Map<ServerLevel, List<Bullet>> FLYING = new WeakHashMap<>();

    /** Fires one bullet. It covers its first tick of flight at once, so close targets are hit without delay. */
    public static void fire(ServerLevel level, @Nullable Entity shooter, Vec3 from, Vec3 velocity, Caliber caliber, float damageScale) {
        Bullet b = new Bullet(from, velocity, caliber, damageScale, shooter);
        if (step(level, b)) FLYING.computeIfAbsent(level, l -> new ArrayList<>()).add(b);
    }

    public static void tick(ServerLevel level) {
        List<Bullet> list = FLYING.get(level);
        if (list == null || list.isEmpty()) return;
        for (Iterator<Bullet> it = list.iterator(); it.hasNext(); ) if (!step(level, it.next())) it.remove();
    }

    /** Moves a bullet one tick. Returns false when it has stopped. */
    private static boolean step(ServerLevel level, Bullet b) {
        if (++b.age > 100 || b.vel.lengthSqr() < 2.0 * 2.0) return false;                 // spent: under 40 m/s
        Vec3 to = b.pos.add(b.vel);
        for (int pass = 0; pass < 5; pass++) {
            if (!level.isLoaded(BlockPos.containing(b.pos))) return false;
            Vec3 dir = to.subtract(b.pos);
            double reach = dir.length();
            if (reach < 1e-4) break;
            dir = dir.scale(1 / reach);
            BlockHitResult block = level.clip(new ClipContext(b.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
            Entity body = null;
            Vec3 bodyAt = null;
            double best = Double.MAX_VALUE;
            for (Entity e : level.getEntities(b.shooter, new AABB(b.pos, end).inflate(1.0), e -> canHit(b, e))) {
                var at = e.getBoundingBox().inflate(e.getPickRadius()).clip(b.pos, end);
                if (at.isEmpty()) continue;
                double d = b.pos.distanceToSqr(at.get());
                if (d < best) { best = d; body = e; bodyAt = at.get(); }
            }
            if (body != null) {
                boolean through = hitBody(level, b, body, bodyAt);
                if (!through) return false;
                b.pos = bodyAt.add(dir.scale(body.getBbWidth() + 0.3));
                b.vel = b.vel.scale(0.5);
                to = b.pos.add(dir.scale(Math.max(0, to.subtract(b.pos).dot(dir)) * 0.5));
                continue;
            }
            if (block.getType() == HitResult.Type.MISS) break;
            Vec3 next = hitBlock(level, b, block, dir);
            if (next == null) return false;
            // carry on for what is left of this tick, along the (possibly new) direction and at the new speed
            double left = Math.max(0, reach - b.pos.distanceTo(block.getLocation())) * b.vel.length() / Math.max(1e-6, reach);
            b.pos = next;
            to = next.add(b.vel.normalize().scale(Math.min(left, b.vel.length())));
        }
        b.pos = to;
        b.vel = b.caliber.advance(b.vel);
        return true;
    }

    private static boolean canHit(Bullet b, Entity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (b.shooter != null && (e == b.shooter || e == b.shooter.getVehicle() || b.shooter.hasPassenger(e))) return false;
        return e.isPickable() || e instanceof FlyingWeaponEntity;
    }

    /** @return whether the bullet carries on through the body */
    private static boolean hitBody(ServerLevel level, Bullet b, Entity e, Vec3 at) {
        double speed = b.vel.length() / b.muzzleSpeed;
        float damage = (float) (b.caliber.damage * b.damageScale * Math.pow(speed, 1.5) * Config.GUN_DAMAGE.get());
        boolean head = e instanceof LivingEntity living && living.getBbHeight() > 1.0f && at.y >= living.getEyeY() - 0.2;
        if (head) damage *= 1.8f;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET),
                null, b.shooter, b.origin);
        boolean wasAlive = e.isAlive();
        boolean hurt = e.hurt(source, damage);
        if (e instanceof LivingEntity) level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.1);
        else level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.2);
        if (hurt && b.shooter instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Payloads.GunHit(head, wasAlive && !e.isAlive()));
        }
        // a full-power rifle bullet goes through a body and may hit what is behind it
        return e instanceof LivingEntity && b.caliber.pierce >= 1f && speed > 0.5;
    }

    /** How much of a bullet's piercing power one block of this material uses up; infinite for what stops it dead. */
    private static double resistance(BlockState s) {
        if (!s.getFluidState().isEmpty() && s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) return 3.0;
        if (s.is(BlockTags.LEAVES)) return 0.04;
        if (s.getSoundType() == SoundType.GLASS) return 0.12;
        if (s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS)) return 0.2;
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) return 0.4;
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return 1.0;
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 1.8;
        return Double.POSITIVE_INFINITY;
    }

    /** @return where the bullet carries on from (its velocity updated), or null if the block stopped it */
    @Nullable
    private static Vec3 hitBlock(ServerLevel level, Bullet b, BlockHitResult hit, Vec3 dir) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Vec3 at = hit.getLocation();
        boolean water = !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
        if (water) {
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 8, 0.1, 0.05, 0.1, 0.2);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25f, 1.8f);
            return null;                                                       // water stops a bullet within a metre or two
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.04, 0.04, 0.04, 0.06);
        SoundType st = state.getSoundType(level, pos, null);
        level.playSound(null, at.x, at.y, at.z, st.getHitSound(), SoundSource.BLOCKS, 0.7f, st.getPitch() * 1.3f);
        double res = Config.BULLET_PENETRATION.get() ? resistance(state) : Double.POSITIVE_INFINITY;
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        if (Double.isInfinite(res)) {
            // hard surface: a shallow hit may glance off, keeping about half its speed
            double graze = Math.abs(dir.dot(n));
            if (graze < 0.22 && level.random.nextFloat() < 0.65f && b.vel.length() > 6) {
                Vec3 out = b.vel.subtract(n.scale(2 * b.vel.dot(n))).scale(0.45 + level.random.nextDouble() * 0.2);
                double j = out.length() * 0.06;
                b.vel = out.add((level.random.nextDouble() - 0.5) * j, level.random.nextDouble() * j, (level.random.nextDouble() - 0.5) * j);
                level.playSound(null, at.x, at.y, at.z, GunLogic.sound("bullet_ricochet"), SoundSource.NEUTRAL, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.3);
                return at.add(n.scale(0.02));
            }
            return null;
        }
        // soft or thin cover: how far is it through, and has the bullet the energy for it?
        Vec3 far = at.add(dir.scale(1.8));
        BlockHitResult exit = state.getCollisionShape(level, pos).clip(far, at, pos);
        double thick = exit == null ? 1.0 : Math.max(0.02, exit.getLocation().distanceTo(at));
        double speed = b.vel.length() / b.muzzleSpeed;
        double power = b.caliber.pierce * speed * speed;
        double cost = res * thick;
        if (state.getSoundType() == SoundType.GLASS && Config.GUNS_BREAK_GLASS.get() && state.getDestroySpeed(level, pos) >= 0
                && state.getDestroySpeed(level, pos) < 0.6f) {
            level.destroyBlock(pos, false, b.shooter);
        }
        if (cost >= power) return null;
        b.vel = b.vel.scale(Math.sqrt(1 - cost / power));
        return at.add(dir.scale(thick + 0.02));
    }

    private Bullets() {}
}
