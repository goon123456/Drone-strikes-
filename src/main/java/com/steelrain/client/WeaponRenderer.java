package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.WeaponType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the whole full-size vehicle from its core block, with the turret and barrel in their current pose. */
public class WeaponRenderer implements BlockEntityRenderer<WeaponBlockEntity> {
    @Override
    public void render(WeaponBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        WeaponType type = be.type();
        BoxModel model = Models.weapon(type);
        if (model == null) return;
        float yaw = Mth.rotLerp(partialTick, be.yawO, be.yaw) * Mth.DEG_TO_RAD;
        float pitch = Mth.lerp(partialTick, be.pitchO, be.pitch) * Mth.DEG_TO_RAD;
        if (be.getLevel() != null) {
            // Sample the light above the vehicle: the core block itself sits inside the hull.
            light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(type.height));
        }

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.facing().toYRot()));
        pose.translate(0, 0, -0.5);

        BoxModel.Group turret = model.get("turret"), gun = model.get("gun");
        if (turret != null) turret.yRot = -yaw;
        if (gun != null) gun.xRot = -pitch;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Models.PAINT));
        model.render(pose, vc, light);

        if (be.hasDome()) {
            pose.pushPose();
            pose.translate(0, type.height > 3 ? type.height - 0.6 : type.height, type.length / 2.0);
            Models.DOME.render(pose, vc, light);
            pose.popPose();
        }
        if (be.loaded() >= 0) {
            Vec3 g = type.gunPivot();
            switch (type) {
                case ORLAN_CATAPULT -> cargo(pose, vc, light, g, type.fixedPitch(), 0.3, 1.9, Models.aircraft(AircraftType.ORLAN));
                case GROUND_STATION -> {
                    // The aircraft waits at the start of the runway in front of the station.
                    AircraftType aircraft = AircraftType.values()[Mth.clamp(be.loaded(), 0, AircraftType.values().length - 1)];
                    pose.pushPose();
                    pose.translate(0, aircraft.gear, type.length + WeaponBlockEntity.RUNWAY_GAP + 0.5 + aircraft.length / 2);
                    Models.aircraft(aircraft).render(pose, vc, light);
                    pose.popPose();
                }
                default -> { }
            }
        }
        pose.popPose();
    }

    private static void cargo(PoseStack pose, VertexConsumer vc, int light, Vec3 pivot, float inclineDeg,
                              double up, double along, BoxModel model) {
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(Axis.XP.rotationDegrees(-inclineDeg));
        pose.translate(0, up, along);
        model.render(pose, vc, light);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WeaponBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 256; }

    @Override
    public AABB getRenderBoundingBox(WeaponBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(be.type() == WeaponType.GROUND_STATION ? 48 : 16);
    }
}
