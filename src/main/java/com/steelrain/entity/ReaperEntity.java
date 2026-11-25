package com.steelrain.entity;

import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.util.Blast;
import com.steelrain.weapon.MissileProfile;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Reusable strike drone: flies out, fires its Hellfires at the target, comes home and lands on its pad. */
public class ReaperEntity extends FlyingWeaponEntity {
    private static final double SPEED = 1.3;
    private static final double ALTITUDE = 60;

    private enum Phase { OUTBOUND, ATTACK, RETURN, LAND }

    private BlockPos home = BlockPos.ZERO;
    private double targetX, targetZ;
    private int hellfires;
    private Phase phase = Phase.OUTBOUND;
    private Vec3 dir = new Vec3(0, 1, 0);
    private int age;
    private int fireCooldown = 10;

    public ReaperEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.health = 16f;
    }

    public static ReaperEntity launch(ServerLevel level, BlockPos home, double tx, double tz, int hellfires,
                                      @Nullable UUID owner, @Nullable String ownerName) {
        ReaperEntity r = new ReaperEntity(com.steelrain.ModRegistry.REAPER.get(), level);
        r.setOwner(owner, ownerName);
        r.home = home;
        r.targetX = tx;
        r.targetZ = tz;
        r.hellfires = hellfires;
        Vec3 from = Vec3.atBottomCenterOf(home).add(0, 2.0, 0);
        double dx = tx - from.x, dz = tz - from.z;
        double d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        r.dir = new Vec3(dx / d, 0.7, dz / d).normalize();
        r.setPos(from);
        r.faceAlong(r.dir);
        preload(level, from.x, from.z);
        level.addFreshEntity(r);
        return r;
    }

    @Override public boolean jammable() { return phase == Phase.OUTBOUND || phase == Phase.ATTACK; }

    @Override
    public void jam() {
        if (!jammable()) return;
        phase = Phase.RETURN;
        tell("message.steelrain.reaper_jammed");
    }

    @Override
    protected void serverTick(ServerLevel level) {
        age++;
        Vec3 pos = position();
        double speed = SPEED;
        Vec3 desired;
        double k = 0.08;
        switch (phase) {
            case OUTBOUND -> {
                desired = terrainFollowing(dir, targetX, targetZ, ALTITUDE, SPEED);
                if (flat(pos, targetX, targetZ) < 110) phase = Phase.ATTACK;
            }
            case ATTACK -> {
                desired = terrainFollowing(dir, targetX, targetZ, ALTITUDE, SPEED);
                double h = flat(pos, targetX, targetZ);
                if (hellfires > 0 && --fireCooldown <= 0) {
                    hellfires--;
                    fireCooldown = 25;
                    MissileEntity.launch(level, MissileProfile.HELLFIRE, pos.add(0, -1.2, 0), targetX, targetZ,
                            ownerId, ownerName, true);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 6f, 0.7f);
                }
                if (h < 20 || (hellfires <= 0 && fireCooldown < 5)) phase = Phase.RETURN;
            }
            case RETURN -> {
                desired = terrainFollowing(dir, home.getX() + 0.5, home.getZ() + 0.5, ALTITUDE, SPEED);
                if (flat(pos, home.getX() + 0.5, home.getZ() + 0.5) < 80) phase = Phase.LAND;
            }
            default -> {
                Vec3 pad = Vec3.atBottomCenterOf(home).add(0, 1.6, 0);
                Vec3 to = pad.subtract(pos);
                double dist = to.length();
                if (dist < 2.2) {
                    dock(level);
                    return;
                }
                desired = to.scale(1 / dist);
                speed = Math.max(0.3, Math.min(SPEED, dist / 30));
                k = 0.3;
            }
        }
        if (age < 15) k = 0; // straight climb-out
        dir = steer(dir, desired, k);
        Vec3 next = pos.add(dir.scale(speed));
        if (!keepChunksLoaded(level, next, dir.scale(speed))) {
            age--;
            return;
        }
        if (age > 10 && blockHit(pos, next) != null) {
            if (phase == Phase.LAND && pos.distanceTo(Vec3.atCenterOf(home)) < 6) {
                dock(level);
            } else {
                discard();
                Blast.detonate(level, pos, 3f, 0, 20f, 5, true, this, ownerPlayer());
                tell("message.steelrain.reaper_lost");
            }
            return;
        }
        setPos(next);
        faceAlong(dir);
    }

    private static double flat(Vec3 pos, double x, double z) {
        double dx = x - pos.x, dz = z - pos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void dock(ServerLevel level) {
        if (level.getBlockEntity(home) instanceof WeaponBlockEntity pad && pad.type() == WeaponType.REAPER_PAD) {
            pad.dockReaper(hellfires);
        } else {
            spawnAtLocation(new net.minecraft.world.item.ItemStack(com.steelrain.ModRegistry.MQ9_REAPER.get()));
            if (hellfires > 0) {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(com.steelrain.ModRegistry.HELLFIRE_MISSILE.get(), hellfires));
            }
        }
        tell("message.steelrain.reaper_landed");
        discard();
    }

    @Override
    public void shotDown() {
        tell("message.steelrain.reaper_lost");
        super.shotDown();
    }

    private void tell(String key) {
        if (ownerPlayer() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(key), false);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(BlockPos.ZERO);
        targetX = tag.getDouble("TX");
        targetZ = tag.getDouble("TZ");
        hellfires = tag.getInt("Hellfires");
        phase = Phase.values()[Math.floorMod(tag.getInt("Phase"), Phase.values().length)];
        dir = new Vec3(tag.getDouble("DX"), tag.getDouble("DY"), tag.getDouble("DZ"));
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putDouble("TX", targetX); tag.putDouble("TZ", targetZ);
        tag.putInt("Hellfires", hellfires);
        tag.putInt("Phase", phase.ordinal());
        tag.putDouble("DX", dir.x); tag.putDouble("DY", dir.y); tag.putDouble("DZ", dir.z);
        tag.putInt("Age", age);
    }
}
