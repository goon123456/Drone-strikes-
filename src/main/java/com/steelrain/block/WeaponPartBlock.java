package com.steelrain.block;

import com.steelrain.util.Blast;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/** Invisible, solid block that fills out the body of a full-size weapon and forwards everything to its core. */
public class WeaponPartBlock extends Block implements EntityBlock {
    public WeaponPartBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    private static BlockPos core(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WeaponPartBlockEntity part && part.core() != null
                && level.getBlockState(part.core()).getBlock() instanceof WeaponBlock) {
            return part.core();
        }
        return null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1f; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeaponPartBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos core = core(level, pos);
        if (core == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return WeaponBlock.interact(level, core, player, stack).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos core = core(level, pos);
        return core == null ? InteractionResult.PASS : WeaponBlock.interact(level, core, player, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos core = core(level, pos);
        return core == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(core).getBlock());
    }

    /** Breaking any part breaks the whole thing. Creative players and explosions get no item back. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = core(level, pos);
        if (core != null && !level.isClientSide) {
            level.destroyBlock(core, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        BlockPos core = state.is(newState.getBlock()) ? null : core(level, pos);
        super.onRemove(state, level, pos, newState, moved);
        if (core != null && !level.isClientSide) {
            level.destroyBlock(core, !Blast.destroying());
        }
    }
}
