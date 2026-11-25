package com.steelrain.util;

import com.steelrain.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Warhead effects: crater, vanilla explosion, area damage, smoke and debris. */
public final class Blast {
    public static void detonate(ServerLevel level, Vec3 pos, float power, int craterRadius, float damage,
                                double damageRadius, boolean fire, @Nullable Entity source, @Nullable Entity owner) {
        double scale = Config.EXPLOSION_SCALE.get();
        boolean blocks = Config.BLOCK_DAMAGE.get();
        RandomSource rnd = level.random;

        if (blocks && craterRadius > 0) {
            carveCrater(level, pos, craterRadius * scale, rnd);
        }
        float p = (float) Math.min(12.0, power * scale);
        level.explode(source, pos.x, pos.y, pos.z, p, fire && blocks,
                blocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);

        // Fragmentation: reaches further than the vanilla blast.
        double r = damageRadius;
        float dmgScale = (float) (double) Config.DAMAGE_SCALE.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(r))) {
            double d = Math.sqrt(e.distanceToSqr(pos));
            if (d > r) continue;
            float dealt = (float) (damage * dmgScale * (1.0 - d / r));
            if (dealt > 0.5f) {
                e.hurt(level.damageSources().explosion(source, owner), dealt);
            }
        }

        int puffs = 2 + craterRadius;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, puffs,
                craterRadius * 0.5, craterRadius * 0.3, craterRadius * 0.5, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.x, pos.y + 1, pos.z, 12 + craterRadius * 8,
                craterRadius * 0.5 + 0.5, 1.0, craterRadius * 0.5 + 0.5, 0.02);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 1, pos.z, 10 + craterRadius * 4,
                craterRadius * 0.4, 0.5, craterRadius * 0.4, 0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                4f + craterRadius * 2f, 0.55f + rnd.nextFloat() * 0.2f);
    }

    private static void carveCrater(ServerLevel level, Vec3 centre, double radius, RandomSource rnd) {
        BlockPos c = BlockPos.containing(centre);
        int r = Mth.ceil(radius);
        int debrisLeft = Config.DEBRIS.get() ? 6 + r * 2 : 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    // Flattened sphere with a ragged edge.
                    double d = Math.sqrt(dx * dx + dz * dz + (dy * dy) / 0.55);
                    if (d > radius - rnd.nextDouble() * 1.5) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (level.isOutsideBuildHeight(m)) continue;
                    BlockState state = level.getBlockState(m);
                    if (state.isAir() || state.liquid()) continue;
                    if (state.getDestroySpeed(level, m) < 0) continue;              // bedrock & co.
                    if (state.getBlock().getExplosionResistance() >= 600f) continue; // obsidian & co.
                    if (debrisLeft > 0 && d > radius * 0.5 && rnd.nextInt(24) == 0
                            && state.isCollisionShapeFullBlock(level, m) && !state.hasBlockEntity()) {
                        debrisLeft--;
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                        FallingBlockEntity fb = FallingBlockEntity.fall(level, m.immutable(), state);
                        fb.dropItem = false;
                        Vec3 out = new Vec3(dx, Math.abs(dy) + radius, dz).normalize();
                        fb.setDeltaMovement(out.x * 0.7 + rnd.nextGaussian() * 0.1,
                                0.6 + rnd.nextDouble() * 0.5, out.z * 0.7 + rnd.nextGaussian() * 0.1);
                        fb.hurtMarked = true;
                    } else {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** Harmless airburst used when something is shot down. */
    public static void airburst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 6, 0.6, 0.6, 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 20, 0.8, 0.8, 0.8, 0.02);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6f, 1.3f);
    }

    private Blast() {}
}
