package com.steelrain.block;

import com.steelrain.ModRegistry;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class WeaponMenu extends AbstractContainerMenu {
    public final WeaponBlockEntity be;
    private final ContainerData data;
    private final int ownSlots;

    /** Client side. */
    public WeaponMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, (WeaponBlockEntity) inv.player.level().getBlockEntity(pos), new SimpleContainerData(3));
    }

    public WeaponMenu(int id, Inventory inv, WeaponBlockEntity be, ContainerData data) {
        super(ModRegistry.WEAPON_MENU.get(), id);
        this.be = be;
        this.data = data;
        WeaponType type = be.type();
        int n = 0;
        if (type.primaryAmmo() != null) {
            addSlot(new SlotItemHandler(be.items, WeaponBlockEntity.SLOT_PRIMARY, 44, 35));
            n++;
        }
        if (type.secondaryAmmo() != null) {
            addSlot(new SlotItemHandler(be.items, WeaponBlockEntity.SLOT_SECONDARY, 66, 35));
            n++;
        }
        ownSlots = n;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 156));
        }
        addDataSlots(data);
    }

    public static boolean usesEnergy(WeaponType type) { return type.usesEnergy(); }

    /** Stored energy in thousands of FE. */
    public int energyK() { return data.get(0); }
    public int busy() { return data.get(1); }
    public boolean switchedOn() { return data.get(2) != 0; }

    /** Button 0: the on/off switch of a jammer. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && be.type().kind == WeaponType.Kind.JAMMER) {
            be.toggle();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ownSlots) {
            if (!moveItemStackTo(stack, ownSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, ownSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !be.isRemoved() && player.distanceToSqr(be.getBlockPos().getCenter()) < 28 * 28;
    }
}
