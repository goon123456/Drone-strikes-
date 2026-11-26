package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws a Jammer Dome on the roof of every vehicle that has one fitted. A dome can go on any Superb Warfare
 * vehicle, so it cannot be part of the vehicle's own model: the server tells us which vehicles carry one and
 * we draw it on top of them after the vehicles themselves.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class DomeClient {
    private static final Set<Integer> FITTED = new HashSet<>();

    static void fitted(int entityId) { FITTED.add(entityId); }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { FITTED.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FITTED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int id : FITTED) {
            Entity vehicle = mc.level.getEntity(id);
            if (vehicle == null || vehicle.isRemoved()) continue;      // out of sight; it is told again when seen
            double x = Mth.lerp(partial, vehicle.xo, vehicle.getX());
            double y = Mth.lerp(partial, vehicle.yo, vehicle.getY()) + vehicle.getBbHeight();
            double z = Mth.lerp(partial, vehicle.zo, vehicle.getZ());
            if (camera.distanceToSqr(x, y, z) > 160 * 160) continue;
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + 0.5, z));
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(-vehicle.getViewYRot(partial)));
            Models.DOME.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Models.DOME.texture())), light);
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(Models.DOME.texture()));
    }

    private DomeClient() {}
}
