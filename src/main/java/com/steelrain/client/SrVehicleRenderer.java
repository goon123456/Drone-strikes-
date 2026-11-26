package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.entity.vehicle.SrVehicle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws Steel Rain's own vehicles from their mesh models, moving the parts by the names the model generator gives
 * them: the hull leaning on its springs, wheels turning and steering, the turret and gun laid where the vehicle is
 * aiming, hatches, jacks, chocks and aerials.
 */
public class SrVehicleRenderer<T extends SrVehicle> extends EntityRenderer<T> {
    private enum Part { BODY, TURRET, GUN, WHEEL, STEER_WHEEL, HATCH, SWAY, JACK, CHOCK, RECOIL_HEAVY, RECOIL_LIGHT, SPIN_RADAR, SCAN_DISH }

    private record Bone(BoxModel.Group group, Part part, int index) {}

    private final BoxModel model;
    private final List<Bone> bones = new ArrayList<>();
    private final Map<SrVehicle, float[]> state = new WeakHashMap<>();   // parked, parkedO, hatch, hatchO, stillTicks, lastTick

    public SrVehicleRenderer(EntityRendererProvider.Context ctx, String modelName) {
        super(ctx);
        this.model = BoxModel.load("vehicle", modelName);
        this.shadowRadius = 1.6f;
        int i = 0;
        for (BoxModel.Group g : model.groups()) {
            String n = BoxModel.nameOf(g);
            Part part = n.equals("body") ? Part.BODY : n.equals("turret") ? Part.TURRET : n.equals("barrel") ? Part.GUN
                    : n.startsWith("wheel") ? (n.endsWith("Turn") ? Part.STEER_WHEEL : Part.WHEEL)
                    : n.startsWith("move_hatch") ? Part.HATCH : n.startsWith("move_sway") ? Part.SWAY : n.startsWith("move_jack") ? Part.JACK
                    : n.startsWith("move_chock") ? Part.CHOCK : n.equals("move_recoilHeavy") ? Part.RECOIL_HEAVY
                    : n.equals("move_recoilLight") ? Part.RECOIL_LIGHT : n.equals("move_spinRadar") ? Part.SPIN_RADAR
                    : n.equals("move_scanDish") ? Part.SCAN_DISH : null;
            if (part != null) bones.add(new Bone(g, part, i++));
        }
    }

    @Override
    public void render(T v, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float[] s = state.computeIfAbsent(v, k -> new float[]{1, 1, 0, 0, 0, -1});
        if (s[5] != v.tickCount) {
            // once a tick: parked after a second standing still; hatches open while it stands with a crew aboard
            s[5] = v.tickCount;
            s[1] = s[0]; s[3] = s[2];
            boolean still = Math.abs(v.getX() - v.xo) + Math.abs(v.getZ() - v.zo) < 0.01;
            s[4] = still ? s[4] + 1 : 0;
            s[0] = Mth.clamp(s[0] + (s[4] > 20 ? 0.03f : -0.06f), 0, 1);
            s[2] = Mth.clamp(s[2] + (!v.getPassengers().isEmpty() && s[4] > 20 ? 0.08f : -0.12f), 0, 1);
        }
        float parked = Mth.lerp(pt, s[1], s[0]), hatch = Mth.lerp(pt, s[3], s[2]);
        float time = v.tickCount + pt;
        float spin = Mth.lerp(pt, v.wheelSpinO, v.wheelSpin), steer = Mth.lerp(pt, v.steerShownO, v.steerShown);
        float leanP = v.leanPitch(pt), leanR = v.leanRoll(pt);
        float speed = (float) Math.sqrt(Mth.square(v.getX() - v.xo) + Mth.square(v.getZ() - v.zo));
        for (Bone b : bones) {
            BoxModel.Group g = b.group;
            switch (b.part) {
                case BODY -> { g.xRot = leanP; g.zRot = leanR; }
                case TURRET -> g.yRot = -Mth.rotLerp(pt, v.turretYawO, v.turretYaw()) * Mth.DEG_TO_RAD;
                case GUN -> g.xRot = -Mth.lerp(pt, v.gunPitchO, v.gunPitch()) * Mth.DEG_TO_RAD;
                case WHEEL -> g.xRot = spin;
                case STEER_WHEEL -> { g.yRot = steer; g.xRot = spin; }
                case HATCH -> g.xRot = -hatch * 1.75f;
                case SWAY -> {
                    float quiver = Math.min(1f, speed * 3f) * Mth.sin(time * 1.3f + b.index * 2.1f) * 0.035f;
                    g.xRot = -leanP * 5f + quiver;
                    g.zRot = -leanR * 5f + quiver * 0.6f;
                }
                case JACK -> g.oy = (1 - parked) * 0.4f;
                case CHOCK -> g.visible = parked > 0.6f;
                case SPIN_RADAR -> g.yRot = time * 0.3f;
                case SCAN_DISH -> g.yRot = Mth.sin(time * 0.02f) * 0.9f;
                default -> { }
            }
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, v.yRotO, v.getYRot())));
        pose.mulPose(Axis.XP.rotation(Mth.lerp(pt, v.tiltPitchO, v.tiltPitch)));
        pose.mulPose(Axis.ZP.rotation(Mth.lerp(pt, v.tiltRollO, v.tiltRoll)));
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture())), light);
        pose.popPose();
        super.render(v, entityYaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T v) { return model.texture(); }
}
