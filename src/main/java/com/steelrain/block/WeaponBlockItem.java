package com.steelrain.block;

import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Places the core block plus the invisible parts that make up the rest of the vehicle. */
public class WeaponBlockItem extends BlockItem {
    private final WeaponType type;

    public WeaponBlockItem(WeaponBlock block, Properties properties) {
        super(block, properties);
        this.type = block.type();
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext ctx, BlockState state) {
        Level level = ctx.getLevel();
        BlockPos core = ctx.getClickedPos();
        Direction facing = state.getValue(WeaponBlock.FACING);
        BlockPos blocked = WeaponBlock.obstruction(level, core, facing, type);
        if (blocked != null) {
            Player player = ctx.getPlayer();
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.steelrain.no_space",
                        type.width, type.height, type.length, blocked.getX(), blocked.getY(), blocked.getZ()), true);
            }
            return false;
        }
        if (!super.placeBlock(ctx, state)) return false;
        if (!level.isClientSide) WeaponBlock.placeParts(level, core, facing, type);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain.footprint", type.width, type.height, type.length));
    }
}
