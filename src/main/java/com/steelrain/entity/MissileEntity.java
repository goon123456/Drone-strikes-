package com.steelrain.entity;

import com.steelrain.ModRegistry;
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
    private boolean jammed;
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
        MissileEntity m = new MissileEntity(ModRegistry.typeOf(profile), level);
        m.setOwner(owner, ownerName);
        m.announce = announce;
        m.launched = true;
        m.start = from;
        m.targetX = tx + level.random.nextGaussian() * profile.spread();
        m.targetZ = tz + level.random.nextGaussian() * profile.spread();
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
            case CRUISE -> m.dir = profile == MissileProfile.TOMAHAWK ? new Vec3(0, 1, 0) : flat.add(0, 0.6, 0).normalize();
            case DIRECT -> m.dir = flat.add(0, -0.3, 0).normalize();
        }
        m.setPos(from);
        m.faceAlong(m.dir);
        preload(level, from.x, from.z); // make sure the launch chunk ticks entities
        level.addFreshEntity(m);
        preload(level, m.targetX, m.targetZ);
        return m;
    }

    @Override public boolean interceptable() { return profile.interceptable(); }
    @Override public boolean jammable() { return profile.jammable() && !jammed; }

    @Override
    public void jam() {
        if (jammed) return;
        jammed = true;
        // Guidance lost: the drone wanders off and comes down somewhere nearby.
        double a = random.nextDouble() * Math.PI * 2;
        double r = 40 + random.nextDouble() * 50;
        targetX = getX() + Math.cos(a) * r;
        targetZ = getZ() + Math.sin(a) * r;
        targetY = Double.NaN;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (!launched) { // e.g. created with /summon: it has no target, so it must not fly anywhere
            discard();
            return;
        }
        age++;
        Vec3 pos = position();
        Vec3 next = switch (profile.mode()) {
            case BALLISTIC -> ballisticStep(pos);
            case CRUISE -> cruiseStep(pos);
            case DIRECT -> directStep(pos);
        };
        Vec3 move = next.subtract(pos);
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

    private int flightLimit() {
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

    private void detonate(ServerLevel level, Vec3 at) {
        Player owner = ownerPlayer();
        discard();
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
    }
}
