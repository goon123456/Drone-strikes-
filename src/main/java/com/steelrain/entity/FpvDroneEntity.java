package com.steelrain.entity;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import com.steelrain.util.Blast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/** Small kamikaze quadcopter flown by a player through its camera. */
public class FpvDroneEntity extends FlyingWeaponEntity {
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double MAX_SPEED = 0.95;

    @Nullable private UUID pilot;
    private float inForward, inStrafe, inVertical;
    private int sinceInput;
    private int flightAge;
    private boolean armed;

    public FpvDroneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.health = 2f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BATTERY, 20 * 150);
        builder.define(ACTIVE, false);
    }

    public int battery() { return entityData.get(BATTERY); }
    public void setBattery(int ticks) { entityData.set(BATTERY, Math.max(0, ticks)); }
    public boolean active() { return entityData.get(ACTIVE); }
    public static int maxBattery() { return Config.FPV_BATTERY_SECONDS.get() * 20; }

    /** A parked drone is not a target; one in the air is. */
    @Override public boolean interceptable() { return active() || !onGround(); }

    @Override public boolean jammable() { return pilot != null; }

    @Override
    public void jam() {
        releasePilot("message.steelrain.fpv_jammed");
    }

    // ---- piloting ----

    public boolean startPiloting(ServerPlayer player) {
        if (pilot != null && !pilot.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_busy"), true);
            return false;
        }
        if (battery() <= 0) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_no_battery"), true);
            return false;
        }
        double range = Config.FPV_RANGE.get();
        if (player.distanceToSqr(this) > range * range) {
            player.displayClientMessage(Component.translatable("message.steelrain.fpv_out_of_range"), true);
            return false;
        }
        pilot = player.getUUID();
        setOwner(player.getUUID(), player.getGameProfile().getName());
        sinceInput = 0;
        inForward = inStrafe = inVertical = 0;
        entityData.set(ACTIVE, true);
        PacketDistributor.sendToPlayer(player, new Payloads.Pilot(getId(), true, Config.FPV_RANGE.get(), maxBattery()));
        return true;
    }

    public void releasePilot(@Nullable String messageKey) {
        if (pilot == null) return;
        if (level().getPlayerByUUID(pilot) instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Payloads.Pilot(getId(), false, 0, 0));
            if (messageKey != null) sp.displayClientMessage(Component.translatable(messageKey), true);
        }
        pilot = null;
        entityData.set(ACTIVE, false);
    }

    public void control(ServerPlayer player, float forward, float strafe, float vertical, float yaw, float pitch, boolean detonate) {
        if (pilot == null || !pilot.equals(player.getUUID())) return;
        sinceInput = 0;
        inForward = Mth.clamp(forward, -1, 1);
        inStrafe = Mth.clamp(strafe, -1, 1);
        inVertical = Mth.clamp(vertical, -1, 1);
        setRot(yaw, Mth.clamp(pitch, -90, 90));
        if (detonate && armed) explode();
    }

    public boolean isPilot(Player player) { return pilot != null && pilot.equals(player.getUUID()); }

    @Override
    protected void serverTick(ServerLevel level) {
        if (pilot != null) {
            sinceInput++;
            Player p = level.getPlayerByUUID(pilot);
            double range = Config.FPV_RANGE.get();
            if (p == null || !p.isAlive()) releasePilot(null);
            else if (p.distanceToSqr(this) > range * range || sinceInput > 40) releasePilot("message.steelrain.fpv_signal_lost");
            else if (battery() <= 0) releasePilot("message.steelrain.fpv_no_battery");
        }

        Vec3 vel = getDeltaMovement();
        boolean powered = pilot != null && battery() > 0;
        if (powered) {
            setBattery(battery() - 1);
            if (++flightAge > 30) armed = true;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vec3 want = look.scale(inForward * MAX_SPEED)
                    .add(left.scale(inStrafe * MAX_SPEED * 0.6))
                    .add(0, inVertical * 0.5, 0);
            vel = vel.add(want.subtract(vel).scale(0.12));
            keepChunksLoaded(level, position().add(vel), vel);
        } else if (onGround()) {
            vel = new Vec3(vel.x * 0.5, 0, vel.z * 0.5);
        } else {
            vel = vel.scale(0.98).add(0, -0.05, 0);
        }

        double speed = vel.length();
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);

        if (armed && speed > 0.45 && (horizontalCollision || verticalCollision)) {
            explode();
            return;
        }
        if (armed && speed > 0.3) {
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

    private void explode() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        Player owner = ownerPlayer();
        releasePilot(null);
        discard();
        Blast.detonate(level, position(), 3.5f, 1, 30f, 6, false, this, owner);
    }

    @Override
    public void shotDown() {
        releasePilot("message.steelrain.fpv_destroyed");
        super.shotDown();
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
        if (player.isShiftKeyDown() && pilot == null) {
            if (!level().isClientSide) {
                ItemStack drop = new ItemStack(ModRegistry.FPV_DRONE.get());
                if (!player.getInventory().add(drop)) spawnAtLocation(drop);
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Battery", battery());
        tag.putBoolean("Armed", armed);
    }
}
