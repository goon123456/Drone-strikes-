package com.steelrain.client;

import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.block.WeaponMenu;
import com.steelrain.weapon.WeaponType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Inventory of a weapon block. Drawn with plain rectangles, so it needs no texture. */
public class WeaponScreen extends AbstractContainerScreen<WeaponMenu> {
    private static final int GX = 140, GY = 17, GW = 12, GH = 52;

    public WeaponScreen(WeaponMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 180;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1E241C);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF4B5A40);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
            g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }
        if (WeaponMenu.usesEnergy(menu.be.type())) {
            g.fill(x + GX - 1, y + GY - 1, x + GX + GW + 1, y + GY + GH + 1, 0xFF373737);
            g.fill(x + GX, y + GY, x + GX + GW, y + GY + GH, 0xFF1B1B1B);
            int filled = Math.min(GH, menu.energyK() * GH / (WeaponBlockEntity.ENERGY_CAPACITY / 1000));
            g.fill(x + GX, y + GY + GH - filled, x + GX + GW, y + GY + GH, 0xFF4CC2E0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WeaponType type = menu.be.type();
        g.drawString(font, title, 8, 6, 0x303828, false);
        g.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0x404040, false);
        if (type.primaryAmmo() != null) {
            g.drawString(font, Component.translatable(type.launchesAircraft() ? "gui.steelrain.aircraft" : "gui.steelrain.ammo"), 44, 24, 0x404040, false);
        }
        if (WeaponMenu.usesEnergy(type)) {
            g.drawString(font, Component.translatable("gui.steelrain.fuel"), 112, 24, 0x404040, false);
        }
        Component status = menu.busy() > 0
                ? Component.translatable("gui.steelrain.busy", (menu.busy() + 19) / 20)
                : Component.translatable("gui.steelrain.ready");
        g.drawString(font, status, 8, 60, menu.busy() > 0 ? 0xA03020 : 0x207020, false);
        Component hint = Component.translatable(switch (type.kind) {
            case LAUNCHER -> type == WeaponType.GROUND_STATION ? "gui.steelrain.hint_station" : "gui.steelrain.hint_launcher";
        });
        g.drawString(font, hint, 8, 72, 0x505050, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int x = leftPos + GX, y = topPos + GY;
        if (WeaponMenu.usesEnergy(menu.be.type()) && mouseX >= x && mouseX < x + GW && mouseY >= y && mouseY < y + GH) {
            g.renderTooltip(font, Component.translatable("gui.steelrain.fuel_amount", menu.energyK(),
                    WeaponBlockEntity.ENERGY_CAPACITY / 1000), mouseX, mouseY);
        }
    }
}
