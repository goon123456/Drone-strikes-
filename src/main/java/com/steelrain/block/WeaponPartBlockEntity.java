package com.steelrain.block;

import com.steelrain.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Remembers which core a filler block belongs to. */
public class WeaponPartBlockEntity extends BlockEntity {
    @Nullable private BlockPos core;

    public WeaponPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WEAPON_PART_BE.get(), pos, state);
    }

    @Nullable public BlockPos core() { return core; }

    public void setCore(BlockPos core) {
        this.core = core.immutable();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (core != null) tag.put("Core", NbtUtils.writeBlockPos(core));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core = NbtUtils.readBlockPos(tag, "Core").orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
