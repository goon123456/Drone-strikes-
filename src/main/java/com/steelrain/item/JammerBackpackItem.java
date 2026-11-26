package com.steelrain.item;

import com.steelrain.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Personal jammer. Right-click to switch on or off; works from the Curios back slot or the inventory. */
public class JammerBackpackItem extends Item {
    public static final int FULL = 20 * 60 * 10;

    public JammerBackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean on = !stack.getOrDefault(ModRegistry.ACTIVE.get(), false);
            if (on && stack.getOrDefault(ModRegistry.BATTERY.get(), FULL) <= 0) {
                player.displayClientMessage(Component.translatable("message.steelrain.flat_battery"), true);
            } else {
                stack.set(ModRegistry.ACTIVE.get(), on);
                player.displayClientMessage(Component.translatable(on ? "message.steelrain.jammer_on" : "message.steelrain.jammer_off"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return stack.getOrDefault(ModRegistry.ACTIVE.get(), false); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.getOrDefault(ModRegistry.ACTIVE.get(), false)
                ? "tooltip.steelrain.switched_on" : "tooltip.steelrain.switched_off"));
        tooltip.add(Component.translatable("tooltip.steelrain.charge", stack.getOrDefault(ModRegistry.BATTERY.get(), FULL) / 20));
        tooltip.add(Component.translatable("tooltip.steelrain.jammer_backpack"));
    }
}
