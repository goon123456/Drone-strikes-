package com.steelrain.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.item.LaptopItem;
import com.steelrain.util.Jamming;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.Launcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The three jammers as Superb Warfare vehicles.
 */
public class SupportVehicle extends VehicleEntity {
    public enum Kind { JAMMER, TRENCH_JAMMER, KRASUKHA }

    private final Kind kind;
    private boolean switchedOn = true;
    @Nullable private UUID ownerId;
    @Nullable private String ownerName;

    public SupportVehicle(EntityType<?> type, Level level) {
        super(type, level);
        this.kind = switch (EntityType.getKey(type).getPath()) {
            case "trench_jammer" -> Kind.TRENCH_JAMMER;
            case "krasukha" -> Kind.KRASUKHA;
            default -> Kind.JAMMER;
        };
    }

    public Kind kind() { return kind; }
    @Nullable public UUID ownerId() { return ownerId; }
    @Nullable public String ownerName() { return ownerName; }

    /** Unit vector the vehicle faces along, on the ground plane. */
    public Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // ---- jamming ----

    private double jamRadius() {
        return switch (kind) {
            case TRENCH_JAMMER -> Config.TRENCH_JAMMER_RANGE.get();
            case KRASUKHA -> Config.STRATEGIC_JAMMER_RANGE.get();
            default -> Config.JAMMER_RANGE.get();
        };
    }

    /** FE drawn every half second while switched on. */
    private int jamEnergy() { return kind == Kind.KRASUKHA ? 1250 : 250; }

    public boolean switchedOn() { return switchedOn; }

    @Override
    public void baseTick() {
        super.baseTick();
        if (!(level() instanceof ServerLevel level) || isWreck()) return;
        if (switchedOn && tickCount % 10 == 0) {
            if (Config.REQUIRE_ENERGY.get()) {
                if (getEnergy() < jamEnergy()) return;
                consumeEnergy(jamEnergy());
            }
            Jamming.emit(level, getUUID(), position().add(0, getBbHeight(), 0), jamRadius(), ownerId, ownerName);
            if (tickCount % 40 == 0) {
                level.playSound(null, getX(), getY() + 1, getZ(), ModRegistry.JAMMER_HUM.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 1f);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ownerId == null && !level().isClientSide) {
            // Whoever sets it up owns it: that decides whose drones a jammer leaves alone.
            ownerId = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
        if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.steelrain.not_linkable"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!level().isClientSide) {
                switchedOn = !switchedOn;
                ownerId = player.getUUID();
                ownerName = player.getGameProfile().getName();
                player.displayClientMessage(Component.translatable(switchedOn ? "message.steelrain.jammer_on" : "message.steelrain.jammer_off"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SrOn", switchedOn);
        if (ownerId != null) tag.putUUID("SrOwner", ownerId);
        if (ownerName != null) tag.putString("SrOwnerName", ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        switchedOn = !tag.contains("SrOn") || tag.getBoolean("SrOn");
        ownerId = tag.hasUUID("SrOwner") ? tag.getUUID("SrOwner") : null;
        ownerName = tag.contains("SrOwnerName") ? tag.getString("SrOwnerName") : null;
    }
}
