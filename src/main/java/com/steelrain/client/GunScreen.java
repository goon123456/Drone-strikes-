package com.steelrain.client;

import com.mojang.math.Axis;
import com.steelrain.gun.Attachment;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunLogic;
import com.steelrain.gun.GunType;
import com.steelrain.net.Payloads;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The gun on the bench: it turns slowly in the middle with what is fitted to it, the three mounting points are
 * listed with everything that could go on each, and the gun's figures are set out beside it. Clicking a fitting
 * takes it from the pack and puts it on; clicking the one already fitted takes it off.
 */
public class GunScreen extends Screen {
    private ItemStack shown = ItemStack.EMPTY;
    private float spin = -35f;
    private boolean dragging;

    public GunScreen() { super(Component.translatable("gui.steelrain.gun.fittings")); }

    private ItemStack gun() {
        ItemStack s = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return s.getItem() instanceof GunItem ? s : ItemStack.EMPTY;
    }

    @Override
    protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        ItemStack stack = gun();
        shown = stack.copy();
        if (stack.isEmpty()) return;
        GunType type = ((GunItem) stack.getItem()).type;
        int x = width / 2 - 190, y = height / 2 + 34;
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            int bx = x + 70;
            Attachment fitted = GunItem.part(stack, slot);
            for (Attachment a : Attachment.VALUES) {
                if (a.slot != slot || !type.fits.contains(a)) continue;
                boolean on = fitted == a;
                boolean have = on || minecraft.player.getAbilities().instabuild
                        || minecraft.player.getInventory().contains(new ItemStack(com.steelrain.ModRegistry.ATTACHMENTS.get(a).get()));
                Component name = Component.translatable("item.steelrain." + a.id);
                int bw = font.width(name) + 14;
                var button = new Ui.TabletButton(bx, y, bw, 16, name, on, () -> {
                    PacketDistributor.sendToServer(on ? new Payloads.GunAction(GunLogic.ACT_REMOVE, slot.ordinal()) : new Payloads.GunAction(GunLogic.ACT_FIT, a.ordinal()));
                });
                button.active = have;
                addRenderableWidget(button);
                bx += bw + 4;
            }
            y += 22;
        }
    }

    @Override
    public void tick() {
        ItemStack now = gun();
        if (now.isEmpty()) { onClose(); return; }
        if (!ItemStack.isSameItemSameComponents(now, shown)) rebuild();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (my < height / 2.0 + 28) { spin += (float) dx * 0.8f; dragging = true; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { dragging = false; return super.mouseReleased(mx, my, button); }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0080B0D);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack stack = gun();
        if (stack.isEmpty()) return;
        GunType type = ((GunItem) stack.getItem()).type;
        int x = width / 2 - 190, top = height / 2 - 108, w = 380;
        Ui.header(g, font, x, top, w, Component.translatable("gui.steelrain.gun.fittings"), Component.translatable("item.steelrain." + type.id));
        Ui.well(g, x, top + 14, w - 122, 122);
        // the gun, turning
        if (!dragging) spin += partialTick * 0.35f;
        float[] ex = GunItemRenderer.extent(type);
        float len = ex[0] + 0.03f;
        float mid = ex[1];
        float scale = (type.pistol() ? 170f : 300f) / len;
        g.pose().pushPose();
        g.pose().translate(x + (w - 122) / 2f, top + 78, 150);
        g.pose().scale(scale, -scale, scale);
        g.pose().mulPose(Axis.XP.rotationDegrees(-12));
        g.pose().mulPose(Axis.YP.rotationDegrees(spin));
        g.pose().translate(0, -ex[2], -mid);
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        GunModels.render(stack, type, GunModels.REST, g.pose(), g.bufferSource(), 0xF000F0);
        g.flush();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        g.pose().popPose();

        // figures
        int fx = x + w - 116, fy = top + 15;
        Ui.panel(g, fx, top + 13, 116, 124);
        var cal = GunItem.caliber(stack);
        String[][] rows = {
                {"gui.steelrain.gun.s_calibre", cal.label},
                {"gui.steelrain.gun.s_velocity", Math.round(cal.metresPerSecond * type.velocityFactor) + " m/s"},
                {"gui.steelrain.gun.s_rate", type.rpm + " /min"},
                {"gui.steelrain.gun.s_feed", Integer.toString(type.capacity)},
                {"gui.steelrain.gun.s_accuracy", String.format("%.1f MOA", type.moa)},
                {"gui.steelrain.gun.s_weight", String.format("%.1f kg", type.weightKg)},
                {"gui.steelrain.gun.s_zero", GunItem.zeroRange(stack) + " m"},
                {"gui.steelrain.gun.s_drop300", String.format("%.2f m", -cal.heightAt(300, cal.elevation(100, type.velocityFactor), type.velocityFactor))},
                {"gui.steelrain.gun.s_v300", Math.round(cal.at(300, type.velocityFactor)[0]) + " m/s"},
        };
        for (String[] row : rows) {
            Ui.field(g, font, fx + 5, fy + 3, 106, Component.translatable(row[0]), Component.literal(row[1]), Ui.TEXT);
            fy += 13;
        }
        // mounting points
        int y = height / 2 + 34;
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.gun.slot." + slot.name().toLowerCase(java.util.Locale.ROOT))), x, y + 4, Ui.DIM, false);
            boolean any = false;
            for (Attachment a : Attachment.VALUES) if (a.slot == slot && type.fits.contains(a)) any = true;
            if (!any) g.drawString(font, Component.translatable("gui.steelrain.gun.no_mount"), x + 70, y + 4, Ui.FAINT, false);
            y += 22;
        }
        g.drawString(font, Component.translatable("gui.steelrain.gun.fit_hint"), x, y + 4, Ui.FAINT, false);
    }
}
