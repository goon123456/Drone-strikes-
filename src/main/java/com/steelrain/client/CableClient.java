package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the fibre-optic cables trailing behind wired drones. */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class CableClient {
    private record Cable(List<Vec3> points, long receivedAt) {}

    private static final Map<Integer, Cable> CABLES = new HashMap<>();

    static void update(Payloads.Cable p) {
        if (p.points().isEmpty()) CABLES.remove(p.droneId());
        else CABLES.put(p.droneId(), new Cable(p.points(), System.currentTimeMillis()));
    }

    public static void clear() { CABLES.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CABLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        CABLES.values().removeIf(c -> now - c.receivedAt > 5000); // the server refreshes live cables every 2 s

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        for (Map.Entry<Integer, Cable> e : CABLES.entrySet()) {
            List<Vec3> pts = e.getValue().points;
            for (int i = 1; i < pts.size(); i++) segment(vc, last, pts.get(i - 1), pts.get(i));
            Entity drone = mc.level.getEntity(e.getKey());
            if (drone != null) segment(vc, last, pts.get(pts.size() - 1), drone.getPosition(pt).add(0, 0.02, 0));
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void segment(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b) {
        Vec3 n = b.subtract(a);
        if (n.lengthSqr() < 1e-6) return;
        n = n.normalize();
        vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(225, 240, 255, 230).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(225, 240, 255, 230).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    private CableClient() {}
}
