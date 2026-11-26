package com.steelrain.entity.vehicle;

import com.steelrain.Config;
import com.steelrain.entity.CannonShellEntity;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.util.Ballistics;
import com.steelrain.util.Teams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Air defence on Steel Rain's own vehicles. Crewed, the gunner looks where to shoot and holds fire; the radar tracks
 * the threat nearest the sight and shows where to aim, and a lock makes the turret follow that target by itself.
 * Uncrewed with automatic mode on, it searches for hostile drones, missiles and aircraft, aims at where they will be
 * and engages them with its guns or missiles. Every shot is a real object with a flight time.
 */
public abstract class SrAirDefence extends SrVehicle {
    private static final EntityDataAccessor<Boolean> AUTO = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ROUNDS = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BELT_AP = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.BOOLEAN);
    /** Belts (HE, AP) and missiles in the store, for the gunner's sight: the store itself is only on the server. */
    private static final EntityDataAccessor<Integer> STOCK = SynchedEntityData.defineId(SrAirDefence.class, EntityDataSerializers.INT);

    public static final int ACTION_FIRE_ON = 11, ACTION_FIRE_OFF = 12, ACTION_LOCK = 13, ACTION_MISSILE = 14, ACTION_BELT = 15, ACTION_AUTO = 16;

    /** FE drawn every second by the radar while it searches (doubled while tracking). */
    private static final int RADAR_ENERGY = 250;

    private boolean trigger;
    private Vec3 aimError = Vec3.ZERO;
    private double shotDue;
    private int nextBarrel, shotsFired, cooldown, trackTicks, beltLoad;
    @Nullable private String ownerNameCache;

    protected SrAirDefence(EntityType<?> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ what each system defines

    /** The seat that aims and fires, or -1 when nobody can crew it. */
    public abstract int gunnerSeat();
    /** Radar-laid: tracks targets, shows the lead, can lock on. */
    public abstract boolean hasRadar();
    public boolean hasGuns() { return muzzles().length > 0; }
    /** Muzzles in vehicle space with the guns level and facing forward; fired in turn. */
    protected abstract Vec3[] muzzles();
    /** Muzzle velocity, blocks per tick. */
    public double shellSpeed() { return 6.0; }
    public float shellGravity() { return 0.03f; }
    /** Dispersion of the rounds, radians (one standard deviation per axis). */
    protected double spread() { return 0.004; }
    /** Rounds a minute from all barrels together. */
    protected abstract int roundsPerMinute();
    protected abstract float shellDamage();
    protected abstract float fuseReach();
    protected abstract float fuseChance();
    public abstract double gunRange();
    public double missileRange() { return 0; }
    protected int missileCooldown() { return 30; }
    protected int missileEnergy() { return 5000; }
    @Nullable protected Item missileItem() { return null; }
    @Nullable protected EntityType<InterceptorEntity> interceptor() { return null; }
    /** Where interceptors leave, in vehicle space with the launcher level and facing forward. */
    protected Vec3 missileTube() { return new Vec3(0, 3, 0); }
    /** Patriot: leaves drones to the guns and only takes on missiles and aircraft. */
    protected boolean highValueOnly() { return false; }
    /** Guns do not fire at things far above them. */
    protected boolean hasCeiling() { return true; }
    @Nullable protected abstract Item heBelt();
    @Nullable protected abstract Item apBelt();
    protected int roundsPerBelt() { return 50; }
    /** Ticks to put the next belt on: the radar guns feed from big magazines almost without a pause. */
    protected int beltChangeTicks() { return 6; }
    public double minPitch() { return -8; }
    public double maxPitch() { return 85; }
    protected abstract float traverseRate();
    protected abstract float elevateRate();
    /** Close, far and very far firing sounds (Superb Warfare's, until Steel Rain has its own). */
    protected abstract String[] gunSounds();

    // ------------------------------------------------------------------ state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(AUTO, false);
        b.define(TARGET, -1);
        b.define(LOCKED, false);
        b.define(ROUNDS, 0);
        b.define(BELT_AP, false);
        b.define(STOCK, 0);
    }

    public boolean auto() { return entityData.get(AUTO); }
    public boolean locked() { return entityData.get(LOCKED); }
    public int rounds() { return entityData.get(ROUNDS); }
    public boolean beltAp() { return entityData.get(BELT_AP); }

    @Nullable
    public Entity target() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    private void setTarget(@Nullable Entity e) {
        int id = e == null ? -1 : e.getId();
        if (entityData.get(TARGET) != id) entityData.set(TARGET, id);
        if (e == null && locked()) entityData.set(LOCKED, false);
    }

    public boolean isGunner(Entity e) { return gunnerSeat() >= 0 && hasPassenger(e) && seatOf(e) == gunnerSeat(); }

    @Override
    protected Vec3 seatPosition(int seat) {
        return seat == gunnerSeat() ? onTurret(seats()[seat], false) : seats()[seat];
    }

    /** Belts or missiles in the store as the gunner's game knows them. */
    public int stock(int which) { return (entityData.get(STOCK) >> (which * 10)) & 1023; }

    public int belts(boolean ap) {
        Item item = ap ? apBelt() : heBelt();
        int n = 0;
        if (item == null) return 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) n += inventory.getItem(i).getCount();
        return n;
    }

    public int missiles() {
        Item item = missileItem();
        int n = 0;
        if (item == null) return 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) n += inventory.getItem(i).getCount();
        return n;
    }

    /** Where the guns point now, in the world. */
    public Vec3 gunDirection() {
        Vec3 m = muzzles().length > 0 ? muzzles()[0] : missileTube();
        return toWorld(onTurret(m.add(0, 0, 1), true)).subtract(toWorld(onTurret(m, true))).normalize();
    }

    /** The middle of the turret in the world, for working out leads. */
    public Vec3 turretCentre() { return toWorld(onTurret(barrelPivot(), false)); }

    // ------------------------------------------------------------------ the crew and the radar

    @Override
    public void action(ServerPlayer player, int action) {
        super.action(player, action);
        if (action == ACTION_AUTO) {
            if (getOwnerUUID() != null && !getOwnerUUID().equals(player.getUUID()) && !player.getAbilities().instabuild) return;
            if (getOwnerUUID() == null) setOwner(player.getUUID());
            entityData.set(AUTO, !auto());
            player.displayClientMessage(Component.translatable(auto() ? "message.steelrain.auto_on" : "message.steelrain.auto_off"), true);
            return;
        }
        if (!isGunner(player)) return;
        switch (action) {
            case ACTION_FIRE_ON -> trigger = true;
            case ACTION_FIRE_OFF -> trigger = false;
            case ACTION_BELT -> {
                if (apBelt() != null) entityData.set(BELT_AP, !beltAp());
            }
            case ACTION_LOCK -> {
                if (!hasRadar()) return;
                if (locked()) entityData.set(LOCKED, false);
                else if (target() != null) entityData.set(LOCKED, true);
            }
            case ACTION_MISSILE -> {
                Entity t = target();
                if (t != null && level() instanceof ServerLevel sl) launchMissile(sl, t, true);
            }
            default -> { }
        }
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (cooldown > 0) cooldown--;
        if (getOwnerUUID() != null && level.getPlayerByUUID(getOwnerUUID()) instanceof Player p) ownerNameCache = p.getGameProfile().getName();
        loadBelt();
        if (tickCount % 10 == 0) entityData.set(STOCK, Math.min(1023, belts(false)) | Math.min(1023, belts(true)) << 10 | Math.min(1023, missiles()) << 20);
        Entity gunner = gunnerSeat() >= 0 ? inSeat(gunnerSeat()) : null;
        if (gunner != null) {
            crewed(level, gunner);
        } else if (auto()) {
            trigger = false;
            automatic(level);
        } else {
            trigger = false;
            setTarget(null);
        }
    }

    /** The loader puts the next belt of the chosen type on the gun when the last one runs out. */
    private void loadBelt() {
        if (!hasGuns() || rounds() > 0) { beltLoad = 0; return; }
        Item want = beltAp() && apBelt() != null ? apBelt() : heBelt();
        Item other = want == heBelt() ? apBelt() : heBelt();
        int slot = slotOf(want);
        if (slot < 0 && other != null) slot = slotOf(other);
        if (slot < 0) return;
        if (++beltLoad < beltChangeTicks()) return;
        boolean ap = inventory.getItem(slot).is(apBelt());
        inventory.removeItem(slot, 1);
        entityData.set(BELT_AP, ap);
        entityData.set(ROUNDS, roundsPerBelt());
        beltLoad = 0;
        if (level() instanceof ServerLevel sl) com.steelrain.util.Sfx.play(sl, position(), "cannon_reload", 1.5f, 1.3f);
    }

    private int slotOf(@Nullable Item item) {
        if (item == null) return -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) return i;
        return -1;
    }

    private boolean radarPowered(ServerLevel level, boolean tracking) {
        if (!Config.REQUIRE_ENERGY.get()) return true;
        if (energy() < RADAR_ENERGY * 2) return false;
        if (tickCount % 20 == 0) useEnergy(tracking ? RADAR_ENERGY * 2 : RADAR_ENERGY);
        return true;
    }

    /** Crewed: the radar follows what the gunner is looking at; a lock lays the guns on it by itself. */
    private void crewed(ServerLevel level, Entity gunner) {
        Vec3 look = gunner.getLookAngle();
        Entity t = target();
        boolean valid = t != null && t.isAlive() && !(t instanceof FlyingWeaponEntity f && f.falling())
                && t.distanceTo(this) < Math.max(gunRange(), missileRange()) * 1.5;
        if (!hasRadar() || !radarPowered(level, valid)) {
            setTarget(null);
        } else if (locked() && valid) {
            trackTicks = 0;
        } else if (tickCount % 4 == 0) {
            // track the threat closest to where the gunner is looking
            Entity best = null;
            double bestAngle = 25;
            Vec3 centre = turretCentre();
            double range = Math.max(gunRange(), missileRange()) * 1.3;
            for (Entity e : level.getEntities(this, new AABB(centre, centre).inflate(range), this::isThreat)) {
                Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(centre);
                double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
                if (angle < bestAngle && to.length() < range) { best = e; bestAngle = angle; }
            }
            setTarget(best);
        }
        Vec3 want = locked() && target() != null ? leadOn(target(), true) : look;
        slewTo(want);
        if (trigger) shoot(level, gunner);
    }

    /** Uncrewed with automatic mode on: Steel Rain's area defence. */
    private void automatic(ServerLevel level) {
        if (!radarPowered(level, target() != null)) { setTarget(null); return; }
        boolean hasMissile = missiles() > 0 && interceptor() != null;
        boolean hasRounds = hasGuns() && (rounds() > 0 || belts(false) + belts(true) > 0);
        if (!hasMissile && !hasRounds) { setTarget(null); return; }
        Vec3 centre = turretCentre();
        long now = level.getGameTime();
        Entity target = target();
        if (target == null || !target.isAlive() || (target instanceof FlyingWeaponEntity down && down.falling()) || ++trackTicks > 400) {
            setTarget(null);
            if (tickCount % (hasGuns() ? 4 : 10) != 0) return;
            target = findTarget(level, centre, hasMissile ? Math.max(missileRange(), gunRange() * 1.3) : gunRange() * 1.3, !hasMissile, now);
            if (target == null) return;
            setTarget(target);
            trackTicks = 0;
        }
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        double dist = centre.distanceTo(targetPos);
        boolean highValue = target instanceof FlyingWeaponEntity f ? f.highValue() : com.steelrain.compat.Sbw.isVehicle(target);
        boolean useGuns = hasRounds && dist <= gunRange() && (!hasMissile || !highValue);
        if (!useGuns && !hasMissile) {
            if (dist > gunRange() * 1.4) setTarget(null);
            if (!hasRounds) return;
            useGuns = dist <= gunRange();
        }
        Vec3 desired = leadOn(target, useGuns);
        if (!hasRadar()) {
            // laid by eye: the crew judge the lead roughly, and the error wanders as they correct
            if (tickCount % 10 == 0) aimError = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.09);
            desired = desired.add(aimError).normalize();
        }
        slewTo(desired);
        double off = Math.toDegrees(Math.acos(Mth.clamp(gunDirection().dot(desired), -1, 1)));
        if (useGuns) {
            if (target instanceof FlyingWeaponEntity f) f.engagedUntil = Math.max(f.engagedUntil, now + 10);
            // the shells leave along the barrels as they point right now: a slow turret or a jinking target means misses
            if (off <= 2.5) shoot(level, null);
            return;
        }
        if (off > 14) return;
        if (target instanceof FlyingWeaponEntity f && f.engagedUntil > now) return;
        launchMissile(level, target, false);
    }

    /** Direction to aim so a shell (or a missile) meets the target where it will be. */
    public Vec3 leadOn(Entity target, boolean guns) {
        Vec3 from = turretCentre();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        double speed = guns ? shellSpeed() : 4.5;
        Vec3 aim = Ballistics.intercept(from, targetPos, Ballistics.velocity(target), speed);
        if (aim == null) aim = targetPos;
        if (guns) {
            double t = from.distanceTo(aim) / speed;
            aim = aim.add(0, 0.5 * shellGravity() * t * t, 0);
        }
        return aim.subtract(from).normalize();
    }

    /** Turns the turret and raises the guns towards a direction, at their own pace. */
    private void slewTo(Vec3 dir) {
        float wantYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) - getYRot());
        float wantPitch = (float) Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1))), minPitch(), maxPitch());
        float dy = Mth.clamp(Mth.wrapDegrees(wantYaw - turretYaw()), -traverseRate(), traverseRate());
        float dp = Mth.clamp(wantPitch - gunPitch(), -elevateRate(), elevateRate());
        if (Math.abs(dy) > 0.001f) setTurretYaw(turretYaw() + dy);
        if (Math.abs(dp) > 0.001f) setGunPitch(gunPitch() + dp);
    }

    /** Fires the guns at their rate of fire, the barrels taking turns. */
    private void shoot(ServerLevel level, @Nullable Entity gunner) {
        if (!hasGuns()) return;
        shotDue += roundsPerMinute() / 1200.0;
        Vec3[] muzzles = muzzles();
        boolean fired = false;
        while (shotDue >= 1) {
            shotDue -= 1;
            if (rounds() <= 0) { shotDue = 0; break; }
            entityData.set(ROUNDS, rounds() - 1);
            Vec3 local = muzzles[nextBarrel++ % muzzles.length];
            Vec3 muzzle = toWorld(onTurret(local, true));
            Vec3 dir = toWorld(onTurret(local.add(0, 0, 1), true)).subtract(muzzle).normalize();
            // a little dispersion, as real guns have
            dir = dir.add(random.nextGaussian() * spread(), random.nextGaussian() * spread(), random.nextGaussian() * spread()).normalize();
            CannonShellEntity.fire(level, this, gunner, muzzle, dir.scale(shellSpeed()), shellDamage(), shellGravity(), beltAp(),
                    shotsFired++ % 2 == 0, fuseReach(), fuseChance(), getOwnerUUID(), ownerNameCache);
            if (shotsFired % 2 == 0) com.steelrain.util.Sfx.gunfire(level, muzzle, gunSounds()[0], gunSounds()[1], gunSounds()[2]);
            level.sendParticles(ParticleTypes.FLAME, muzzle.x + dir.x * 0.3, muzzle.y + dir.y * 0.3, muzzle.z + dir.z * 0.3, 1, 0.05, 0.05, 0.05, 0.01);
            if (shotsFired % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.1, 0.1, 0.1, 0.01);
            fired = true;
        }
        if (fired) markFired();
    }

    private void launchMissile(ServerLevel level, Entity target, boolean byHand) {
        EntityType<InterceptorEntity> type = interceptor();
        int slot = slotOf(missileItem());
        if (type == null || slot < 0 || cooldown > 0) return;
        if (target.distanceTo(this) > missileRange()) return;
        if (!useEnergy(missileEnergy())) return;
        inventory.removeItem(slot, 1);
        Vec3 dir = byHand ? leadOn(target, false) : gunDirection();
        InterceptorEntity missile = new InterceptorEntity(type, level);
        Vec3 muzzle = toWorld(onTurret(missileTube(), true));
        missile.setPos(muzzle);
        missile.setOwner(getOwnerUUID(), ownerNameCache);
        missile.setTarget(target, dir.add(0, 0.25, 0));
        level.addFreshEntity(missile);
        if (target instanceof FlyingWeaponEntity f) f.engagedUntil = level.getGameTime() + (highValueOnly() ? 160 : 60);
        cooldown = missileCooldown();
        if (!byHand) setTarget(null);
        com.steelrain.util.Sfx.gunfire(level, muzzle, "missile_start", "igla_9k38_far", "rpg_veryfar");
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 20, 0.3, 0.3, 0.3, 0.06);
    }

    @Nullable
    private Entity findTarget(ServerLevel level, Vec3 centre, double range, boolean gunsOnly, long now) {
        Entity best = null;
        double bestDist = range * range;
        for (Entity e : level.getEntities(this, new AABB(centre, centre).inflate(range), this::isThreat)) {
            double d = e.distanceToSqr(centre);
            if (d >= bestDist) continue;
            if (hasCeiling() && e.getY() - getY() > Config.AIR_DEFENCE_CEILING.get()) continue;
            if (!gunsOnly && e instanceof FlyingWeaponEntity f && f.engagedUntil >= now) continue;
            best = e;
            bestDist = d;
        }
        return best;
    }

    /** Hostile things in the air: Steel Rain drones and missiles, and (with Superb Warfare) its aircraft and guided weapons. */
    public boolean isThreat(Entity e) {
        if (!e.isAlive()) return false;
        UUID owner = getOwnerUUID();
        if (e instanceof FlyingWeaponEntity f) {
            return !f.falling() && f.interceptable() && (!highValueOnly() || f.highValue())
                    && !Teams.friendly(level(), owner, ownerNameCache, f.ownerId(), f.ownerName());
        }
        return com.steelrain.compat.Sbw.isAirThreat(level(), e, highValueOnly(), owner, ownerNameCache);
    }

    /** A system nobody can sit in (the Patriot) is switched on and off by right-clicking it. */
    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        if (seats().length == 0 && player.getItemInHand(hand).isEmpty() && !player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer sp) {
                if (getOwnerUUID() == null || getOwnerUUID().equals(sp.getUUID()) || sp.getAbilities().instabuild) {
                    if (getOwnerUUID() == null) setOwner(sp.getUUID());
                    entityData.set(AUTO, !auto());
                    sp.displayClientMessage(Component.translatable(auto() ? "message.steelrain.auto_on" : "message.steelrain.auto_off"), true);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AUTO, tag.getBoolean("Auto"));
        entityData.set(ROUNDS, tag.getInt("Rounds"));
        entityData.set(BELT_AP, tag.getBoolean("BeltAP"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Auto", auto());
        tag.putInt("Rounds", rounds());
        tag.putBoolean("BeltAP", beltAp());
    }
}
