package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.gun.AttachmentItem;
import com.steelrain.gun.Caliber;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunType;
import com.steelrain.gun.MagazineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws guns, magazines, fittings and loose rounds as their 3D models wherever the item is shown: in the hand of
 * another player, in an inventory slot, lying on the ground or in a frame. (The firer's own view of the gun in
 * their hands is drawn by {@link GunHand}.)
 */
public class GunItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GunItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Length of each gun and where its middle is along the bore, to fit it in a slot. */
    private static float[] extent(GunType t) {
        return switch (t) {
            case AK74M -> new float[]{0.96f, 0.13f, -0.04f};
            case M4A1 -> new float[]{0.84f, 0.08f, -0.05f};
            case SVD -> new float[]{1.23f, 0.19f, -0.04f};
            case PKM -> new float[]{1.20f, 0.22f, -0.05f};
            case M17 -> new float[]{0.24f, 0.0f, -0.05f};
            case M590 -> new float[]{1.06f, 0.11f, -0.04f};
            case M107 -> new float[]{1.46f, 0.35f, -0.03f};
            case RPG7 -> new float[]{1.32f, 0.16f, -0.02f};
        };
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (stack.getItem() instanceof GunItem gun) {
            float[] ex = extent(gun.type);
            switch (ctx) {
                case GUI -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(gun.type.pistol() ? 0 : 38));
                    pose.mulPose(Axis.YP.rotationDegrees(-90));
                    float s = (gun.type.pistol() ? 0.95f : 1.32f) / ex[0];
                    pose.scale(s, s, s);
                    pose.translate(0, -ex[2], -ex[1]);
                }
                case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                    // in the fist, bore along the forearm; a little larger than life so it reads at a distance
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    pose.scale(1.15f, 1.15f, 1.15f);
                    var grip = GunModels.gun(gun.type).point("grip");
                    pose.translate(-grip.x, -grip.y - 0.13f, -grip.z + (gun.type.pistol() ? 0.0f : 0.04f));
                }
                case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    pose.translate(0, 0.05, 0.1);
                }
                case GROUND -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(90));
                    pose.mulPose(Axis.YP.rotationDegrees(-90));
                    pose.translate(0, -ex[2], -ex[1]);
                }
                default -> {                                  // item frames, heads
                    pose.mulPose(Axis.YP.rotationDegrees(-90));
                    float s = Math.min(1f, 0.95f / ex[0]);
                    pose.scale(s, s, s);
                    pose.translate(0, -ex[2], -ex[1]);
                }
            }
            GunModels.render(stack, gun.type, GunModels.REST, pose, buffers, light);
        } else if (stack.getItem() instanceof MagazineItem mag) {
            place(ctx, pose, mag.gun == GunType.PKM ? 3.6f : mag.gun == GunType.M17 ? 6.0f : 3.8f);
            pose.translate(0, mag.gun == GunType.PKM ? 0.055 : mag.gun == GunType.M17 ? 0.06 : 0.1, mag.gun == GunType.AK74M ? 0.06 : 0);
            GunModels.renderMagazine(mag.gun, pose, buffers, light);
        } else if (stack.getItem() instanceof AttachmentItem att) {
            BoxModel m = GunModels.part(att.attachment);
            switch (att.attachment) {
                case RED_DOT -> { place(ctx, pose, 9f); pose.translate(0, -0.03, 0); }
                case ACOG -> { place(ctx, pose, 5.6f); pose.translate(0, -0.035, 0); }
                case PSO1 -> { place(ctx, pose, 2.5f); pose.translate(0, -0.01, 0); }
                case FOREGRIP -> { place(ctx, pose, 7.5f); pose.translate(0, 0.055, 0); }
                case SCOPE10 -> { place(ctx, pose, 2.6f); pose.translate(0, -0.04, 0); }
                default -> { place(ctx, pose, 5.2f); pose.translate(0, 0, -0.08); }
            }
            m.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture())), light);
        } else {
            for (Caliber c : Caliber.values()) {
                if (!stack.is(com.steelrain.ModRegistry.AMMO.get(c).get())) continue;
                BoxModel m = GunModels.ammo(c);
                place(ctx, pose, c.rocket() ? 1.35f : c == Caliber.B50 ? 5.5f : c == Caliber.G12 ? 9f : c == Caliber.P9 ? 20f : c == Caliber.R762 ? 9f : 12f);
                pose.mulPose(Axis.XP.rotationDegrees(c == Caliber.G12 ? 0 : c.rocket() ? -30 : -20));
                m.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture())), light);
            }
        }
        pose.popPose();
    }

    /** A small part shown side-on in a slot (tipped up a little), or at a sensible size in a hand or on the ground. */
    private static void place(ItemDisplayContext ctx, PoseStack pose, float guiScale) {
        switch (ctx) {
            case GUI -> {
                pose.mulPose(Axis.XP.rotationDegrees(18));
                pose.mulPose(Axis.YP.rotationDegrees(-60));
                pose.scale(guiScale, guiScale, guiScale);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(1.3f, 1.3f, 1.3f);
                pose.translate(0, 0, 0.05);
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                pose.translate(0, 0.1, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-70));
                pose.mulPose(Axis.XP.rotationDegrees(10));
                pose.scale(2.4f, 2.4f, 2.4f);
            }
            default -> {
                pose.mulPose(Axis.YP.rotationDegrees(-90));
                pose.scale(Math.min(guiScale, 3f), Math.min(guiScale, 3f), Math.min(guiScale, 3f));
            }
        }
    }
}
