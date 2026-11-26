package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steelrain.entity.CannonShellEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tracer rounds as a short glowing streak along their flight path, bright at the head and fading behind; the rounds
 * between tracers are too small and fast to see, as in life.
 */
public class CannonShellRenderer extends EntityRenderer<CannonShellEntity> {
    public CannonShellRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(CannonShellEntity shell, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!shell.tracer()) return;
        Vec3 v = shell.getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        Vec3 dir = v.normalize();
        Vec3 here = new Vec3(Mth.lerp(pt, shell.xo, shell.getX()), Mth.lerp(pt, shell.yo, shell.getY()), Mth.lerp(pt, shell.zo, shell.getZ()));
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(here);
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1e-6) return;
        // a little wider far away so a tracer stays visible across the sky
        double width = Mth.clamp(toCamera.length() * 0.004, 0.05, 0.4);
        side = side.normalize().scale(width);
        Vec3 tail = dir.scale(-Math.min(4.0, v.length() * 0.8));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        vertex(vc, m, side, 1f, 0.85f, 0.45f, 0.95f);
        vertex(vc, m, side.scale(-1), 1f, 0.85f, 0.45f, 0.95f);
        vertex(vc, m, tail.subtract(side.scale(0.5)), 1f, 0.45f, 0.1f, 0f);
        vertex(vc, m, tail.add(side.scale(0.5)), 1f, 0.45f, 0.1f, 0f);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonShellEntity shell) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
