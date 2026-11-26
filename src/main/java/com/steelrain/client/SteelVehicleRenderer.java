package com.steelrain.client;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.client.renderer.entity.BasicVehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Superb Warfare's renderer plus Steel Rain's own moving parts: travelling track links, radars and dishes. */
public class SteelVehicleRenderer extends BasicVehicleRenderer {
    private final VehicleAnim.Track track;

    public SteelVehicleRenderer(EntityRendererProvider.Context context, String vehicle) {
        super(context);
        VehicleAnim.reset();
        this.track = VehicleAnim.track(vehicle);
    }

    @Override
    public void transformCustomModelPart(VehicleEntity entity, VehicleModelInstance instance, PoseStack poseStack, float entityYaw, float partialTicks) {
        super.transformCustomModelPart(entity, instance, poseStack, entityYaw, partialTicks);
        VehicleAnim.extras(entity, instance, partialTicks);
    }

    @Override
    public float getBoneMoveY(float t) { return track == null ? t : track.sample(track.y(), t); }

    @Override
    public float getBoneMoveZ(float t) { return track == null ? t : track.sample(track.z(), t); }

    @Override
    public float getBoneRotX(float t) { return track == null ? t : track.sample(track.rot(), t); }

    @Override
    public float getTrackDistance() { return track == null ? 2f : 100f / track.links(); }
}
