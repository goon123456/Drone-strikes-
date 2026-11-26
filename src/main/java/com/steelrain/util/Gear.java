package com.steelrain.util;

import com.steelrain.ModRegistry;
import com.steelrain.compat.CuriosCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Worn equipment: looked for in the Curios back slot when Curios is installed, then anywhere in the inventory. */
public final class Gear {
    public static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static ItemStack find(Player player, Item item) {
        if (CURIOS) {
            ItemStack worn = CuriosCompat.find(player, item);
            if (!worn.isEmpty()) return worn;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Runs a switched-on jammer backpack: drains its battery and keeps its bubble alive. */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 10 != 0 || player.isSpectator()) return;
        ItemStack pack = find(player, ModRegistry.JAMMER_BACKPACK.get());
        if (pack.isEmpty() || !pack.getOrDefault(ModRegistry.ACTIVE.get(), false)) return;
        int charge = pack.getOrDefault(ModRegistry.BATTERY.get(), com.steelrain.item.JammerBackpackItem.FULL);
        if (charge <= 0) {
            pack.set(ModRegistry.ACTIVE.get(), false);
            return;
        }
        pack.set(ModRegistry.BATTERY.get(), Math.max(0, charge - 10));
        Jamming.emit((ServerLevel) player.level(), player.getUUID(), player.position().add(0, 1, 0),
                com.steelrain.Config.BACKPACK_JAMMER_RANGE.get(), player.getUUID(), player.getGameProfile().getName());
    }

    private Gear() {}
}
