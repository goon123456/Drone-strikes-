package com.steelrain.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/** Only loaded when the Curios mod is present. */
public final class CuriosCompat {
    public static ItemStack find(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(item))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    private CuriosCompat() {}
}
