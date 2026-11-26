package com.steelrain.client;

import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.block.WeaponMenu;
import com.steelrain.weapon.WeaponType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** A launch site's loading panel, in the same rugged-tablet style as the Field Laptop. No textures needed. */
public class WeaponScreen extends AbstractContainerScreen<WeaponMenu> {
    private static final int GX = 140, GY = 30, GW = 14, GH = 40;

    public WeaponScreen(WeaponMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 180;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // rubber bezel and the panel inside it
        g.fill(x - 4, y - 4, x + imageWidth + 4, y + imageHeight + 4, 0xFF0B0D0C);
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF2A2E26);
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF050607);
        g.fill(x, y, x + imageWidth, y + imageHeight, Ui.BG);
        Ui.header(g, font, x + 4, y + 4, imageWidth - 8, title, null);
        // the site's own section and the player's
        Ui.panel(g, x + 4, y + 18, imageWidth - 8, 62);
        Ui.panel(g, x + 4, y + imageHeight - 98, imageWidth - 8, 94);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0A0E10);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF243038);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFF0E1316);
        }
        if (WeaponMenu.usesEnergy(menu.be.type())) {
            double frac = (double) menu.energyK() / (WeaponBlockEntity.ENERGY_CAPACITY / 1000);
            Ui.well(g, x + GX, y + GY, GW, GH);
            int segs = 10, colour = Ui.level(frac);
            for (int i = 0; i < segs; i++) {
                int sy = y + GY + GH - (i + 1) * GH / segs;
                g.fill(x + GX + 1, sy + 1, x + GX + GW - 1, sy + GH / segs, i < Math.round(frac * segs) ? colour : 0xFF253036);
            }
            Ui.icon(g, x + GX + 4, y + GY - 9, Ui.ICON_BOLT, Ui.CYAN);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WeaponType type = menu.be.type();
        if (type.primaryAmmo() != null) {
            g.drawString(font, Ui.upper(Component.translatable(type.launchesAircraft() ? "gui.steelrain.aircraft" : "gui.steelrain.ammo")), 44, 22, Ui.DIM, false);
        }
        boolean busy = menu.busy() > 0;
        Component status = busy ? Component.translatable("gui.steelrain.busy", (menu.busy() + 19) / 20) : Component.translatable("gui.steelrain.ready");
        Ui.chip(g, font, 90, 39, Ui.upper(status), busy ? Ui.AMBER : Ui.GREEN);
        Component hint = Component.translatable(type == WeaponType.GROUND_STATION ? "gui.steelrain.hint_station" : "gui.steelrain.hint_launcher");
        g.drawWordWrap(font, hint, 8, 58, 126, Ui.FAINT);
        g.drawString(font, Ui.upper(playerInventoryTitle), 8, inventoryLabelY, Ui.DIM, false);
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
