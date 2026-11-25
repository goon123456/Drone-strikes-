package com.steelrain.item;

import com.steelrain.ModRegistry;
import com.steelrain.entity.FpvDroneEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** Goggles + radio: right-click a drone to pair, right-click the air to take control. */
public class FpvControllerItem extends Item {
    public FpvControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            UUID id = stack.get(ModRegistry.LINKED_DRONE.get());
            if (id == null) {
                sp.displayClientMessage(Component.translatable("message.steelrain.fpv_not_linked"), true);
            } else if (sl.getEntity(id) instanceof FpvDroneEntity drone && drone.isAlive()) {
                drone.startPiloting(sp);
            } else {
                sp.displayClientMessage(Component.translatable("message.steelrain.fpv_not_found"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.has(ModRegistry.LINKED_DRONE.get())
                ? "tooltip.steelrain.controller_linked" : "tooltip.steelrain.controller_unlinked"));
    }
}
