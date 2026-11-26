package com.steelrain.entity.vehicle;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.util.Jamming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The three jammers: the EW Jammer shelter and the Krasukha-4 truck run their own diesel power units; the small
 * Trench Jammer runs on its battery, topped up with Drone Batteries. While switched on (sneak + right-click with an
 * empty hand) and powered, each jams every radio and satellite link in its bubble except its owner's team's.
 */
public class Jammer extends SrVehicle {
    public enum Kind { JAMMER, TRENCH_JAMMER, KRASUKHA }

    private static final EntityDataAccessor<Boolean> ON = SynchedEntityData.defineId(Jammer.class, EntityDataSerializers.BOOLEAN);
    private static final Vec3[] KRASUKHA_SEATS = {new Vec3(0.55, 0.9, 4.5), new Vec3(-0.55, 0.9, 4.5)};
    /** FE a Drone Battery gives the Trench Jammer. */
    private static final int BATTERY_FE = 40_000;

    private final Kind kind;
    @Nullable private String ownerNameCache;

    public Jammer(EntityType<?> type, Level level) {
        super(type, level);
        this.kind = switch (EntityType.getKey(type).getPath()) {
            case "trench_jammer" -> Kind.TRENCH_JAMMER;
            case "krasukha" -> Kind.KRASUKHA;
            default -> Kind.JAMMER;
        };
    }

    public Kind kind() { return kind; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ON, true);
    }

    public boolean switchedOn() { return entityData.get(ON); }

    @Override public float maxHealth() { return switch (kind) { case TRENCH_JAMMER -> 30; case KRASUKHA -> 300; default -> 150; }; }
    @Override public float tankLitres() { return switch (kind) { case TRENCH_JAMMER -> 0; case KRASUKHA -> 200; default -> 60; }; }
    @Override public int batteryFe() { return switch (kind) { case TRENCH_JAMMER -> 100_000; case KRASUKHA -> 800_000; default -> 200_000; }; }
    @Override protected int alternatorFe() { return kind == Kind.KRASUKHA ? 400 : 200; }
    @Override protected boolean hasPowerUnit() { return kind != Kind.TRENCH_JAMMER; }
    @Override protected Vec3[] seats() { return kind == Kind.KRASUKHA ? KRASUKHA_SEATS : new Vec3[0]; }
    @Override public boolean drivable() { return kind == Kind.KRASUKHA; }
    @Override protected double wheelbase() { return 6.2; }
    @Override public double wheelRadius() { return 0.6; }
    @Override protected double length() { return kind == Kind.KRASUKHA ? 11.0 : kind == Kind.TRENCH_JAMMER ? 0.8 : 3.0; }
    @Override protected double width() { return kind == Kind.KRASUKHA ? 2.6 : kind == Kind.TRENCH_JAMMER ? 0.8 : 2.6; }
    @Override public String modelName() { return switch (kind) { case TRENCH_JAMMER -> "trench_jammer"; case KRASUKHA -> "krasukha"; default -> "jammer"; }; }

    @Override
    protected ItemStack asItem() {
        return new ItemStack(switch (kind) {
            case TRENCH_JAMMER -> ModRegistry.TRENCH_JAMMER_ITEM.get();
            case KRASUKHA -> ModRegistry.KRASUKHA_ITEM.get();
            default -> ModRegistry.JAMMER_ITEM.get();
        });
    }

    private double jamRadius() {
        return switch (kind) {
            case TRENCH_JAMMER -> Config.TRENCH_JAMMER_RANGE.get();
            case KRASUKHA -> Config.STRATEGIC_JAMMER_RANGE.get();
            default -> Config.JAMMER_RANGE.get();
        };
    }

    /** FE drawn every half second while switched on. */
    private int jamEnergy() { return kind == Kind.KRASUKHA ? 1250 : 250; }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (getOwnerUUID() != null && level.getPlayerByUUID(getOwnerUUID()) instanceof Player p) ownerNameCache = p.getGameProfile().getName();
        if (!switchedOn() || tickCount % 10 != 0) return;
        if (Config.REQUIRE_ENERGY.get() && !useEnergy(jamEnergy())) return;
        Jamming.emit(level, getUUID(), position().add(0, getBbHeight(), 0), jamRadius(), getOwnerUUID(), ownerNameCache);
        if (tickCount % 160 == 0) {   // the hum recording is eight seconds long
            level.playSound(null, getX(), getY() + 1, getZ(), ModRegistry.JAMMER_HUM.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 1f);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) player.displayClientMessage(Component.translatable("message.steelrain.not_linkable"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.is(ModRegistry.DRONE_BATTERY.get()) && tankLitres() <= 0) {
            if (!level().isClientSide) {
                if (energy() >= batteryFe()) {
                    player.displayClientMessage(Component.translatable("message.steelrain.battery_full"), true);
                } else {
                    setEnergy(energy() + BATTERY_FE);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.displayClientMessage(Component.translatable("message.steelrain.battery_charged", energy() / 1000, batteryFe() / 1000), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level().isClientSide) {
                if (getOwnerUUID() != null && !getOwnerUUID().equals(player.getUUID()) && !player.getAbilities().instabuild) return InteractionResult.FAIL;
                if (getOwnerUUID() == null) setOwner(player.getUUID());
                entityData.set(ON, !switchedOn());
                player.displayClientMessage(Component.translatable(switchedOn() ? "message.steelrain.jammer_on" : "message.steelrain.jammer_off"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (seats().length == 0 && !level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.steelrain.vehicle_status",
                    Math.round(fuel()), Math.round(tankLitres()), energy() / 1000, batteryFe() / 1000), true);
        }
        return super.interact(player, hand);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ON, !tag.contains("On") || tag.getBoolean("On"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("On", switchedOn());
    }
}
