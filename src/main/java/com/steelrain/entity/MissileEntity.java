package com.steelrain.entity;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.util.Recon;
import com.steelrain.util.Blast;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Every one-way munition: cruise missiles, ballistic missiles, rockets, shells, kamikaze drones. */
public class MissileEntity extends FlyingWeaponEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final MissileProfile profile;
    private Vec3 start = Vec3.ZERO;
    private double targetX, targetZ;
    private double targetY = Double.NaN;
    private Vec3 dir = new Vec3(0, 1, 0);
    private int age;
    private int flightTicks = 1;
    private double apex;
    private boolean whistled;
    private boolean jammed;
    private double driftAngle;
    /** Downward acceleration of an unguided shell, blocks per tick squared. Range tables are built on this. */
    public static final double GRAVITY = 0.05;

    // Unguided flight: fired from a gun a player laid by hand. Pure kinematics from the muzzle, nothing steers it.
    private boolean free;
    private Vec3 freeVelocity = Vec3.ZERO;
    private boolean announce = true;
    private boolean launched;

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.profile = ModRegistry.profileOf(type);
        this.health = profile.health();
    }

    public MissileProfile profile() { return profile; }

    /** Creates, aims and spawns a munition. */
    public static MissileEntity launch(ServerLevel level, MissileProfile profile, Vec3 from, double tx, double tz,
                                       @Nullable UUID owner, @Nullable String ownerName, boolean announce) {
        return launch(level, profile, from, tx, tz, owner, ownerName, announce, null);
    }

    /** Releases an unguided bomblet that keeps the carrier's velocity and falls. */
    public static MissileEntity drop(ServerLevel level, MissileProfile profile, Vec3 from, Vec3 velocity,
                                     @Nullable UUID owner, @Nullable String ownerName) {
        MissileEntity m = new MissileEntity(ModRegistry.typeOf(profile), level);
        m.setOwner(owner, ownerName);
        m.announce = false;
        m.launched = true;
        m.start = from;
        m.dir = velocity;
        m.setPos(from);
        level.addFreshEntity(m);
        return m;
    }

    /** @param initialDir direction the round leaves the launcher in, or null for the default. */
    public static MissileEntity launch(ServerLevel level, MissileProfile profile, Vec3 from, double tx, double tz,
                                       @Nullable UUID owner, @Nullable String ownerName, boolean announce,
                                       @Nullable Vec3 initialDir) {
        double spread = profile.spread();
        if (spread > 0 && Recon.observed(level, tx, tz, owner, ownerName)) {
            spread *= Config.SPOTTED_SPREAD.get(); // a friendly recon drone is correcting fire
        }
        MissileEntity m = new MissileEntity(ModRegistry.typeOf(profile), level);
        m.setOwner(owner, ownerName);
        m.announce = announce;
        m.launched = true;
        m.start = from;
        m.targetX = tx + level.random.nextGaussian() * spread;
        m.targetZ = tz + level.random.nextGaussian() * spread;
        double dx = m.targetX - from.x, dz = m.targetZ - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        Vec3 flat = dist < 1e-3 ? new Vec3(0, 0, 1) : new Vec3(dx / dist, 0, dz / dist);
        switch (profile.mode()) {
            case BALLISTIC -> {
                m.flightTicks = Math.max(20, (int) (dist / profile.speed()));
                m.apex = Mth.clamp(dist * 0.3, 24, 260);
                // Loads the one target chunk right away so the arc ends exactly on the ground there.
                level.getChunk(Mth.floor(m.targetX) >> 4, Mth.floor(m.targetZ) >> 4);
                m.targetY = m.groundAt(m.targetX, m.targetZ);
                m.dir = flat.add(0, 1, 0).normalize();
            }
            case CRUISE -> m.dir = initialDir != null ? initialDir
                    : profile == MissileProfile.TOMAHAWK ? new Vec3(0, 1, 0) : flat.add(0, 0.6, 0).normalize();
            case DIRECT -> m.dir = flat.add(0, -0.3, 0).normalize();
            case DROP -> m.dir = Vec3.ZERO;
        }
        m.setPos(from);
        m.faceAlong(m.dir);
        preload(level, from.x, from.z); // make sure the launch chunk ticks entities
        level.addFreshEntity(m);
        preload(level, m.targetX, m.targetZ);
        return m;
    }

    @Override public boolean interceptable() { return profile.interceptable(); }
    @Override public boolean highValue() { return profile.highValue(); }
    @Override protected boolean radioLinked() { return profile.jammable(); }

    /** Satellite navigation is being jammed: the aim point slides away for as long as it stays in the bubble. */
    private void drift() {
        if (!jammed) {
            jammed = true;
            driftAngle = random.nextDouble() * Math.PI * 2;
        }
        targetX += Math.cos(driftAngle) * jam * 0.9;
        targetZ += Math.sin(driftAngle) * jam * 0.9;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (!launched) {
            // Superb Warfare fires its weapons by creating the projectile and giving it a velocity: that is a shell
            // leaving a hand-laid gun. Anything else (e.g. /summon) has no business flying anywhere.
            if (getDeltaMovement().lengthSqr() < 0.01) {
                discard();
                return;
            }
            launched = true;
            free = true;
            freeVelocity = getDeltaMovement();
            start = position();
            age = 0;
            Player gunner = level.getNearestPlayer(this, 12);
            if (gunner != null) setOwner(gunner.getUUID(), gunner.getGameProfile().getName());
            if (profile == MissileProfile.MORTAR_SHELL) {
                com.steelrain.util.Sfx.gunfire(level, position(), "mortar_fire", "mortar_distant", "mortar_distant");
            } else {
                com.steelrain.util.Sfx.gunfire(level, position(), "plz_05_fire_3p", "plz_05_far", "plz_05_veryfar");
            }
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 14, 0.3, 0.3, 0.3, 0.03);
        }
        age++;
        if (jam > 0) drift();
        if (profile == MissileProfile.SHELL_CLUSTER && (free
                ? freeVelocity.y - GRAVITY * age < 0 && getY() - groundAt(getX(), getZ()) < 24
                : age >= flightTicks - 9)) {
            burst(level);
            return;
        }
        Vec3 pos = position();
        Vec3 next = free ? freeStep(pos) : switch (profile.mode()) {
            case BALLISTIC -> ballisticStep(pos);
            case CRUISE -> cruiseStep(pos);
            case DIRECT -> directStep(pos);
            case DROP -> {
                dir = dir.scale(0.99).add(0, -0.06, 0);
                yield pos.add(dir);
            }
        };
        Vec3 move = next.subtract(pos);
        // The whistle of a shell coming in, a moment before it lands.
        if (!whistled && move.y < -0.4 && !profile.smokeTrail() && profile.mode() != MissileProfile.FlightMode.DROP
                && profile.mode() != MissileProfile.FlightMode.CRUISE && getY() - groundAt(getX(), getZ()) < -move.y * 30) {
            whistled = true;
            com.steelrain.util.Sfx.play(level, pos, "shell_fly", 6f, 1f);
        }
        if (!keepChunksLoaded(level, next, move)) {
            age--; // hold until the terrain ahead has loaded
            return;
        }

        if (age > 4) {
            Vec3 hit = blockHit(pos, next);
            if (hit != null) {
                detonate(level, hit);
                return;
            }
        }
        if (next.y < level.getMinBuildHeight() - 16 || age > flightLimit()) {
            discard();
            return;
        }
        setPos(next);
        if (move.lengthSqr() > 1e-6) faceAlong(move);
        if (profile.smokeTrail() && (age & 1) == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.005);
        }
    }

    /** Exact projectile motion, so a shell lands where the range table says it will. */
    private Vec3 freeStep(Vec3 pos) {
        Vec3 next = start.add(freeVelocity.scale(age)).add(0, -0.5 * GRAVITY * age * age, 0);
        Vec3 d = next.subtract(pos);
        if (d.lengthSqr() > 1e-6) dir = d;
        return next;
    }

    private int flightLimit() {
        if (free) return 2400;
        return profile.mode() == MissileProfile.FlightMode.BALLISTIC ? flightTicks + 400 : 20 * 60 * 12;
    }

    private Vec3 ballisticStep(Vec3 pos) {
        double s = (double) age / flightTicks;
        if (s <= 1.0) {
            double x = Mth.lerp(s, start.x, targetX);
            double z = Mth.lerp(s, start.z, targetZ);
            double y = Mth.lerp(s, start.y, targetY) + apex * 4 * s * (1 - s);
            Vec3 next = new Vec3(x, y, z);
            Vec3 d = next.subtract(pos);
            if (d.lengthSqr() > 1e-6) dir = d;
            return next;
        }
        // Past the aim point (the ground was lower than expected): keep falling.
        dir = dir.add(0, -0.05, 0);
        return pos.add(dir);
    }

    private Vec3 cruiseStep(Vec3 pos) {
        double speed = profile.speed();
        double dx = targetX - pos.x, dz = targetZ - pos.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        Vec3 desired;
        double k;
        if (age < 12) {
            desired = dir; // boost straight off the launcher
            k = 0;
        } else if (h > profile.cruiseAltitude() * 0.7 + speed * 4) {
            desired = terrainFollowing(dir, targetX, targetZ, profile.cruiseAltitude(), speed);
            k = 0.12;
        } else {
            desired = aimPoint().subtract(pos).normalize();
            k = 0.3;
        }
        dir = steer(dir, desired, k);
        double v = age < 12 ? speed * (0.3 + 0.7 * age / 12.0) : speed;
        return pos.add(dir.scale(v));
    }

    private Vec3 directStep(Vec3 pos) {
        Vec3 desired = aimPoint().subtract(pos);
        if (desired.lengthSqr() > 1e-4) dir = steer(dir, desired.normalize(), age < 5 ? 0.1 : 0.35);
        return pos.add(dir.scale(profile.speed()));
    }

    private Vec3 aimPoint() {
        if (Double.isNaN(targetY) || (age % 10 == 0)) {
            if (level().hasChunk(Mth.floor(targetX) >> 4, Mth.floor(targetZ) >> 4)) {
                targetY = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        Mth.floor(targetX), Mth.floor(targetZ));
            } else if (Double.isNaN(targetY)) {
                return new Vec3(targetX, groundAt(getX(), getZ()), targetZ);
            }
        }
        return new Vec3(targetX, targetY - 0.5, targetZ);
    }

    /** Cluster shell opening above the target: a dozen bomblets fan out and fall. */
    private void burst(ServerLevel level) {
        Vec3 at = position();
        Vec3 forward = new Vec3(dir.x, 0, dir.z);
        forward = forward.lengthSqr() < 1e-6 ? Vec3.ZERO : forward.normalize().scale(0.25);
        discard();
        Blast.airburst(level, at);
        for (int i = 0; i < 12; i++) {
            Vec3 v = forward.add(random.nextGaussian() * 0.22, -0.3 - random.nextDouble() * 0.2, random.nextGaussian() * 0.22);
            drop(level, MissileProfile.BOMBLET, at, v, ownerId, ownerName);
        }
    }

    private void smoke(ServerLevel level, Vec3 at) {
        com.steelrain.entity.SmokeCloudEntity cloud = new com.steelrain.entity.SmokeCloudEntity(ModRegistry.SMOKE.get(), level);
        cloud.setPos(at.x, at.y + 0.5, at.z);
        level.addFreshEntity(cloud);
        com.steelrain.util.Sfx.play(level, at, "smoke_grenade_release", 3f, 1f);
    }

    /** Spreads fire over the ground around the impact and sets everything nearby alight. */
    private void ignite(ServerLevel level, Vec3 at) {
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(6))) {
            e.igniteForSeconds(8);
        }
        if (!Config.BLOCK_DAMAGE.get()) return;
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = 0; i < 40; i++) {
            int x = Mth.floor(at.x + random.nextGaussian() * 3), z = Mth.floor(at.z + random.nextGaussian() * 3);
            for (int dy = 3; dy >= -3; dy--) {
                m.set(x, Mth.floor(at.y) + dy, z);
                if (level.getBlockState(m).isAir() && level.getBlockState(m.below()).isSolidRender(level, m.below())) {
                    level.setBlockAndUpdate(m, net.minecraft.world.level.block.BaseFireBlock.getState(level, m));
                    break;
                }
            }
        }
    }

    private void detonate(ServerLevel level, Vec3 at) {
        Player owner = ownerPlayer();
        discard();
        if (profile == MissileProfile.SHELL_SMOKE) {
            smoke(level, at);
            return;
        }
        if (profile == MissileProfile.SHELL_INCENDIARY) ignite(level, at);
        LOGGER.info("{} (owner {}) hit {} {} {} in {}", profile.id(), ownerName == null ? "none" : ownerName,
                Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), level.dimension().location());
        Blast.detonate(level, at, profile.blastPower(), profile.craterRadius(), profile.damage(),
                profile.damageRadius(), profile.fire(), this, owner);
        if (announce && owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.steelrain.impact",
                    Component.translatable("entity.steelrain." + profile.id()),
                    Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)), true);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        start = new Vec3(tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ"));
        targetX = tag.getDouble("TX");
        targetZ = tag.getDouble("TZ");
        targetY = tag.contains("TY") ? tag.getDouble("TY") : Double.NaN;
        dir = new Vec3(tag.getDouble("DX"), tag.getDouble("DY"), tag.getDouble("DZ"));
        age = tag.getInt("Age");
        flightTicks = Math.max(1, tag.getInt("Flight"));
        apex = tag.getDouble("Apex");
        jammed = tag.getBoolean("Jammed");
        launched = tag.getBoolean("Launched");
        free = tag.getBoolean("Free");
        freeVelocity = new Vec3(tag.getDouble("FVX"), tag.getDouble("FVY"), tag.getDouble("FVZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("SX", start.x); tag.putDouble("SY", start.y); tag.putDouble("SZ", start.z);
        tag.putDouble("TX", targetX); tag.putDouble("TZ", targetZ);
        if (!Double.isNaN(targetY)) tag.putDouble("TY", targetY);
        tag.putDouble("DX", dir.x); tag.putDouble("DY", dir.y); tag.putDouble("DZ", dir.z);
        tag.putInt("Age", age);
        tag.putInt("Flight", flightTicks);
        tag.putDouble("Apex", apex);
        tag.putBoolean("Jammed", jammed);
        tag.putBoolean("Launched", launched);
        tag.putBoolean("Free", free);
        tag.putDouble("FVX", freeVelocity.x); tag.putDouble("FVY", freeVelocity.y); tag.putDouble("FVZ", freeVelocity.z);
    }
}
