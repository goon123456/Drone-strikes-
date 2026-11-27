package com.steelrain.block;

import com.steelrain.ModRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/** Vehicle cargo uses vanilla chest slot, transfer, validity and close behavior with its own screen identity. */
public class SrStorageMenu extends ChestMenu {
    public SrStorageMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(27));
    }

    public SrStorageMenu(int id, Inventory inventory, Container cargo) {
        super(ModRegistry.STORAGE_MENU.get(), id, inventory, cargo, 3);
    }
}
