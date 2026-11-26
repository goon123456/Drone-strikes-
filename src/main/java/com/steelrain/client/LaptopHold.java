package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;

/**
 * The Field Laptop in the player's hands: the lid swings open when it is pulled out, and using it raises it up to the
 * eye before the fire-control screen comes up, as if looking into the display; closing the screen lowers it again.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class LaptopHold {
    private static final float LID_MS = 520, RAISE_MS = 280;
    private static boolean held;
    private static long equipAt, raiseAt = -1, lowerAt = -1;
    @Nullable private static Payloads.LaptopData pending;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var player = Minecraft.getInstance().player;
        boolean now = player != null && player.getMainHandItem().is(ModRegistry.FIELD_LAPTOP.get());
        if (now && !held) equipAt = Util.getMillis();
        if (!now) { raiseAt = -1; pending = null; }
        held = now;
        if (pending != null && raise() >= 1f) {
            Minecraft.getInstance().setScreen(new LaptopScreen(pending));
            pending = null;
        }
    }

    /** Right-click: start bringing it up to the face. */
    public static void raiseUp() {
        if (raiseAt < 0) raiseAt = Util.getMillis();
        lowerAt = -1;
    }

    /** The server answered: open the screen now if the laptop is already up, else as soon as it is. */
    public static boolean deferOpen(Payloads.LaptopData data) {
        if (!held || raiseAt < 0 || raise() >= 1f) return false;
        pending = data;
        return true;
    }

    /** The screen was closed: lower it again. */
    public static void closed() {
        if (raiseAt >= 0) lowerAt = Util.getMillis();
        raiseAt = -1;
    }

    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    /** How far the lid is open, 0 shut to 1 fully open; it starts once the laptop is in the hands. */
    public static float lid() { return held ? ease((Util.getMillis() - equipAt - 120) / LID_MS) : 0f; }

    /** How far it is raised to the face, 0 to 1. */
    public static float raise() {
        if (raiseAt >= 0) return ease((Util.getMillis() - raiseAt) / RAISE_MS);
        if (lowerAt >= 0) return 1f - ease((Util.getMillis() - lowerAt) / RAISE_MS);
        return 0f;
    }

    private static final LaptopItemRenderer HELD = new LaptopItemRenderer();

    /**
     * First person: the laptop is held out in both hands like a map, keyboard towards the player, and the lid swings
     * open; using it brings it up towards the eye. Drawn here instead of as an item so both arms can hold it.
     */
    @SubscribeEvent
    public static void hand(net.neoforged.neoforge.client.event.RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !player.getMainHandItem().is(ModRegistry.FIELD_LAPTOP.get())) return;
        e.setCanceled(true);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;    // both arms are drawn below
        var pose = e.getPoseStack();
        var buffers = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float up = raise(), equip = e.getEquipProgress();
        pose.pushPose();
        // the same framing Minecraft uses for a map held in both hands, lifted towards the eye while in use
        pose.translate(0, 0.04f - equip * 1.2f - 0.12f + up * 0.16f, -0.72f + up * 0.22f);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-21f + up * 14f));
        if (!player.isInvisible()) {
            pose.pushPose();
            pose.translate(0, 0.1f, 0.04f);              // up to grip the laptop's sides
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            arm(pose, buffers, light, player, net.minecraft.world.entity.HumanoidArm.RIGHT);
            arm(pose, buffers, light, player, net.minecraft.world.entity.HumanoidArm.LEFT);
            pose.popPose();
        }
        // the laptop sits in the hands: base towards the player, tipped up so the keyboard and screen face the eye
        pose.translate(0, -0.1f + up * 0.04f, 0.16f);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(58f + up * 12f));
        pose.scale(1.05f, 1.05f, 1.05f);
        float open = Math.max(lid(), up);
        HELD.renderModel(pose, buffers, light, open);
        if (open > 0.3f) HELD.renderDisplay(pose, buffers, player, open);
        pose.popPose();
    }

    /** One arm reaching forward to hold the laptop's side, posed as for a two-handed map. */
    private static void arm(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                            net.minecraft.client.player.AbstractClientPlayer player, net.minecraft.world.entity.HumanoidArm side) {
        var renderer = (net.minecraft.client.renderer.entity.player.PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        pose.pushPose();
        float f = side == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(92f));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(45f));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f * -41f));
        pose.translate(f * 0.3f, -1.1f, 0.45f);
        if (side == net.minecraft.world.entity.HumanoidArm.RIGHT) renderer.renderRightHand(pose, buffers, light, player);
        else renderer.renderLeftHand(pose, buffers, light, player);
        pose.popPose();
    }

    private LaptopHold() {}
}
