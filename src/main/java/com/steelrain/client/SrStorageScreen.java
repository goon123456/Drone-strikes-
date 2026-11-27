package com.steelrain.client;

import com.steelrain.block.SrStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Steel Rain cargo console; unrelated vanilla chest screens keep their own appearance. */
public class SrStorageScreen extends AbstractContainerScreen<SrStorageMenu> {
    private static final int CORE_W = 176, SIDE_X = 182, SIDE_W = 158;
    private boolean expanded;

    public SrStorageScreen(SrStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CORE_W;
        imageHeight = 168;
        inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        expanded = width >= 368;
        imageWidth = expanded ? SIDE_X + SIDE_W + 4 : CORE_W;
        super.init();
    }

    private int occupied() {
        int count = 0;
        for (int i = 0; i < 27; i++) if (menu.getSlot(i).hasItem()) count++;
        return count;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 4, y - 4, x + imageWidth + 4, y + imageHeight + 4, 0xFF263831);
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF030808);
        g.fill(x, y, x + imageWidth, y + imageHeight, Ui.BG);
        g.fillGradient(x, y, x + imageWidth, y + 16, 0xFF1A2923, 0xFF080E0D);
        g.fill(x + 4, y + 15, x + imageWidth - 4, y + 16, Ui.EDGE);
        g.fill(x + 4, y + 72, x + CORE_W - 4, y + 73, Ui.EDGE);
        g.fillGradient(x + 4, y + 73, x + CORE_W - 4, y + 84, 0xFF14211C, 0xFF0A1210);
        if (expanded) Ui.instrument(g, font, x + SIDE_X, y + 20, SIDE_W, 144, "CARGO MANIFEST", "");
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, i < 27 && slot.hasItem() ? 0xFF496450 : Ui.EDGE);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0A1111);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFF030807);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String count = occupied() + "/27";
        g.drawString(font, font.plainSubstrByWidth(Ui.upper(title).getString(), imageWidth - font.width(count) - 28), 8, 4, Ui.TEXT, false);
        g.drawString(font, count, imageWidth - 8 - font.width(count), 4, Ui.GREEN, false);
        g.drawString(font, "INVENTORY", 8, inventoryLabelY + 1, Ui.DIM, false);
        if (!expanded) return;
        int x = SIDE_X + 8, w = SIDE_W - 16;
        int used = occupied(), items = 0;
        for (int i = 0; i < 27; i++) items += menu.getSlot(i).getItem().getCount();
        Ui.readout(g, font, x, 45, w, "USED", used + " / 27", Ui.TEXT);
        Ui.readout(g, font, x, 62, w, "FREE", Integer.toString(27 - used), used == 27 ? Ui.AMBER : Ui.GREEN);
        Ui.readout(g, font, x, 79, w, "ITEMS", Integer.toString(items), Ui.TEXT);
        Ui.gauge(g, x, 98, w, 5, used / 27.0, used == 27 ? Ui.AMBER : Ui.GREEN, 27);
        g.drawString(font, used == 0 ? "CARGO HOLD EMPTY" : "VEHICLE CARGO", x, 113, used == 0 ? Ui.DIM : Ui.GREEN, false);
        g.drawString(font, "SHIFT-CLICK TO TRANSFER", x, 136, Ui.DIM, false);
        g.drawString(font, "DRAG TO SPLIT STACKS", x, 149, Ui.FAINT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
