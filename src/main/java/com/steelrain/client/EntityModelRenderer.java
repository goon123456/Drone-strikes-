package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.entity.FpvDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Draws a box model pointed along the entity's yaw and pitch. */
public class EntityModelRenderer<T extends Entity> extends EntityRenderer<T> {
    private final BoxModel model;
    private final float axisHeight;
    private final float pitchFactor;

    /** @param axisHeight how far the model's origin sits above the entity's feet */
    public EntityModelRenderer(EntityRendererProvider.Context ctx, BoxModel model, float axisHeight, float pitchFactor) {
        super(ctx);
        this.model = model;
        this.axisHeight = axisHeight;
        this.pitchFactor = pitchFactor;
        this.shadowRadius = 0;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, axisHeight, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * pitchFactor));
        if (entity instanceof FpvDroneEntity drone && drone.mavic()) {
            model.get("grenade").visible = drone.hasGrenade();
        }
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Models.PAINT)), light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return Models.PAINT; }
}
