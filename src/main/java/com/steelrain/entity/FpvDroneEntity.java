package com.steelrain.entity;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.item.FiberBackpackItem;
import com.steelrain.net.Payloads;
import com.steelrain.util.Blast;
import com.steelrain.util.FeedManager;
import com.steelrain.util.Gear;
import com.steelrain.util.Recon;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Hand-flown quadcopters: the radio FPV kamikaze, the fibre-optic FPV kamikaze, and the reusable Mavic.
 * The pilot rides along inside the drone (see FeedManager) while a stand-in body stays where they stood.
 */
public class FpvDroneEntity extends FlyingWeaponEntity {
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GRENADE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SIGNAL = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DISTANCE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CABLE_LEFT = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);

    /** Fibre drones with a cable out, so explosions can find and cut them. */
    private static final Set<FpvDroneEntity> WIRED = Collections.newSetFromMap(new WeakHashMap<>());

    public enum Kind { FPV, MAVIC, FIBER }

    private final Kind kind;
    private final double maxSpeed;

    @Nullable private UUID pilot;
    private Vec3 pilotBase = Vec3.ZERO;
    private float inForward, inStrafe, inVertical;
    private int sinceInput;
    private int flightAge;
    private boolean armed;

    // fibre-optic cable: where the pilot stands, then every corner it has been dragged round
    private final List<Vec3> cable = new ArrayList<>();
    private double cableMax, cableOut;
    private Vec3 lastClear = Vec3.ZERO;

    public FpvDroneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.health = 2f;
        String path = EntityType.getKey(type).getPath();
        this.kind = path.equals("mavic") ? Kind.MAVIC : path.equals("fiber_fpv_drone") ? Kind.FIBER : Kind.FPV;
        this.maxSpeed = kind == Kind.MAVIC ? 0.6 : kind == Kind.FIBER ? 0.85 : 0.95;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BATTERY, 20 * 150);
        builder.define(ACTIVE, false);
        builder.define(GRENADE, false);
        builder.define(SIGNAL, 100);
        builder.define(DISTANCE, 0);
        builder.define(CABLE_LEFT, -1);
    }

    public Kind kind() { return kind; }
    public boolean mavic() { return kind == Kind.MAVIC; }
    public boolean fiber() { return kind == Kind.FIBER; }
    public boolean hasGrenade() { return entityData.get(GRENADE); }
    public int battery() { return entityData.get(BATTERY); }
    public void setBattery(int ticks) { entityData.set(BATTERY, Math.max(0, ticks)); }
    public boolean active() { return entityData.get(ACTIVE); }
    /** Link quality 0-100 as the pilot sees it. */
    public int signal() { return entityData.get(SIGNAL); }
    /** Blocks from the pilot's body. */
    public int distance() { return entityData.get(DISTANCE); }
    /** Blocks of cable still on the spool, or -1 for radio drones. */
    public int cableLeft() { return entityData.get(CABLE_LEFT); }
    public int fullBattery() { return (mavic() ? Config.MAVIC_BATTERY_SECONDS.get() : Config.FPV_BATTERY_SECONDS.get()) * 20; }

    @Override protected double modelRadius() { return 1; }

    /** A parked drone is not a target; one in the air is. */
    @Override public boolean interceptable() { return active() || !onGround(); }

    /** Radio drones can be jammed. A fibre-optic one has no radio link at all. */
    @Override protected boolean radioLinked() { return !fiber() && pilot != null; }

    // ---- piloting ----

    public boolean startPiloting(ServerPlayer player) {
        if (pilot != null) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_busy"), true);
            return false;
        }
        if (battery() <= 0) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_no_battery"), true);
            return false;
        }
        double reach = Config.FPV_RANGE.get();
        if (fiber()) {
            ItemStack pack = Gear.find(player, ModRegistry.FIBER_BACKPACK.get());
            if (pack.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.steelrain.no_spool"), true);
                return false;
            }
            reach = FiberBackpackItem.cable(pack);
            if (reach < 8) {
                player.displayClientMessage(Component.translatable("message.steelrain.spool_empty"), true);
                return false;
            }
        }
        if (player.distanceToSqr(this) > Math.min(reach, 48) * Math.min(reach, 48)) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_out_of_range"), true);
            return false;
        }
        Vec3 base = player.position().add(0, 1.2, 0);
        Component problem = FeedManager.attach(player, this);
        if (problem != null) {
            player.displayClientMessage(problem, true);
            return false;
        }
        pilot = player.getUUID();
        pilotBase = base;
        setOwner(player.getUUID(), player.getGameProfile().getName());
        sinceInput = 0;
        inForward = inStrafe = inVertical = 0;
        entityData.set(ACTIVE, true);
        if (fiber()) {
            cable.clear();
            cable.add(base);
            cableMax = reach;
            cableOut = 0;
            lastClear = position();
            WIRED.add(this);
            syncCable();
        }
        PacketDistributor.sendToPlayer(player, new Payloads.Pilot(getId(), true, fullBattery()));
        return true;
    }

    public void releasePilot(@Nullable String messageKey) {
        if (pilot == null) return;
        UUID was = pilot;
        pilot = null;
        entityData.set(ACTIVE, false);
        ServerPlayer player = getServer() == null ? null : getServer().getPlayerList().getPlayer(was);
        if (fiber()) {
            // Cable that was paid out stays on the ground: it is gone from the spool.
            if (player != null) {
                ItemStack pack = Gear.find(player, ModRegistry.FIBER_BACKPACK.get());
                if (!pack.isEmpty()) FiberBackpackItem.spend(pack, Mth.ceil(cableOut));
            }
            cable.clear();
            WIRED.remove(this);
            syncCable();
            entityData.set(CABLE_LEFT, -1);
        }
        if (player != null) {
            FeedManager.detach(player);
            PacketDistributor.sendToPlayer(player, new Payloads.Pilot(getId(), false, 0));
            if (messageKey != null) player.displayClientMessage(Component.translatable(messageKey), false);
        }
    }

    public void control(ServerPlayer player, float forward, float strafe, float vertical, float yaw, float pitch, boolean detonate) {
        if (pilot == null || !pilot.equals(player.getUUID())) return;
        sinceInput = 0;
        inForward = Mth.clamp(forward, -1, 1);
        inStrafe = Mth.clamp(strafe, -1, 1);
        inVertical = Mth.clamp(vertical, -1, 1);
        // A jammed link is sluggish: the drone only follows part of each steering command.
        float follow = 1 - jam * 0.85f;
        setRot(Mth.rotLerp(follow, getYRot(), yaw), Mth.lerp(follow, getXRot(), Mth.clamp(pitch, -90, 90)));
        if (detonate && jam < 0.9f) {
            if (mavic()) dropGrenade();
            else if (armed) explode();
        }
    }

    private void dropGrenade() {
        if (!hasGrenade() || !(level() instanceof ServerLevel sl)) return;
        entityData.set(GRENADE, false);
        MissileEntity.drop(sl, MissileProfile.GRENADE, position().add(0, -0.3, 0), getDeltaMovement().scale(0.5), ownerId, ownerName);
        com.steelrain.util.Sfx.play(sl, position(), "bomb_release", 1f, 1.3f);
    }

    public boolean isPilot(Player player) { return pilot != null && pilot.equals(player.getUUID()); }

    @Override
    protected void serverTick(ServerLevel level) {
        if (pilot != null) {
            sinceInput++;
            double dist = position().distanceTo(pilotBase);
            entityData.set(DISTANCE, (int) dist);
            if (fiber()) {
                if (!cableTick(level)) return;
                entityData.set(SIGNAL, 100);
            } else {
                double range = Config.FPV_RANGE.get();
                int quality = (int) Math.round(100 * Mth.clamp(1 - dist / range, 0, 1) * (1 - jam));
                entityData.set(SIGNAL, quality);
                if (jam >= 0.98f) releasePilot("message.steelrain.fpv_jammed");
                else if (dist > range) releasePilot("message.steelrain.fpv_signal_lost");
            }
            if (pilot != null && sinceInput > 60) releasePilot("message.steelrain.fpv_signal_lost");
            if (pilot != null && battery() <= 0) releasePilot("message.steelrain.fpv_no_battery");
        }

        Vec3 vel = getDeltaMovement();
        boolean powered = pilot != null && battery() > 0;
        if (powered) {
            setBattery(battery() - 1);
            if (++flightAge > 30) armed = true;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            // The FPV flies where its fixed camera points; the Mavic moves level while its gimbal looks around.
            Vec3 ahead = mavic() ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)) : look;
            double authority = 1 - jam * 0.7;
            Vec3 want = ahead.scale(inForward * maxSpeed * authority)
                    .add(left.scale(inStrafe * maxSpeed * 0.6 * authority))
                    .add(0, inVertical * 0.5 * authority, 0);
            vel = vel.add(want.subtract(vel).scale(0.12));
            if (fiber()) vel = holdCable(vel);
            keepChunksLoaded(level, position().add(vel), vel);
            if (mavic()) Recon.scan(level, this, 32, false, jam);
        } else if (onGround()) {
            vel = new Vec3(vel.x * 0.5, 0, vel.z * 0.5);
        } else if (mavic() && battery() > 0) {
            // Lost its pilot: hover and come down gently instead of dropping out of the sky.
            setBattery(battery() - 1);
            vel = new Vec3(vel.x * 0.8, -0.12, vel.z * 0.8);
        } else {
            vel = vel.scale(0.98).add(0, -0.05, 0);
        }

        double speed = vel.length();
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);

        if (!mavic() && armed && speed > 0.45 && (horizontalCollision || verticalCollision)) {
            explode();
            return;
        }
        if (!mavic() && armed && speed > 0.3) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.35))) {
                if (e.isAlive() && !e.getUUID().equals(pilot) && !e.isSpectator()) {
                    explode();
                    return;
                }
            }
        }
        if (getY() < level.getMinBuildHeight() - 32) {
            releasePilot(null);
            discard();
        }
    }

    // ---- fibre-optic cable ----

    private boolean blocked(Vec3 a, Vec3 b) {
        return level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    private double cableLength() {
        double len = 0;
        for (int i = 1; i < cable.size(); i++) len += cable.get(i - 1).distanceTo(cable.get(i));
        return len + cable.get(cable.size() - 1).distanceTo(position());
    }

    /** Pays the cable out behind the drone, wrapping it round corners. @return false if it snapped. */
    private boolean cableTick(ServerLevel level) {
        Vec3 me = position().add(0, 0.1, 0);
        if (tickCount % 2 == 0) {
            Vec3 last = cable.get(cable.size() - 1);
            if (blocked(last, me)) {
                // Flew round something: the cable now bends at the last spot the drone could be seen from.
                cable.add(lastClear);
                if (cable.size() > 96) {
                    snap("message.steelrain.cable_snagged");
                    return false;
                }
                syncCable();
            } else {
                lastClear = me;
                if (cable.size() >= 2 && !blocked(cable.get(cable.size() - 2), me)) {
                    cable.remove(cable.size() - 1); // came back round the corner: the slack is taken up
                    syncCable();
                }
            }
        }
        double len = cableLength();
        cableOut = Math.max(cableOut, len);
        entityData.set(CABLE_LEFT, (int) Math.max(0, cableMax - len));
        if (len > cableMax + 6) {
            snap("message.steelrain.cable_snapped");
            return false;
        }
        if (tickCount % 40 == 0) syncCable();
        return true;
    }

    /** At the end of the spool the drone simply cannot move any further away. */
    private Vec3 holdCable(Vec3 vel) {
        if (cable.isEmpty() || cableLength() < cableMax) return vel;
        Vec3 away = position().subtract(cable.get(cable.size() - 1)).normalize();
        double out = vel.dot(away);
        return out > 0 ? vel.subtract(away.scale(out)) : vel;
    }

    private void snap(String messageKey) {
        cableOut = Math.max(cableOut, Math.min(cableMax, cableLength()));
        level().playSound(null, blockPosition(), ModRegistry.CABLE_SNAP.get(), SoundSource.NEUTRAL, 1.5f, 1f);
        releasePilot(messageKey);
    }

    private void syncCable() {
        if (!(level() instanceof ServerLevel sl)) return;
        Payloads.Cable packet = new Payloads.Cable(getId(), new ArrayList<>(cable));
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 320 * 320 || p.position().distanceToSqr(pilotBase) < 320 * 320) {
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }

    /** An explosion close to any stretch of cable cuts it. */
    public static void explosionAt(Level level, Vec3 centre, double radius) {
        for (FpvDroneEntity d : new ArrayList<>(WIRED)) {
            if (d.level() != level || d.isRemoved() || d.pilot == null || d.cable.isEmpty()) continue;
            List<Vec3> pts = new ArrayList<>(d.cable);
            pts.add(d.position());
            for (int i = 1; i < pts.size(); i++) {
                if (distanceToSegment(centre, pts.get(i - 1), pts.get(i)) <= radius) {
                    d.snap("message.steelrain.cable_cut");
                    break;
                }
            }
        }
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1e-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // ---- end of the line ----

    private void explode() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        Player owner = ownerPlayer();
        Vec3 at = position();
        releasePilot(null);
        discard();
        Blast.detonate(level, at, 3.5f, 1, 30f, 6, false, this, owner);
    }

    /** An attack drone's charge goes off at half strength; a Mavic only if it still has its grenade. */
    @Override
    protected void crashBlast(ServerLevel level, Vec3 at) {
        if (mavic() && !hasGrenade()) super.crashBlast(level, at);
        else Blast.detonate(level, at, mavic() ? 1.5f : 1.8f, 0, mavic() ? 8f : 15f, mavic() ? 3 : 4, false, this, ownerPlayer());
    }

    @Override
    public void shotDown() {
        releasePilot("message.steelrain.fpv_destroyed");
        super.shotDown();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && pilot != null) releasePilot(null);
        super.remove(reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModRegistry.FPV_CONTROLLER.get())) {
            if (!level().isClientSide) {
                stack.set(ModRegistry.LINKED_DRONE.get(), getUUID());
                player.displayClientMessage(Component.translatable("message.steelrain.fpv_linked"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (mavic() && stack.is(ModRegistry.DRONE_GRENADE.get()) && !hasGrenade()) {
            if (!level().isClientSide) {
                entityData.set(GRENADE, true);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.steelrain.grenade_loaded"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isShiftKeyDown() && pilot == null) {
            if (!level().isClientSide) {
                ItemStack drop = new ItemStack(switch (kind) {
                    case MAVIC -> ModRegistry.MAVIC.get();
                    case FIBER -> ModRegistry.FIBER_FPV_DRONE.get();
                    default -> ModRegistry.FPV_DRONE.get();
                });
                if (mavic()) drop.set(ModRegistry.BATTERY.get(), battery());
                if (!player.getInventory().add(drop)) spawnAtLocation(drop);
                if (hasGrenade()) spawnAtLocation(new ItemStack(ModRegistry.DRONE_GRENADE.get()));
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Battery")) setBattery(tag.getInt("Battery"));
        armed = tag.getBoolean("Armed");
        entityData.set(GRENADE, tag.getBoolean("Grenade"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Battery", battery());
        tag.putBoolean("Armed", armed);
        tag.putBoolean("Grenade", hasGrenade());
    }
}
