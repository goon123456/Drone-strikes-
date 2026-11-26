package com.steelrain.entity;

import com.steelrain.ModRegistry;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.util.Blast;
import com.steelrain.util.Recon;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Autopilot aircraft: Reaper (strike), Bayraktar TB2, Global Hawk and Orlan-10 (recon).
 * Runway types roll down the strip in front of their ground station and land back on it;
 * the Orlan is thrown from a catapult and comes down on a parachute.
 */
public class AircraftEntity extends FlyingWeaponEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public enum Phase { ROLL, OUTBOUND, ATTACK, ORBIT, RETURN, FINAL, ROLLOUT, CHUTE }

    private final AircraftType type;
    private BlockPos home = BlockPos.ZERO;
    private Vec3 runwayStart = Vec3.ZERO;
    private Vec3 forward = new Vec3(0, 0, 1);
    private double targetX, targetZ;
    private int hellfires;
    private Phase phase = Phase.OUTBOUND;
    private Vec3 dir = new Vec3(0, 0, 1);
    private double speed;
    private int age, phaseTicks, orbitTicks;
    private int fireCooldown = 10;
    private int jammedTicks;

    public AircraftEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        AircraftType found = AircraftType.REAPER;
        String path = EntityType.getKey(entityType).getPath();
        for (AircraftType t : AircraftType.values()) {
            if (t.id.equals(path)) found = t;
        }
        this.type = found;
        this.health = found.health;
    }

    public AircraftType type() { return type; }
    public Phase phase() { return phase; }
    public double targetX() { return targetX; }
    public double targetZ() { return targetZ; }

    public static AircraftEntity launch(ServerLevel level, AircraftType type, WeaponBlockEntity station, double tx, double tz,
                                        int hellfires, @Nullable UUID owner, @Nullable String ownerName) {
        AircraftEntity a = new AircraftEntity(ModRegistry.AIRCRAFT.get(type).get(), level);
        a.setOwner(owner, ownerName);
        a.home = station.getBlockPos();
        a.targetX = tx;
        a.targetZ = tz;
        a.hellfires = hellfires;
        a.forward = Vec3.atLowerCornerOf(station.facing().getNormal());
        Vec3 from;
        if (type.runway > 0) {
            a.runwayStart = station.runwayStart();
            from = a.parkingSpot();
            a.phase = Phase.ROLL;
            a.speed = 0;
            a.dir = a.forward;
        } else {
            from = station.muzzle();
            a.phase = Phase.OUTBOUND;
            a.speed = type.speed;
            a.dir = station.aimDirection();
            level.playSound(null, station.getBlockPos(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3f, 1.2f);
        }
        a.setPos(from);
        a.faceAlong(a.dir);
        preload(level, from.x, from.z);
        level.addFreshEntity(a);
        return a;
    }

    /** Where the aircraft sits before take-off and stops after landing. */
    private Vec3 parkingSpot() { return runwayStart.add(forward.scale(type.length / 2)); }

    private Vec3 runwayEnd() { return runwayStart.add(forward.scale(type.runway)); }

    @Override protected double modelRadius() { return type.span / 2 + 2; }
    @Override public boolean highValue() { return type != AircraftType.ORLAN; }
    @Override public boolean interceptable() { return phase != Phase.ROLL && phase != Phase.ROLLOUT; }
    @Override protected boolean radioLinked() { return phase != Phase.ROLL && phase != Phase.ROLLOUT && phase != Phase.CHUTE; }

    /** Blinded sensors straight away; if the link stays jammed for five seconds the aircraft gives up and turns home. */
    private void jammedTick() {
        if (jam < 0.5f) {
            jammedTicks = Math.max(0, jammedTicks - 1);
            return;
        }
        if (++jammedTicks > 100 && (phase == Phase.OUTBOUND || phase == Phase.ATTACK || phase == Phase.ORBIT)) {
            jammedTicks = 0;
            setPhase(Phase.RETURN);
            tell("message.steelrain.aircraft_jammed");
        }
    }

    /** Sends an airborne aircraft to a new target. */
    public boolean retask(double x, double z) {
        if (phase != Phase.OUTBOUND && phase != Phase.ATTACK && phase != Phase.ORBIT && phase != Phase.RETURN) return false;
        targetX = x;
        targetZ = z;
        setPhase(Phase.OUTBOUND);
        phaseTicks = 100;
        return true;
    }

    public boolean recall() {
        if (phase != Phase.OUTBOUND && phase != Phase.ATTACK && phase != Phase.ORBIT) return false;
        setPhase(Phase.RETURN);
        return true;
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        age++;
        phaseTicks++;
        Vec3 pos = position();
        jammedTick();
        if (phase != Phase.ROLL && phase != Phase.ROLLOUT) {
            Recon.scan(level, this, type.sensorRadius, phase == Phase.ORBIT, jam);
        }
        switch (phase) {
            case ROLL -> roll(level, pos);
            case ROLLOUT -> rollout(level, pos);
            case CHUTE -> chute(level, pos);
            default -> fly(level, pos);
        }
    }

    private void roll(ServerLevel level, Vec3 pos) {
        // Constant acceleration that reaches flying speed after 70% of the strip.
        speed = Math.min(type.speed, speed + type.speed * type.speed / (2 * 0.7 * type.runway));
        Vec3 next = pos.add(forward.scale(speed));
        keepChunksLoaded(level, next, forward.scale(speed));
        setPos(next);
        faceAlong(forward);
        if (speed >= type.speed * 0.98) {
            dir = forward.add(0, 0.3, 0).normalize();
            setPhase(Phase.OUTBOUND);
        }
    }

    private void rollout(ServerLevel level, Vec3 pos) {
        Vec3 stop = parkingSpot();
        double d = flat(pos, stop.x, stop.z);
        if (d < 1.2) {
            dock(level);
            return;
        }
        speed = Mth.clamp(d * 0.05, 0.12, speed);
        Vec3 next = pos.subtract(forward.scale(speed));
        // Ease onto the centreline.
        Vec3 rel = next.subtract(runwayStart);
        double along = rel.x * forward.x + rel.z * forward.z;
        Vec3 onLine = runwayStart.add(forward.scale(along));
        next = new Vec3(Mth.lerp(0.2, next.x, onLine.x), runwayStart.y, Mth.lerp(0.2, next.z, onLine.z));
        setPos(next);
        faceAlong(forward.scale(-1));
    }

    private void chute(ServerLevel level, Vec3 pos) {
        Vec3 top = Vec3.atBottomCenterOf(home).add(0, 1.5, 0);
        Vec3 next = new Vec3(Mth.lerp(0.1, pos.x, top.x), pos.y - 0.3, Mth.lerp(0.1, pos.z, top.z));
        keepChunksLoaded(level, next, Vec3.ZERO);
        if (next.y <= top.y + 0.5 || blockHit(pos, next) != null) {
            dock(level);
            return;
        }
        setPos(next);
        setRot(getYRot() + 6, 0); // swings gently under the canopy
    }

    private void fly(ServerLevel level, Vec3 pos) {
        Vec3 desired = dir;
        double k = 0.08;
        double v = type.speed;
        switch (phase) {
            case OUTBOUND -> {
                if (phaseTicks < 25) {
                    k = 0; // straight climb-out
                } else {
                    desired = terrainFollowing(dir, targetX, targetZ, type.altitude, type.speed);
                }
                double h = flat(pos, targetX, targetZ);
                if (type.armed() && hellfires > 0) {
                    if (h < 110) setPhase(Phase.ATTACK);
                } else if (h < type.orbitRadius + 25) {
                    setPhase(Phase.ORBIT);
                }
            }
            case ATTACK -> {
                desired = terrainFollowing(dir, targetX, targetZ, type.altitude, type.speed);
                double h = flat(pos, targetX, targetZ);
                if (hellfires > 0 && jam < 0.5f && --fireCooldown <= 0) {
                    hellfires--;
                    fireCooldown = 25;
                    MissileEntity.launch(level, MissileProfile.HELLFIRE, pos.add(0, -1.5, 0), targetX, targetZ, ownerId, ownerName, true);
                    com.steelrain.util.Sfx.gunfire(level, pos, "bmp_2_missile_fire_3p", "javelin_far", "rpg_veryfar");
                }
                if (h < 20 || (hellfires <= 0 && fireCooldown < 5)) setPhase(Phase.ORBIT);
            }
            case ORBIT -> {
                desired = orbit(pos);
                k = 0.1;
                if (phaseTicks % 100 == 1) loadAround(level, Mth.ceil(type.sensorRadius / 16.0));
                if (++orbitTicks > type.endurance) {
                    setPhase(Phase.RETURN);
                    tell("message.steelrain.aircraft_returning");
                }
            }
            case RETURN -> {
                if (type.runway == 0) {
                    desired = terrainFollowing(dir, home.getX() + 0.5, home.getZ() + 0.5, 30, type.speed);
                    if (flat(pos, home.getX() + 0.5, home.getZ() + 0.5) < 6) {
                        setPhase(Phase.CHUTE);
                        return;
                    }
                } else {
                    // Fly to a point out beyond the far end of the runway, lined up with it.
                    Vec3 fix = runwayEnd().add(forward.scale(130)).add(0, 45, 0);
                    double far = Math.max(170, (pos.y - fix.y) * 3.5);
                    if (flat(pos, fix.x, fix.z) > far) {
                        desired = terrainFollowing(dir, fix.x, fix.z, Math.min(type.altitude, 90), type.speed);
                    } else {
                        desired = fix.subtract(pos).normalize();
                        k = 0.12;
                        v = Math.max(0.6, type.speed * 0.75);
                        if (pos.distanceTo(fix) < 12 || flat(pos, fix.x, fix.z) < 8) setPhase(Phase.FINAL);
                    }
                }
            }
            case FINAL -> {
                Vec3 touchdown = runwayEnd().subtract(forward.scale(3));
                desired = touchdown.subtract(pos).normalize();
                k = 0.3;
                v = Math.max(0.45, type.speed * 0.55);
                if (pos.y <= runwayStart.y + 0.4 || flat(pos, touchdown.x, touchdown.z) < 2.5) {
                    touchDown(pos, v);
                    return;
                }
            }
            default -> { }
        }
        dir = steer(dir, desired, k);
        speed = v;
        Vec3 move = dir.scale(v);
        Vec3 next = pos.add(move);
        if (!keepChunksLoaded(level, next, move)) {
            age--;
            phaseTicks--;
            return;
        }
        if (phaseTicks > 12 || phase != Phase.OUTBOUND) {
            Vec3 hit = blockHit(pos.add(0, 0.3, 0), next.add(0, 0.3, 0));
            if (hit != null) {
                if (phase == Phase.FINAL && pos.y - runwayStart.y < 4) {
                    touchDown(pos, v);
                } else {
                    crash(level, pos);
                }
                return;
            }
        }
        setPos(next);
        faceAlong(dir);
    }

    private void touchDown(Vec3 pos, double v) {
        speed = v;
        setPos(pos.x, runwayStart.y, pos.z);
        setPhase(Phase.ROLLOUT);
    }

    /** Circle the target at cruise height. */
    private Vec3 orbit(Vec3 pos) {
        double rx = pos.x - targetX, rz = pos.z - targetZ;
        double d = Math.sqrt(rx * rx + rz * rz);
        if (d < 1) return dir;
        rx /= d;
        rz /= d;
        double corr = Mth.clamp((type.orbitRadius - d) / type.orbitRadius, -1, 1) * 1.5;
        double hx = -rz + rx * corr, hz = rx + rz * corr;
        double hl = Math.sqrt(hx * hx + hz * hz);
        // Hold height above the highest ground on the circle ahead, not just what is directly below.
        double ground = Math.max(groundAt(pos.x, pos.z), groundAt(targetX, targetZ));
        for (double ahead : new double[]{20, 45, 80}) {
            ground = Math.max(ground, groundAt(pos.x + hx / hl * ahead, pos.z + hz / hl * ahead));
        }
        double climb = Mth.clamp((ground + type.altitude - pos.y) / 40, -0.4, 0.7);
        return new Vec3(hx / hl, climb, hz / hl).normalize();
    }

    private static double flat(Vec3 pos, double x, double z) {
        double dx = x - pos.x, dz = z - pos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void crash(ServerLevel level, Vec3 pos) {
        LOGGER.info("{} (owner {}) crashed at {} {} {} during {}", type.id, ownerName == null ? "none" : ownerName,
                Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z), phase);
        discard();
        Blast.detonate(level, pos, 3f, 0, 20f, 5, true, this, ownerPlayer());
        if (level.getBlockEntity(home) instanceof WeaponBlockEntity station) station.aircraftLost();
        tell("message.steelrain.aircraft_lost");
    }

    private void dock(ServerLevel level) {
        if (level.getBlockEntity(home) instanceof WeaponBlockEntity station && station.type().launchesAircraft()) {
            station.dock(type, hellfires);
        } else {
            spawnAtLocation(new ItemStack(type.item().get()));
            if (hellfires > 0) spawnAtLocation(new ItemStack(ModRegistry.HELLFIRE_MISSILE.get(), hellfires));
        }
        tell("message.steelrain.aircraft_landed");
        discard();
    }

    @Override
    public void shotDown() {
        if (level().getBlockEntity(home) instanceof WeaponBlockEntity station) station.aircraftLost();
        tell("message.steelrain.aircraft_lost");
        super.shotDown();
    }

    private void tell(String key) {
        if (ownerPlayer() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(key, Component.translatable(type.translationKey())), false);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(BlockPos.ZERO);
        runwayStart = new Vec3(tag.getDouble("RX"), tag.getDouble("RY"), tag.getDouble("RZ"));
        forward = new Vec3(tag.getDouble("FX"), 0, tag.getDouble("FZ"));
        targetX = tag.getDouble("TX");
        targetZ = tag.getDouble("TZ");
        hellfires = tag.getInt("Hellfires");
        phase = Phase.values()[Math.floorMod(tag.getInt("Phase"), Phase.values().length)];
        dir = new Vec3(tag.getDouble("DX"), tag.getDouble("DY"), tag.getDouble("DZ"));
        speed = tag.getDouble("Speed");
        age = tag.getInt("Age");
        phaseTicks = tag.getInt("PhaseTicks");
        orbitTicks = tag.getInt("Orbit");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putDouble("RX", runwayStart.x); tag.putDouble("RY", runwayStart.y); tag.putDouble("RZ", runwayStart.z);
        tag.putDouble("FX", forward.x); tag.putDouble("FZ", forward.z);
        tag.putDouble("TX", targetX); tag.putDouble("TZ", targetZ);
        tag.putInt("Hellfires", hellfires);
        tag.putInt("Phase", phase.ordinal());
        tag.putDouble("DX", dir.x); tag.putDouble("DY", dir.y); tag.putDouble("DZ", dir.z);
        tag.putDouble("Speed", speed);
        tag.putInt("Age", age);
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("Orbit", orbitTicks);
    }
}
