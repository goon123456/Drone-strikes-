package com.steelrain.item;

import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Remote fire control: link launchers by right-clicking them, then open it to pick targets on a map. */
public class LaptopItem extends Item {
    public static final int MAX_LINKS = 8;

    public LaptopItem(Properties properties) {
        super(properties);
    }

    public static List<BlockPos> links(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.LINKS.get(), List.of());
    }

    /** @return true if the position is linked after the call. */
    public static boolean toggleLink(ItemStack stack, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(links(stack));
        boolean linked;
        if (list.remove(pos)) {
            linked = false;
        } else {
            if (list.size() >= MAX_LINKS) list.remove(0);
            list.add(pos.immutable());
            linked = true;
        }
        stack.set(ModRegistry.LINKS.get(), List.copyOf(list));
        return linked;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Payloads.sendLaptop(sp, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain.laptop_links", links(stack).size(), MAX_LINKS));
        tooltip.add(Component.translatable("tooltip.steelrain.laptop_hint"));
    }
}
