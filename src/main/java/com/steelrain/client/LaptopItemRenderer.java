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
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        var mode = net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET;
        var level = Minecraft.getInstance().level;
        long t = level == null ? 0 : (level.getDayTime() + 6000) % 24000;
        String clock = String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
        font.drawInBatch("STEEL RAIN C2", 4, 4, 0xFF8DF29A, false, pose.last().pose(), buffers, mode, 0, full);
        font.drawInBatch(clock, 206 - font.width(clock), 4, 0xFFF5C451, false, pose.last().pose(), buffers, mode, 0, full);
        font.drawInBatch("FIRE CONTROL", 4, 20, 0xFF8DF29A, false, pose.last().pose(), buffers, mode, 0, full);
        font.drawInBatch("GRID " + player.getBlockX() + " " + player.getBlockZ(), 4, 34, 0xFF4F9A60, false, pose.last().pose(), buffers, mode, 0, full);
        font.drawInBatch("LINK  OK", 4, 48, 0xFF8DF29A, false, pose.last().pose(), buffers, mode, 0, full);
        if (Ui.blink()) font.drawInBatch("RIGHT-CLICK TO OPEN", 4, 112, 0xFFF5C451, false, pose.last().pose(), buffers, mode, 0, full);
        pose.popPose();
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
