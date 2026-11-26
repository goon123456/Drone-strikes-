package com.steelrain.entity.vehicle;

import com.steelrain.ModRegistry;
import com.steelrain.util.Blast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Steel Rain's own vehicles. Drives like a real vehicle: wheeled ones steer with their front wheels (the tighter
 * the faster they turn), tracked ones turn on the spot; both climb a block, lean on slopes and stop against walls.
 * The driver's own game runs the driving and the server follows it, the same way vanilla boats work, so it feels
 * immediate even on a slow connection.
 *
 * <p>Engines burn diesel from a tank, filled with jerrycans. Electronics (radars, launch control, jammers) run on
 * a battery that the engine's alternator charges while it runs, and that any mod's FE cables or chargers can fill.
 * Turret and gun angles are sent to every player so everyone sees them move.
 */
public abstract class SrVehicle extends Entity implements OwnableEntity {
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(SrVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(SrVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(SrVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(SrVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUN_PITCH = SynchedEntityData.defineId(SrVehicle.class, EntityDataSerializers.FLOAT);

    /** Litres of diesel one jerrycan holds. */
    public static final float JERRYCAN = 20f;

    @Nullable private UUID owner;
    protected final SimpleContainer inventory = new SimpleContainer(27);

    // driving, run by whoever controls the vehicle (the driver's game, or the server when nobody drives)
    protected double speed;          // blocks per tick along the way the vehicle faces; negative is reverse
    protected double steer;          // front-wheel angle in radians, positive is left
    private float inForward, inLeft;
    private boolean inBrake;
    private double fallSpeed;

    // remote players' view of a vehicle someone else drives: eased towards each update from the server
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    // server: fuel use and alternator
    private Vec3 lastPos;
    private double distanceSinceFuel;

    // client: what the renderer reads; each has a previous value for smooth frames
    public float turretYawO, gunPitchO, tiltPitch, tiltPitchO, tiltRoll, tiltRollO, wheelSpin, wheelSpinO, steerShown, steerShownO;
    public float leanPitch, leanPitchO, leanRoll, leanRollO;
    private double lastSpeedSeen;
    private float lastYawSeen;
    private boolean soundStarted;

    protected SrVehicle(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ------------------------------------------------------------------ what each vehicle defines

    public abstract float maxHealth();

    public abstract float tankLitres();

    public abstract int batteryFe();

    /** Seats in vehicle space (x left, y up, z forward, from the ground under the middle). Seat 0 drives. */
    protected abstract Vec3[] seats();

    /** Tracked vehicles turn on the spot by running their tracks against each other. */
    public boolean tracked() { return false; }

    /** Distance between the front axle and the middle of the rear ones, for steering. */
    protected double wheelbase() { return 4.0; }

    protected double topSpeed() { return 0.7; }

    protected double reverseSpeed() { return 0.25; }

    protected double acceleration() { return 0.012; }

    /** Radius of the road wheels, for turning them by the distance driven. */
    public double wheelRadius() { return 0.5; }

    /** Length and width of the footprint, for leaning with the ground. */
    protected double length() { return 7.0; }

    protected double width() { return 2.4; }

    /** Whether the crew in this seat are hidden inside the vehicle. */
    public boolean hidesSeat(int seat) { return true; }

    /** What a packed vehicle remembers: damage, fuel, charge, launcher state and anything fitted (a dome). */
    public CompoundTag packState() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        tag.remove("Inventory");          // the load is dropped on the ground when it is packed
        tag.remove("Owner");
        tag.put("NeoForgeData", getPersistentData().copy());
        return tag;
    }

    public void unpackState(CompoundTag tag) {
        CompoundTag t = tag.copy();
        if (!t.contains("Inventory")) t.put("Inventory", new net.minecraft.nbt.ListTag());
        java.util.UUID placedBy = owner;  // whoever puts it down owns it now
        readAdditionalSaveData(t);
        owner = placedBy;
        if (t.contains("NeoForgeData")) getPersistentData().merge(t.getCompound("NeoForgeData"));
    }

    /** The item this vehicle packs back into. */
    protected abstract ItemStack asItem();

    /** Name of the mesh model and texture in assets/steelrain/models/vehicle and textures/entity/vehicle. */
    public abstract String modelName();

    /** Litres per block driven and per tick idling, and FE the alternator makes per tick while the engine runs. */
    protected double litresPerBlock() { return 1.0 / 60; }

    protected double litresPerIdleTick() { return 1.0 / 2400; }

    protected int alternatorFe() { return 200; }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HEALTH, 100f);
        b.define(FUEL, 0f);
        b.define(ENERGY, 0);
        b.define(TURRET_YAW, 0f);
        b.define(GUN_PITCH, 0f);
    }

    public float health() { return entityData.get(HEALTH); }
    protected void setHealth(float h) { entityData.set(HEALTH, h); }
    public float fuel() { return entityData.get(FUEL); }
    public void setFuel(float litres) { entityData.set(FUEL, Mth.clamp(litres, 0, tankLitres())); }
    public int energy() { return entityData.get(ENERGY); }
    public void setEnergy(int fe) { entityData.set(ENERGY, Mth.clamp(fe, 0, batteryFe())); }

    /** Turret bearing relative to the hull, degrees, positive turns to the right like the player's own yaw. */
    public float turretYaw() { return entityData.get(TURRET_YAW); }
    public void setTurretYaw(float deg) { entityData.set(TURRET_YAW, Mth.wrapDegrees(deg)); }
    /** Gun or launcher elevation in degrees, positive is up. */
    public float gunPitch() { return entityData.get(GUN_PITCH); }
    public void setGunPitch(float deg) { entityData.set(GUN_PITCH, deg); }

    public boolean useEnergy(int fe) {
        if (!com.steelrain.Config.REQUIRE_ENERGY.get() || fe <= 0) return true;
        if (energy() < fe) return false;
        setEnergy(energy() - fe);
        return true;
    }

    public SimpleContainer inventory() { return inventory; }

    /** The battery as any mod's energy cables and chargers see it: it takes power but never gives it out. */
    public final IEnergyStorage energyPort = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int took = Math.min(max, batteryFe() - energy());
            if (!simulate && took > 0) setEnergy(energy() + took);
            return Math.max(0, took);
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy(); }
        @Override public int getMaxEnergyStored() { return batteryFe(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    // ------------------------------------------------------------------ owner

    @Nullable @Override public UUID getOwnerUUID() { return owner; }
    public void setOwner(@Nullable UUID id) { owner = id; }

    @Nullable
    public String ownerName() {
        Player p = owner == null ? null : level().getPlayerByUUID(owner);
        return p == null ? null : p.getGameProfile().getName();
    }

    // ------------------------------------------------------------------ seats and riders

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().size() < seats().length; }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    public boolean engineRunning() { return getControllingPassenger() != null && fuel() > 0 && !flooded(); }

    /** Driven into water over the engine deck: the diesel drowns and stops until it is pulled out. */
    public boolean flooded() {
        return level().getFluidState(net.minecraft.core.BlockPos.containing(getX(), getY() + 1.3, getZ())).is(net.minecraft.tags.FluidTags.WATER);
    }

    public int seatOf(Entity passenger) { return getPassengers().indexOf(passenger); }

    /** A point in vehicle space turned into the world, following the hull's heading. */
    public Vec3 toWorld(Vec3 local) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double c = Mth.cos(yaw), s = Mth.sin(yaw);
        return new Vec3(getX() + local.x * c - local.z * s, getY() + local.y, getZ() + local.x * s + local.z * c);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        int seat = seatOf(passenger);
        if (seat < 0) return;
        Vec3 at = toWorld(seats()[seat]);
        move.accept(passenger, at.x, at.y, at.z);
        // turn the crew with the hull
        float turned = Mth.wrapDegrees(getYRot() - yRotO);
        passenger.setYRot(passenger.getYRot() + turned);
        passenger.setYHeadRot(passenger.getYHeadRot() + turned);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seat = Math.max(0, seatOf(passenger));
        Vec3 s = seats()[seat];
        double side = s.x >= 0 ? 1 : -1;
        return toWorld(new Vec3(side * (width() / 2 + 0.9), 0.2, s.z));
    }

    // ------------------------------------------------------------------ input from the driver's game

    public void setInput(float forward, float left, boolean brake) {
        inForward = forward;
        inLeft = left;
        inBrake = brake;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            drive();
        } else if (lerpSteps > 0) {
            double f = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
            setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) * (float) f);
            lerpSteps--;
        }
        if (level() instanceof ServerLevel sl) {
            serverTick(sl);
        } else {
            clientTick();
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x; lerpY = y; lerpZ = z; lerpYRot = yRot;
        lerpSteps = 3;
    }

    @Override public double lerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }
    @Override public double lerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }
    @Override public double lerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }
    @Override public float lerpTargetYRot() { return lerpSteps > 0 ? lerpYRot : getYRot(); }

    /** One tick of driving: engine, brakes, steering, gravity, then move and slide against blocks. */
    private void drive() {
        boolean driven = getControllingPassenger() != null;
        float forward = driven && fuel() > 0 && !flooded() ? inForward : 0;
        float left = driven ? inLeft : 0;
        boolean brake = !driven || inBrake;
        double top = topSpeed(), back = reverseSpeed();
        if (isInWater()) { top *= 0.3; back *= 0.3; }

        if (forward > 0) {
            speed = speed < -0.01 ? Math.min(0, speed + 0.05) : Math.min(top, speed + acceleration() * forward);
        } else if (forward < 0) {
            speed = speed > 0.01 ? Math.max(0, speed - 0.05) : Math.max(-back, speed + acceleration() * 0.7 * forward);
        } else {
            speed *= 0.985;                                   // rolling resistance
            speed = Math.abs(speed) < 0.004 ? 0 : speed - Math.signum(speed) * 0.003;
        }
        if (brake && forward == 0) speed = Math.abs(speed) < 0.04 ? 0 : speed - Math.signum(speed) * 0.04;
        if (!onGround()) speed *= 0.995;

        float yaw = getYRot();
        if (tracked()) {
            // skid steer: turns on the spot, and a little slower at speed
            yaw -= left * (float) (2.2 - Math.min(1.2, Math.abs(speed) * 1.5)) * (speed < -0.01 ? -1 : 1);
            steer = 0;
        } else {
            // the faster it goes, the less the wheels may be turned
            double want = left * 0.6 * (1 - Math.min(0.65, Math.abs(speed) / top * 0.7));
            steer += Mth.clamp(want - steer, -0.06, 0.06);
            yaw -= (float) Math.toDegrees(speed * Math.tan(steer) / wheelbase());
        }
        setYRot(yaw);

        fallSpeed = onGround() ? -0.04 : Math.max(-3.0, fallSpeed - 0.08);
        double rad = yaw * Mth.DEG_TO_RAD;
        Vec3 motion = new Vec3(-Math.sin(rad) * speed, fallSpeed, Math.cos(rad) * speed);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision) speed *= 0.4;
    }

    @Override
    public float maxUpStep() { return 1.05f; }

    protected void serverTick(ServerLevel level) {
        // diesel: a trickle while idling, more by the distance driven; the alternator charges the battery
        if (lastPos == null) lastPos = position();
        double moved = Math.sqrt(Mth.square(getX() - lastPos.x) + Mth.square(getZ() - lastPos.z));
        lastPos = position();
        if (engineRunning()) {
            distanceSinceFuel += moved;
            float burn = (float) (litresPerIdleTick() + distanceSinceFuel * litresPerBlock());
            distanceSinceFuel = 0;
            setFuel(fuel() - burn);
            setEnergy(energy() + alternatorFe());
            if (tickCount % 4 == 0 && moved > 0.05) {
                Vec3 exhaust = toWorld(new Vec3(width() / 2 - 0.2, 2.6, length() / 2 - 2.6));
                level.sendParticles(ParticleTypes.SMOKE, exhaust.x, exhaust.y, exhaust.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    /** What players see: leaning with the ground, wheels turning, the hull rocking on its springs. */
    protected void clientTick() {
        turretYawO = turretYaw();
        gunPitchO = gunPitch();
        tiltPitchO = tiltPitch; tiltRollO = tiltRoll;
        wheelSpinO = wheelSpin; steerShownO = steerShown;
        leanPitchO = leanPitch; leanRollO = leanRoll;

        double rad = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double along = (getX() - xo) * fx + (getZ() - zo) * fz;
        wheelSpin += (float) (along / wheelRadius());
        float turned = Mth.wrapDegrees(getYRot() - lastYawSeen);
        lastYawSeen = getYRot();
        if (!tracked()) {
            // from how fast it is turning for the distance covered, work out where the front wheels point
            double want = Math.abs(along) > 0.01 ? Mth.clamp(-Math.atan(Math.toRadians(turned) * wheelbase() / along), -0.6, 0.6) : steerShown;
            steerShown += (float) (want - steerShown) * 0.3f;
        }

        // lean with the ground under the corners
        double hf = groundAt(fx * length() * 0.42, fz * length() * 0.42), hb = groundAt(-fx * length() * 0.42, -fz * length() * 0.42);
        double hl = groundAt(fz * width() * 0.45, -fx * width() * 0.45), hr = groundAt(-fz * width() * 0.45, fx * width() * 0.45);
        float wantPitch = (float) Math.atan2(hb - hf, length() * 0.84), wantRoll = (float) Math.atan2(hl - hr, width() * 0.9);
        tiltPitch += (wantPitch - tiltPitch) * 0.3f;
        tiltRoll += (wantRoll - tiltRoll) * 0.3f;

        // squat when pulling away, dip when braking, roll out of a turn
        double accel = along - lastSpeedSeen;
        lastSpeedSeen = along;
        leanPitch += (Mth.clamp((float) accel * 1.6f, -0.05f, 0.05f) - leanPitch) * 0.25f;
        leanRoll += (Mth.clamp(turned * (float) along * 0.03f, -0.06f, 0.06f) - leanRoll) * 0.25f;

        if (!soundStarted) {
            soundStarted = true;
            com.steelrain.client.ClientHooks.startVehicleSound(this);
        }
    }

    /** Height of the ground at an offset from the middle, relative to the vehicle, for leaning. */
    private double groundAt(double dx, double dz) {
        Vec3 top = new Vec3(getX() + dx, getY() + 1.5, getZ() + dz);
        var hit = level().clip(new ClipContext(top, top.add(0, -3.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? -1.0 : hit.getLocation().y - getY();
    }

    public float leanPitch(float pt) { return Mth.lerp(pt, leanPitchO, leanPitch); }
    public float leanRoll(float pt) { return Mth.lerp(pt, leanRollO, leanRoll); }

    // ------------------------------------------------------------------ players using it

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModRegistry.DIESEL_JERRYCAN.get())) {
            if (!level().isClientSide) {
                if (fuel() >= tankLitres() - 0.5f) {
                    player.displayClientMessage(Component.translatable("message.steelrain.tank_full"), true);
                } else {
                    setFuel(fuel() + JERRYCAN);
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                        ItemStack empty = new ItemStack(ModRegistry.JERRYCAN.get());
                        if (!player.addItem(empty)) player.drop(empty, false);
                    }
                    level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1f, 0.7f);
                    player.displayClientMessage(Component.translatable("message.steelrain.refuelled", Math.round(fuel()), Math.round(tankLitres())), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, inventoryFor(p)), getDisplayName()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && canAddPassenger(player)) {
            player.startRiding(this);
            if (getControllingPassenger() == player) {
                player.displayClientMessage(Component.translatable("message.steelrain.vehicle_status",
                        Math.round(fuel()), Math.round(tankLitres()), energy() / 1000, batteryFe() / 1000), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** The inventory as a chest the player can use while standing next to the vehicle. */
    private SimpleContainer inventoryFor(Player player) {
        return new SimpleContainer(27) {
            { for (int i = 0; i < 27; i++) setItem(i, inventory.getItem(i)); }
            @Override public void setChanged() {
                for (int i = 0; i < 27; i++) inventory.setItem(i, getItem(i));
            }
            @Override public boolean stillValid(Player p) { return isAlive() && p.distanceToSqr(SrVehicle.this) < 8 * 8; }
        };
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256.0 * 256.0; }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        // The owner (or anyone in creative) packs it up by hitting it while sneaking, if nobody is aboard.
        if (source.getEntity() instanceof Player p && p.isShiftKeyDown() && getPassengers().isEmpty()
                && (p.getAbilities().instabuild || p.getUUID().equals(owner))) {
            Containers.dropContents(level(), blockPosition(), inventory);
            ItemStack item = asItem();
            item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(packState()));
            if (!p.addItem(item)) spawnAtLocation(item);
            discard();
            return true;
        }
        // Armour shrugs off small arms; explosions and heavy shells do the damage.
        float dealt = source.is(DamageTypeTags.IS_EXPLOSION) ? amount : Math.max(0, amount * 0.4f - 3f);
        if (dealt <= 0) return false;
        setHealth(health() - dealt);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.5, getZ(), 6, 0.6, 0.4, 0.6, 0.2);
            if (health() <= 0) destroyed(sl);
        }
        return true;
    }

    /** Burns out: the fuel goes up, the cargo spills and anything explosive aboard adds to the blast. */
    protected void destroyed(ServerLevel level) {
        Containers.dropContents(level, blockPosition(), inventory);
        ejectPassengers();
        discard();
        Blast.detonate(level, position().add(0, 1.2, 0), 3.5f + fuel() / 60f, 1, 30f, 6, true, this, null);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setHealth(tag.contains("Health") ? tag.getFloat("Health") : maxHealth());
        setFuel(tag.getFloat("Fuel"));
        setEnergy(tag.getInt("Energy"));
        setTurretYaw(tag.getFloat("TurretYaw"));
        setGunPitch(tag.getFloat("GunPitch"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", health());
        tag.putFloat("Fuel", fuel());
        tag.putInt("Energy", energy());
        tag.putFloat("TurretYaw", turretYaw());
        tag.putFloat("GunPitch", gunPitch());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Inventory", inventory.createTag(registryAccess()));
    }
}
