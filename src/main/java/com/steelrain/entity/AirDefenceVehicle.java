package com.steelrain.entity;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.projectile.DestroyableProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.util.Ballistics;
import com.steelrain.util.Teams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Steel Rain air defence built on Superb Warfare's turret vehicle. Driving, seats, HUD, energy, ammunition and the
 * cannons themselves are all Superb Warfare; this class replaces its self-defence turret AI with area air
 * defence: it engages hostile drones, missiles and aircraft anywhere in range, aims at where they will be, and can
 * launch Steel Rain interceptor missiles.
 */
public class AirDefenceVehicle extends AutoAimableEntity {
    public enum Kind {
        //      guns  gunRange missileRange highValueOnly ceiling missileCooldown energy/missile
        ZU23(true, 64, 0, false, true, 0, 0),
        GEPARD(true, 96, 0, false, true, 0, 0),
        PANTSIR(true, 64, 128, false, true, 30, 5000),
        PATRIOT(false, 0, 400, true, false, 60, 25000);

        final boolean guns;
        final double gunRange, missileRange;
        final boolean highValueOnly, ceiling;
        final int missileCooldown, missileEnergy;

        Kind(boolean guns, double gunRange, double missileRange, boolean highValueOnly, boolean ceiling, int missileCooldown, int missileEnergy) {
            this.guns = guns;
            this.gunRange = gunRange;
            this.missileRange = missileRange;
            this.highValueOnly = highValueOnly;
            this.ceiling = ceiling;
            this.missileCooldown = missileCooldown;
            this.missileEnergy = missileEnergy;
        }
    }

    /** FE drawn every second by the radar while it searches (doubled while tracking). */
    private static final int RADAR_ENERGY = 250;

    private final Kind kind;
    private int targetId = -1;
    private int cooldown;
    private int trackTicks;
    @Nullable private String ownerName;

    public AirDefenceVehicle(EntityType<?> type, Level level) {
        super(type, level);
        String path = EntityType.getKey(type).getPath();
        this.kind = switch (path) {
            case "gepard" -> Kind.GEPARD;
            case "pantsir" -> Kind.PANTSIR;
            case "patriot" -> Kind.PATRIOT;
            default -> Kind.ZU23;
        };
    }

    private Item missileItem() {
        return kind == Kind.PATRIOT ? ModRegistry.PATRIOT_MISSILE.get() : ModRegistry.PANTSIR_MISSILE.get();
    }

    private int missileSlot() {
        if (kind.missileRange <= 0) return -1;
        for (int i = 0; i < getContainerSize(); i++) {
            if (getItem(i).is(missileItem())) return i;
        }
        return -1;
    }

