package com.steelrain.client;

import com.steelrain.Config;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.block.WeaponMenu;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.WeaponType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Loading console. Slot positions stay identical to the server menu at every window size. */
public class WeaponScreen extends AbstractContainerScreen<WeaponMenu> {
    private static final int CORE_W = 176, SIDE_X = 182, SIDE_W = 158;
    private static final int GX = 145, GY = 39, GW = 14, GH = 31;
    private boolean expanded;

    public WeaponScreen(WeaponMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = CORE_W;
        imageHeight = 180;
        inventoryLabelY = 86;
    }

    @Override
    protected void init() {
        expanded = width >= 368;
        imageWidth = expanded ? SIDE_X + SIDE_W + 4 : CORE_W;
        super.init();
    }

    private ItemStack primary() {
        return menu.be.type().primaryAmmo() == null ? ItemStack.EMPTY : menu.getSlot(0).getItem();
    }

    private ItemStack secondary() {
        return menu.be.type().secondaryAmmo() == null ? ItemStack.EMPTY : menu.getSlot(1).getItem();
    }

    private int launchEnergy() {
        AircraftType aircraft = AircraftType.of(primary());
        return aircraft == null ? menu.be.type().energyPerShot : aircraft.energy;
    }

    private boolean lowPower() {
        return Config.REQUIRE_ENERGY.get() && WeaponMenu.usesEnergy(menu.be.type())
                && menu.energyK() * 1000 < Math.max(1, launchEnergy());
    }

    private String loadState() {
        if (menu.busy() > 0) return String.format(Locale.ROOT, "BUSY %.1f s", menu.busy() / 20.0);
        if (primary().isEmpty()) return menu.be.type().launchesAircraft() ? "NO AIRCRAFT LOADED" : "NO AMMUNITION";
        if (lowPower()) return "INSUFFICIENT POWER";
        return "LOADOUT PRESENT";
    }

    private int stateColour() {
        return menu.busy() > 0 ? Ui.AMBER : primary().isEmpty() || lowPower() ? Ui.AMBER : Ui.GREEN;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 4, y - 4, x + imageWidth + 4, y + imageHeight + 4, 0xFF263831);
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF030808);
        g.fill(x, y, x + imageWidth, y + imageHeight, Ui.BG);
        g.fillGradient(x, y, x + imageWidth, y + 18, 0xFF1A2923, 0xFF080E0D);
        g.fill(x + 4, y + 17, x + imageWidth - 4, y + 18, Ui.EDGE);
        Ui.instrument(g, font, x + 4, y + 20, CORE_W - 8, 60, "LOADOUT", "");
        Ui.instrument(g, font, x + 4, y + 82, CORE_W - 8, 94, "INVENTORY", "");
        if (expanded) Ui.instrument(g, font, x + SIDE_X, y + 20, SIDE_W, 156, "SYSTEM CHECK", "");
        int own = menu.be.type().primaryAmmo() == null ? 0 : menu.be.type().secondaryAmmo() == null ? 1 : 2;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            int edge = i < own ? slot.hasItem() ? Ui.GREEN : Ui.CYAN : Ui.EDGE;
            g.fill(sx, sy, sx + 18, sy + 18, edge);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0A1111);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFF030807);
            if (i < own && !slot.hasItem()) Ui.icon(g, sx + 5, sy + 5,
                    i == 0 && menu.be.type().launchesAircraft() ? Ui.ICON_PLANE : Ui.ICON_MISSILE, Ui.FAINT);
        }
        if (WeaponMenu.usesEnergy(menu.be.type())) {
            double frac = menu.energyK() / (WeaponBlockEntity.ENERGY_CAPACITY / 1000.0);
            Ui.well(g, x + GX, y + GY, GW, GH);
            int filled = (int) Math.round(Math.min(1, Math.max(0, frac)) * 10);
            for (int i = 0; i < 10; i++) {
                int sy = y + GY + GH - (i + 1) * 3;
                g.fill(x + GX + 1, sy, x + GX + GW - 1, sy + 2, i < filled ? Ui.level(frac) : Ui.EDGE);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WeaponType type = menu.be.type();
        g.drawString(font, font.plainSubstrByWidth(Ui.upper(title).getString(), imageWidth - 16), 8, 5, Ui.TEXT, false);
        g.drawString(font, type.launchesAircraft() ? "UAV" : "RDS", 10, 39, Ui.DIM, false);
        String loaded = primary().isEmpty() ? "--" : Integer.toString(primary().getCount());
        g.drawString(font, loaded, 98, 39, primary().isEmpty() ? Ui.FAINT : Ui.GREEN, false);
        if (WeaponMenu.usesEnergy(type)) g.drawString(font, "PWR", GX - 2, 24, lowPower() ? Ui.AMBER : Ui.DIM, false);
        String state = font.plainSubstrByWidth(loadState(), WeaponMenu.usesEnergy(type) ? 128 : 150);
        g.fill(10, 65, 14, 69, stateColour());
        g.drawString(font, state, 19, 63, stateColour(), false);
        if (expanded) drawSystem(g);
    }

    private void drawSystem(GuiGraphics g) {
        int x = SIDE_X + 8, w = SIDE_W - 16, y = 43;
        Ui.readout(g, font, x, y, w, "PRIMARY", primary().isEmpty() ? "EMPTY" : primary().getCount() + " / " + menu.be.type().primaryLimit,
                primary().isEmpty() ? Ui.AMBER : Ui.GREEN);
        y += 15;
        if (menu.be.type().secondaryAmmo() != null) {
            Ui.readout(g, font, x, y, w, "PAYLOAD", secondary().isEmpty() ? "EMPTY" : secondary().getCount() + " / " + menu.be.type().secondaryLimit,
                    secondary().isEmpty() ? Ui.DIM : Ui.TEXT);
            y += 15;
        }
        if (WeaponMenu.usesEnergy(menu.be.type())) {
            Ui.readout(g, font, x, y, w, "ENERGY", menu.energyK() + " kFE", lowPower() ? Ui.AMBER : Ui.CYAN);
            y += 15;
        }
        Ui.readout(g, font, x, y, w, "CYCLE", menu.busy() > 0 ? String.format(Locale.ROOT, "%.1f s", menu.busy() / 20.0) : "IDLE", menu.busy() > 0 ? Ui.AMBER : Ui.TEXT);
        y += 16;
        g.fill(x, y, x + w, y + 1, Ui.EDGE);
        g.drawString(font, "FIELD LAPTOP", x, y + 8, Ui.GREEN, false);
        Component hint = Component.translatable(menu.be.type() == WeaponType.GROUND_STATION
                ? "gui.steelrain.hint_station" : "gui.steelrain.hint_launcher");
        g.drawWordWrap(font, hint, x, y + 22, w, Ui.DIM);
        g.drawString(font, "SHIFT-CLICK TO TRANSFER", x, 162, Ui.FAINT, false);
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
