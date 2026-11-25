package com.steelrain.entity;

import com.steelrain.util.FeedManager;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * What stays behind while a player watches a remote drone feed: a motionless copy with their name, skin and
 * armour. Hitting it pulls the player back into their body and passes the damage on to them.
 */
public class OperatorBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OPERATOR =
            SynchedEntityData.defineId(OperatorBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final NonNullList<ItemStack> armour = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);

    public OperatorBodyEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OPERATOR, Optional.empty());
    }

    public Optional<UUID> operator() { return entityData.get(OPERATOR); }

    /** Dresses the stand-in like the player. The items are copies for show and never drop. */
    public void copyFrom(ServerPlayer player) {
        entityData.set(OPERATOR, Optional.of(player.getUUID()));
        setCustomName(Component.literal(player.getGameProfile().getName()));
        setCustomNameVisible(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
                setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }
        moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        setYHeadRot(player.getYHeadRot());
        setYBodyRot(player.yBodyRot);
        setHealth(Math.min(getMaxHealth(), player.getHealth()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && !FeedManager.ownsBody(this)) discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        FeedManager.bodyHurt(this, source, amount);
        return true;
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public void knockback(double strength, double x, double z) {}
    @Override protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {}
    @Override public boolean shouldShowName() { return true; }

    @Override public Iterable<ItemStack> getArmorSlots() { return armour; }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armour.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.getType()) {
            case HAND -> hands.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> armour.set(slot.getIndex(), stack);
            default -> { }
        }
    }

    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
}
