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
    private static final String[] GEAR = {"gearN", "gearL", "gearR"};
    private final BoxModel model;
    private final float axisHeight;
    private final float pitchFactor;

    /** @param axisHeight how far the model's origin sits above the entity's feet */
    private boolean glow;

    /** Makes the model ignore the light level, for tracers. */
    public EntityModelRenderer<T> fullBright() {
        glow = true;
        return this;
    }

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
        float time = entity.tickCount + partialTick;
        if (model.has("prop")) model.get("prop").zRot = time * 1.9f;
        if (entity instanceof FpvDroneEntity drone) {
            if (drone.mavic()) model.get("grenade").visible = drone.hasGrenade();
            // Rotors spin only while it is being flown; neighbours turn opposite ways.
            for (int i = 1; i <= 4; i++) {
                model.get("r" + i).yRot = drone.active() ? time * (i % 2 == 0 ? 2.3f : -2.3f) : 0.5f * i;
            }
        } else if (entity instanceof com.steelrain.entity.AircraftEntity aircraft) {
            pose.mulPose(Axis.ZP.rotation(aircraft.bank(partialTick)));
            float gear = aircraft.gearAnim(partialTick);
            for (String name : GEAR) {
                if (!model.has(name)) continue;
                model.get(name).xRot = gear * 1.5f;
                model.get(name).visible = gear < 0.97f;
            }
        }
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Models.PAINT)), glow ? 0xF000F0 : light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return Models.PAINT; }
}
