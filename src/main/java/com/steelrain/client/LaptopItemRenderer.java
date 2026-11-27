package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the Field Laptop as its 3D model wherever the item is shown, with the lid open while it is in use. */
public class LaptopItemRenderer extends BlockEntityWithoutLevelRenderer {
    private BoxModel model;
    private BoxModel.Group lid;

    public LaptopItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private void load() {
        if (model == null) {
            model = BoxModel.load("field_laptop");
            for (BoxModel.Group g : model.groups()) if (BoxModel.nameOf(g).equals("lid")) lid = g;
        }
    }

    /** The laptop at the origin, keyboard towards +z, with the lid open by a fraction. */
    public void renderModel(PoseStack pose, MultiBufferSource buffers, int light, float open) {
        load();
        if (lid != null) lid.xRot = -open * 1.92f;      // about 110 degrees when fully open
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture())), light);
    }

    /**
     * What the display shows while it is held: the system name, the clock, the holder's grid and a prompt, glowing
     * on the inside of the lid. Drawn in the lid's own frame so it swings with it.
     */
    public void renderDisplay(PoseStack pose, MultiBufferSource buffers, net.minecraft.world.entity.player.Player player, float open) {
        if (lid == null) return;
        var font = Minecraft.getInstance().font;
        pose.pushPose();
        // into the lid's frame: about the hinge, by the lid's angle
        pose.translate(0, 0.034, -0.135);
        pose.mulPose(Axis.XP.rotation(-open * 1.92f));
        pose.translate(0, -0.034, 0.135);
        // the display lies on the underside of the closed lid; text runs across it, top line at the hinge
        // text is drawn with its y flipped (as on signs) so it faces out of the display; lines run from the lid's
        // free edge towards the hinge, which is top to bottom once the lid is up
        pose.translate(-0.148, 0.034 - 0.0042, 0.135 - 0.045);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        float s = 0.0014f;
        pose.scale(s, -s, s);
        var mc = Minecraft.getInstance();
        var level = mc.level;
        long time = level == null ? 0 : (level.getDayTime() + 6000) % 24000;
        String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", time / 1000, time % 1000 * 60 / 1000);
        ItemStack tablet = player.getMainHandItem().is(com.steelrain.ModRegistry.FIELD_LAPTOP.get())
                ? player.getMainHandItem() : player.getOffhandItem();
        int links = com.steelrain.item.LaptopItem.links(tablet).size() + com.steelrain.item.LaptopItem.vehicles(tablet).size();

        displayRect(pose, buffers, 0, 0, 210, 120, Ui.PANEL, -0.06f);
        displayRect(pose, buffers, 0, 0, 210, 18, Ui.PANEL_3, -0.04f);
        displayRect(pose, buffers, 0, 17, 210, 1, Ui.EDGE_LIGHT, -0.02f);
        displayRect(pose, buffers, 3, 4, 2, 9, Ui.GREEN, -0.02f);
        displayText(pose, buffers, "STEEL RAIN C2", 9, 5, Ui.TEXT, 147);
        displayText(pose, buffers, clock, 204 - font.width(clock), 5, Ui.GREEN, 40);
        displayText(pose, buffers, "FIRE CONTROL", 7, 25, Ui.TEXT, 196);
        displayRect(pose, buffers, 7, 39, 196, 1, Ui.EDGE, -0.02f);
        displayText(pose, buffers, "GRID", 7, 47, Ui.DIM, 45);
        displayText(pose, buffers, player.getBlockX() + "  " + player.getBlockZ(), 57, 47, Ui.TEXT, 146);
        displayText(pose, buffers, "ELEV", 7, 61, Ui.DIM, 45);
        displayText(pose, buffers, "Y " + player.getBlockY(), 57, 61, Ui.TEXT, 70);
        String wx = level == null ? "--" : level.isThundering() ? "STORM" : level.isRaining() ? "RAIN" : "CLEAR";
        displayText(pose, buffers, wx, 203 - font.width(wx), 61, Ui.DIM, 70);
        displayText(pose, buffers, "SAVED LINKS", 7, 77, Ui.DIM, 140);
        displayText(pose, buffers, links + "/" + com.steelrain.item.LaptopItem.MAX_LINKS, 175, 77, links > 0 ? Ui.GREEN : Ui.DIM, 30);
        for (int i = 0; i < com.steelrain.item.LaptopItem.MAX_LINKS; i++)
            displayRect(pose, buffers, 7 + i * 25, 90, 21, 3, i < links ? Ui.GREEN : Ui.EDGE, -0.02f);
        String prompt = links == 0 ? "NO UNITS LINKED" : "OPEN COMMAND DISPLAY";
        displayText(pose, buffers, prompt, 7, 100, links > 0 ? Ui.GREEN : Ui.DIM, 196);
        displayText(pose, buffers, "[" + Ui.keyName(mc.options.keyUse) + "] OPEN", 7, 112, Ui.DIM, 196);
        pose.popPose();
    }

    /** Distinct depths preserve the layer order when the world buffer sorts translucent quads. */
    private static void displayRect(PoseStack pose, MultiBufferSource buffers, int x, int y, int w, int h, int colour, float depth) {
        var vertices = buffers.getBuffer(RenderType.textBackground());
        var matrix = pose.last().pose();
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        vertices.addVertex(matrix, x, y + h, depth).setColor(colour).setLight(light);
        vertices.addVertex(matrix, x + w, y + h, depth).setColor(colour).setLight(light);
        vertices.addVertex(matrix, x + w, y, depth).setColor(colour).setLight(light);
        vertices.addVertex(matrix, x, y, depth).setColor(colour).setLight(light);
    }

    private static void displayText(PoseStack pose, MultiBufferSource buffers, String text, int x, int y, int colour, int width) {
        var font = Minecraft.getInstance().font;
        font.drawInBatch(font.plainSubstrByWidth(text, width), x, y, colour, false, pose.last().pose(), buffers,
                net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        load();
        float open;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        switch (ctx) {
            case GUI -> {
                pose.mulPose(Axis.XP.rotationDegrees(28));
                pose.mulPose(Axis.YP.rotationDegrees(25));
                pose.scale(2.5f, 2.5f, 2.5f);
                pose.translate(0, -0.1, 0.02);
                open = 0.72f;
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                float up = LaptopHold.raise();
                float side = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1 : -1;
                // held low in front, keyboard towards the player, the screen tipped back; raised to the eye in use
                pose.translate(side * (-0.18 - 0.38 * up), -0.12 + 0.32 * up, 0.05 + 0.32 * up);
                pose.mulPose(Axis.YP.rotationDegrees(180 + side * (12 - 12 * up)));
                pose.mulPose(Axis.XP.rotationDegrees(10 + 30 * up));
                float s = 2.0f + 0.6f * up;
                pose.scale(s, s, s);
                open = Math.max(LaptopHold.lid(), up);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                // carried flat on the forearm in front of the body, lid up
                pose.translate(0, 0.1, -0.05);
                pose.mulPose(Axis.XP.rotationDegrees(75));      // screen towards the holder
                pose.scale(1.4f, 1.4f, 1.4f);
                open = 0.8f;
            }
            default -> {
                pose.scale(1.6f, 1.6f, 1.6f);
                pose.translate(0, -0.1, 0);
                open = 0f;
            }
        }
        if (lid != null) lid.xRot = -open * 1.92f;      // about 110 degrees when fully open
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture())), light);
        pose.popPose();
    }
}
