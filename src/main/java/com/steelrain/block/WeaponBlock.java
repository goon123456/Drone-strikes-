package com.steelrain.block;

import com.steelrain.ModRegistry;
import com.steelrain.item.LaptopItem;
import com.steelrain.util.Blast;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** Core block of a weapon system. Sits at the rear-centre-bottom of the footprint and holds all the state. */
public class WeaponBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final WeaponType type;

    public WeaponBlock(WeaponType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public WeaponType type() { return type; }

    /** Visits every block of the footprint except the core itself. */
    public static void forEachPart(BlockPos core, Direction facing, WeaponType type, Consumer<BlockPos> action) {
        Direction right = facing.getClockWise();
        int half = type.width / 2;
        for (int dz = 0; dz < type.length; dz++) {
            for (int dy = 0; dy < type.height; dy++) {
                for (int dx = -half; dx <= half; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    action.accept(core.relative(facing, dz).relative(right, dx).above(dy));
                }
            }
        }
    }

    /** First footprint position that is not free, or null if the whole vehicle fits. */
    @Nullable
    public static BlockPos obstruction(Level level, BlockPos core, Direction facing, WeaponType type) {
        BlockPos[] found = new BlockPos[1];
        forEachPart(core, facing, type, p -> {
            if (found[0] == null && (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced())) found[0] = p.immutable();
        });
        return found[0];
    }

    /** Fills the rest of the footprint around an already placed core. */
    public static void placeParts(Level level, BlockPos core, Direction facing, WeaponType type) {
        BlockState part = ModRegistry.WEAPON_PART.get().defaultBlockState();
        forEachPart(core, facing, type, p -> {
            level.setBlock(p, part, 3);
            if (level.getBlockEntity(p) instanceof WeaponPartBlockEntity be) be.setCore(core);
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    /** The whole vehicle is drawn by the block entity renderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeaponBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (beType != ModRegistry.WEAPON_BE.get()) return null;
        return level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<WeaponBlockEntity>) (l, p, s, be) -> be.clientTick()
                : (BlockEntityTicker<T>) (BlockEntityTicker<WeaponBlockEntity>) (l, p, s, be) -> be.serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof WeaponBlockEntity be) {
            be.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    /** Shared by the core and every part block: link with a laptop, otherwise open the inventory. */
    public static InteractionResult interact(Level level, BlockPos core, Player player, ItemStack stack) {
        if (!(level.getBlockEntity(core) instanceof WeaponBlockEntity be)) return InteractionResult.PASS;
        WeaponType type = be.type();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (be.handLoad(player, stack)) {
            // ammunition or fuel went straight in
        } else if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!type.laptopControlled()) {
                player.displayClientMessage(Component.translatable("message.steelrain.not_linkable"), true);
            } else {
                boolean linked = LaptopItem.toggleLink(stack, core);
                player.displayClientMessage(Component.translatable(
                        linked ? "message.steelrain.linked" : "message.steelrain.unlinked",
                        Component.translatable(type.translationKey())), true);
            }
        } else if (player instanceof ServerPlayer sp) {
            sp.openMenu(be, core);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player, stack).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, ItemStack.EMPTY);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WeaponBlockEntity be && !Blast.destroying()) {
                for (int i = 0; i < be.items.getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.items.getStackInSlot(i));
                }
            }
            // Take the rest of the vehicle with it.
            forEachPart(pos, state.getValue(FACING), type, p -> {
                if (level.getBlockState(p).is(ModRegistry.WEAPON_PART.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3 | 32);
                }
            });
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
