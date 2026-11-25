package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steelrain.ModRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Draws a static box model pointed along the entity's yaw and pitch. */
public class SimpleRenderer<T extends Entity> extends EntityRenderer<T> {
    private final ModelPart model;
    private final ResourceLocation texture;
    private final float scale;
    private final float pitchFactor;

    public SimpleRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, String texture, float scale, float pitchFactor) {
        super(ctx);
        this.model = ctx.bakeLayer(layer);
        this.texture = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "textures/entity/" + texture + ".png");
        this.scale = scale;
        this.pitchFactor = pitchFactor;
        this.shadowRadius = 0.3f * scale;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * pitchFactor));
        pose.scale(scale, scale, scale);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return texture; }
}
