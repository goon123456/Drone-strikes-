package com.steelrain.entity;

import com.steelrain.block.WeaponBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Invisible seat a player rides to man a gun. Disappears when they get off or the gun is gone. */
public class SeatEntity extends Entity {
    private BlockPos gun = BlockPos.ZERO;

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setGun(BlockPos pos) { gun = pos.immutable(); }
    public BlockPos gun() { return gun; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getPassengers().isEmpty() || !(level().getBlockEntity(gun) instanceof WeaponBlockEntity)) discard();
    }

    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