    @Override
    public void autoAim() {
        if (!(level() instanceof ServerLevel level) || isWreck() || getFirstPassenger() != null || !getActive()) return;
        if (cooldown > 0) cooldown--;
        LivingEntity ownerEntity = getOwner();
        if (ownerEntity instanceof Player p) ownerName = p.getGameProfile().getName();

        // Radar needs power: a little while searching, twice that while tracking.
        if (getEnergy() < RADAR_ENERGY * 2) {
            targetId = -1;
            return;
        }
        if (tickCount % 20 == 0) consumeEnergy(targetId >= 0 ? RADAR_ENERGY * 2 : RADAR_ENERGY);

        boolean hasMissile = missileSlot() >= 0;
        boolean hasRounds = kind.guns && getAmmoCount("Main") > 0;
        if (!hasMissile && !hasRounds) {
            targetId = -1;
            return;
        }
        Vec3 centre = position().add(0, getBbHeight(), 0);
        long now = level.getGameTime();

        Entity target = targetId < 0 ? null : level.getEntity(targetId);
        if (target == null || !target.isAlive() || ++trackTicks > 400) {
            targetId = -1;
            if (tickCount % (kind == Kind.PATRIOT ? 10 : 4) != 0) return;
            target = findTarget(level, centre, hasMissile ? Math.max(kind.missileRange, kind.gunRange * 1.3) : kind.gunRange * 1.3, !hasMissile, now);
            if (target == null) return;
            targetId = target.getId();
            trackTicks = 0;
        }

        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 targetMove = Ballistics.velocity(target);
        double dist = centre.distanceTo(targetPos);
        boolean highValue = target instanceof FlyingWeaponEntity f ? f.highValue() : target instanceof VehicleEntity;
        boolean useGuns = hasRounds && dist <= kind.gunRange && (!hasMissile || !highValue);
        if (!useGuns && !hasMissile) {
            if (dist > kind.gunRange * 1.4) targetId = -1;
            if (!hasRounds) return;
            useGuns = dist <= kind.gunRange;
        }

        // Aim at where the target will be when the shot arrives, not where it is now.
        Vec3 from = kind.guns ? getShootPos("Main", 1f) : centre;
        double shotSpeed = useGuns ? getProjectileVelocity("Main") : (kind == Kind.PATRIOT ? 6.0 : 4.5);
        Vec3 aim = Ballistics.intercept(from, targetPos, targetMove, shotSpeed);
        if (aim == null) aim = targetPos;
        if (useGuns) {
            double t = from.distanceTo(aim) / shotSpeed;
            aim = aim.add(0, 0.5 * getProjectileGravity("Main") * t * t, 0);
        }
        Vec3 desired = aim.subtract(from).normalize();
        turretAutoAimFromVector(desired);

        Vec3 pointing = kind.guns ? getShootVec("Main", 1f) : desired;
        double off = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, pointing.normalize().dot(desired)))));

        if (useGuns) {
            if (target instanceof FlyingWeaponEntity f) f.engagedUntil = Math.max(f.engagedUntil, now + 10);
            // The shells leave along the barrels as they point right now, so a slow turret or a jinking target means misses.
            if (off <= 2.5 && tickCount % 2 == 0) vehicleShoot(ownerEntity, "Main", aim);
            return;
        }
        if (cooldown > 0 || off > 14 || getEnergy() < kind.missileEnergy) return;
        if (target instanceof FlyingWeaponEntity f && f.engagedUntil > now) return;
        int slot = missileSlot();
        if (slot < 0) return;
        removeItem(slot, 1);
        consumeEnergy(kind.missileEnergy);

        InterceptorEntity missile = new InterceptorEntity(
                kind == Kind.PATRIOT ? ModRegistry.PATRIOT_INTERCEPTOR.get() : ModRegistry.INTERCEPTOR.get(), level);
        Vec3 muzzle = centre.add(desired.scale(2.5)).add(0, 0.8, 0);
        missile.setPos(muzzle);
        missile.setOwner(getOwnerUUID(), ownerName);
        missile.setTarget(target, desired.add(0, 0.25, 0));
        level.addFreshEntity(missile);
        if (target instanceof FlyingWeaponEntity f) f.engagedUntil = now + (kind == Kind.PATRIOT ? 160 : 60);
        cooldown = kind.missileCooldown;
        targetId = -1;
        level.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 6f, kind == Kind.PATRIOT ? 0.5f : 1.4f);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 20, 0.3, 0.3, 0.3, 0.06);
    }

    @Nullable
    private Entity findTarget(ServerLevel level, Vec3 centre, double range, boolean gunsOnly, long now) {
        Entity best = null;
        double bestDist = range * range;
        for (Entity e : level.getEntities(this, new AABB(centre, centre).inflate(range), this::isThreat)) {
            double d = e.distanceToSqr(centre);
            if (d >= bestDist) continue;
            if (kind.ceiling && e.getY() - getY() > Config.AIR_DEFENCE_CEILING.get()) continue;
            if (!gunsOnly && e instanceof FlyingWeaponEntity f && f.engagedUntil >= now) continue;
            best = e;
            bestDist = d;
        }
        return best;
    }

    /** Hostile things in the air: Steel Rain drones and missiles, Superb Warfare aircraft and guided weapons. */
    private boolean isThreat(Entity e) {
        if (!e.isAlive()) return false;
        UUID owner = getOwnerUUID();
        if (e instanceof FlyingWeaponEntity f) {
            return f.interceptable() && (!kind.highValueOnly || f.highValue())
                    && !Teams.friendly(level(), owner, ownerName, f.ownerId(), f.ownerName());
        }
        if (e instanceof VehicleEntity v) {
            VehicleType type = v.getVehicleType();
            if (v.isWreck() || (type != VehicleType.AIRPLANE && type != VehicleType.HELICOPTER && type != VehicleType.AIRSHIP
                    && !(kind != Kind.PATRIOT && type == VehicleType.DRONE))) return false;
            if (v.onGround()) return false;
            Entity crew = v.getFirstPassenger();
            if (!(crew instanceof Player p)) return false; // leave empty aircraft alone
            return !Teams.friendly(level(), owner, ownerName, p.getUUID(), p.getGameProfile().getName());
        }
        if (e instanceof DestroyableProjectile && e instanceof Projectile p && !kind.highValueOnly) {
            return p.getOwner() instanceof Player shooter
                    && !Teams.friendly(level(), owner, ownerName, shooter.getUUID(), shooter.getGameProfile().getName());
        }
        return false;
    }
}
