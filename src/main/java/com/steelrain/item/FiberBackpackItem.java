package com.steelrain.item;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Reel of fibre-optic cable for wired FPV drones. Click Fiber Spools onto it in the inventory to refill. */
public class FiberBackpackItem extends Item {
    public static final int SPOOL = 250;

    public FiberBackpackItem(Properties properties) {
        super(properties);
    }

    public static int cable(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.CABLE.get(), SPOOL);
    }

    public static void spend(ItemStack stack, int blocks) {
        stack.set(ModRegistry.CABLE.get(), Math.max(0, cable(stack) - blocks));
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY && action != ClickAction.PRIMARY) return false;
        if (!other.is(ModRegistry.FIBER_SPOOL.get())) return false;
        int max = Config.FIBER_MAX.get();
        int have = cable(stack);
        if (have >= max) return true;
        stack.set(ModRegistry.CABLE.get(), Math.min(max, have + SPOOL));
        other.shrink(1);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Math.min(1f, cable(stack) / (float) Math.max(1, Config.FIBER_MAX.get())));
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x40C8FF; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain.cable", cable(stack), Config.FIBER_MAX.get()));
        tooltip.add(Component.translatable("tooltip.steelrain.fiber_backpack"));
    }
}
