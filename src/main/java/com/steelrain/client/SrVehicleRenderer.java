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
    private enum Part { BODY, TURRET, GUN, WHEEL, STEER_WHEEL, TRACK_L, TRACK_R, DOOR_L, DOOR_R, PAYLOAD, HATCH, SWAY, JACK, CHOCK, RECOIL_HEAVY, RECOIL_LIGHT, SPIN_RADAR, SCAN_DISH }

    private record Bone(BoxModel.Group group, Part part, int index) {}

    private final BoxModel model;
    private final List<Bone> bones = new ArrayList<>();
    private final int links;
    private final float trackLength;
    private final float[] trackY, trackZ, trackRot;
    private final Map<SrVehicle, float[]> state = new WeakHashMap<>();   // parked, parkedO, hatch, hatchO, stillTicks, lastTick

    public SrVehicleRenderer(EntityRendererProvider.Context ctx, String modelName) {
        super(ctx);
        this.model = BoxModel.load("vehicle", modelName);
        this.shadowRadius = 1.6f;
        var t = model.track;
        links = t == null ? 0 : t.get("links").getAsInt();
        trackLength = t == null ? 1 : t.get("length").getAsFloat();
        trackY = floats(t, "y"); trackZ = floats(t, "z"); trackRot = floats(t, "rot");
        int i = 0;
        for (BoxModel.Group g : model.groups()) {
            String n = BoxModel.nameOf(g);
            if (n.startsWith("trackL") || n.startsWith("trackR")) {
                bones.add(new Bone(g, n.charAt(5) == 'L' ? Part.TRACK_L : Part.TRACK_R, Integer.parseInt(n.substring(6))));
                continue;
            }
            Part part = n.equals("body") ? Part.BODY : n.equals("turret") ? Part.TURRET : n.equals("barrel") ? Part.GUN
                    : n.startsWith("wheel") ? (n.endsWith("Turn") ? Part.STEER_WHEEL : Part.WHEEL)
                    : n.startsWith("move_hatch") ? Part.HATCH : n.startsWith("move_sway") ? Part.SWAY : n.startsWith("move_jack") ? Part.JACK
                    : n.startsWith("move_chock") ? Part.CHOCK : n.equals("move_recoilHeavy") ? Part.RECOIL_HEAVY
                    : n.equals("move_recoilLight") ? Part.RECOIL_LIGHT : n.equals("move_spinRadar") ? Part.SPIN_RADAR
                    : n.equals("move_scanDish") ? Part.SCAN_DISH : n.equals("move_doorL") ? Part.DOOR_L
                    : n.equals("move_doorR") ? Part.DOOR_R : n.equals("move_payload") ? Part.PAYLOAD : null;
            if (part == Part.WHEEL || part == Part.STEER_WHEEL) {
                bones.add(new Bone(g, part, n.charAt(5) == 'L' ? 0 : 1));      // which side it runs on
            } else if (part != null) {
                bones.add(new Bone(g, part, i++));
            }
        }
    }

    private static float[] floats(com.google.gson.JsonObject t, String key) {
        if (t == null) return new float[2];
        var a = t.getAsJsonArray(key);
        float[] f = new float[a.size()];
        for (int k = 0; k < f.length; k++) f[k] = a.get(k).getAsFloat();
        return f;
    }

    private static float sample(float[] table, float t) {
        float f = t * (table.length - 1);
        int i = Math.min((int) f, table.length - 2);
        return Mth.lerp(f - i, table[i], table[i + 1]);
    }

    @Override
    public void render(T v, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        this.shadowRadius = Math.max(0.5f, v.getBbWidth() * 0.6f);
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
        float spinL = Mth.lerp(pt, v.spinLO, v.spinL), spinR = Mth.lerp(pt, v.spinRO, v.spinR);
        float runL = Mth.lerp(pt, v.trackLO, v.trackL) / trackLength, runR = Mth.lerp(pt, v.trackRO, v.trackR) / trackLength;
        // the barrel slams back in a tenth of a second and runs out again over a second
        float age = v.recoilAge + pt, back = age < 3 ? age / 3f : Math.max(0f, 1f - (age - 3f) / 21f);
        float speed = (float) Math.sqrt(Mth.square(v.getX() - v.xo) + Mth.square(v.getZ() - v.zo));
        for (Bone b : bones) {
            BoxModel.Group g = b.group;
            switch (b.part) {
                case BODY -> { g.xRot = leanP; g.zRot = leanR; }
                case TURRET -> g.yRot = -Mth.rotLerp(pt, v.turretYawO, v.turretYaw()) * Mth.DEG_TO_RAD;
                case GUN -> g.xRot = -Mth.lerp(pt, v.gunPitchO, v.gunPitch()) * Mth.DEG_TO_RAD;
                case WHEEL -> g.xRot = b.index == 0 ? spinL : spinR;
                case STEER_WHEEL -> { g.yRot = steer; g.xRot = b.index == 0 ? spinL : spinR; }
                case TRACK_L, TRACK_R -> {
                    // each link travels round the path: driving forward runs the top of the track forwards
                    float run = b.part == Part.TRACK_L ? runL : runR;
                    float at = (float) Mth.positiveModulo((double) b.index / links - run, 1.0);
                    g.oy = sample(trackY, at);
                    g.oz = sample(trackZ, at);
                    g.xRot = -sample(trackRot, at) * Mth.DEG_TO_RAD;
                }
                // the Iskander's roof doors swing open as the missile starts to rise
                case DOOR_L -> g.zRot = Mth.clamp(Mth.lerp(pt, v.gunPitchO, v.gunPitch()) / 6f, 0f, 1f) * 1.3f;
                case DOOR_R -> g.zRot = -Mth.clamp(Mth.lerp(pt, v.gunPitchO, v.gunPitch()) / 6f, 0f, 1f) * 1.3f;
                case PAYLOAD -> g.visible = !(v instanceof com.steelrain.entity.vehicle.SrErector e) || e.armed();
                case RECOIL_HEAVY -> g.oz = -back * 0.9f;
                case RECOIL_LIGHT -> g.oz = -back * 0.1f;
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
