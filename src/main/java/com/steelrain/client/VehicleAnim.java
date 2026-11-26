package com.steelrain.client;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steelrain.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The moving parts Superb Warfare does not animate by itself: where each track link is on its trip round the
 * wheels, radars that turn and dishes that sweep. The track paths are worked out by tools/vehicle_kit.py from
 * the same wheel positions the model uses, and stored in assets/steelrain/sbw/tracks.json.
 */
public final class VehicleAnim {
    /** One trip round the running gear, sampled at even spacing: offsets from the link's modelled position. */
    public record Track(int links, float[] y, float[] z, float[] rot) {
        float sample(float[] table, float t) {
            float f = Mth.clamp(t / 100f, 0f, 1f) * (table.length - 1);
            int i = Math.min((int) f, table.length - 2);
            return Mth.lerp(f - i, table[i], table[i + 1]);
        }
    }

    private static Map<String, Track> tracks;

    public static Track track(String vehicle) {
        if (tracks == null) tracks = load();
        return tracks.get(vehicle);
    }

    /** Forget the loaded paths, so a resource reload picks up new ones. */
    public static void reset() { tracks = null; }

    private static Map<String, Track> load() {
        Map<String, Track> out = new HashMap<>();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "sbw/tracks.json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String name : root.keySet()) {
                JsonObject t = root.getAsJsonObject(name);
                out.put(name, new Track(t.get("links").getAsInt(), floats(t.getAsJsonArray("y")), floats(t.getAsJsonArray("z")),
                        floats(t.getAsJsonArray("rot"))));
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Steel Rain: could not read track paths", e);
        }
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** How far a hull is leaning on its suspension. Kept per vehicle and eased from tick to tick. */
    private static final class Lean {
        float pitch, roll, pitchO, rollO;
        double speed;
        float yaw;
        int tick = -1;
    }

    private static final Map<VehicleEntity, Lean> LEANS = new java.util.WeakHashMap<>();

    /**
     * Radar heads turn about once a second and jamming dishes sweep slowly from side to side. The hull squats
     * when the vehicle pulls away, dips when it brakes and rolls outwards in a turn. Gun barrels slide back
     * when they fire and run out again.
     */
    public static void extras(VehicleEntity entity, VehicleModelInstance model, float partialTicks) {
        float time = entity.tickCount + partialTicks;
        BoneState radar = model.getBone("spinRadar");
        if (radar != null) radar.rotation.rotationY(time * 0.3f);
        BoneState dish = model.getBone("scanDish");
        if (dish != null) dish.rotation.rotationY(Mth.sin(time * 0.02f) * 0.9f);

        BoneState body = model.getBone("body");
        if (body != null) {
            Lean lean = LEANS.computeIfAbsent(entity, e -> new Lean());
            if (lean.tick != entity.tickCount) {
                net.minecraft.world.phys.Vec3 look = entity.getViewVector(1f);
                double speed = entity.getDeltaMovement().dot(look);
                float yaw = entity.getYRot();
                boolean first = lean.tick < 0;
                double accel = first ? 0 : speed - lean.speed;
                float turn = first ? 0 : Mth.wrapDegrees(yaw - lean.yaw);
                lean.speed = speed;
                lean.yaw = yaw;
                lean.tick = entity.tickCount;
                lean.pitchO = lean.pitch;
                lean.rollO = lean.roll;
                // a soft spring: head for the lean the forces ask for, a quarter of the way each tick
                float wantPitch = Mth.clamp((float) accel * 1.6f, -0.06f, 0.06f);
                float wantRoll = Mth.clamp(turn * (float) speed * 0.03f, -0.07f, 0.07f);
                lean.pitch += (wantPitch - lean.pitch) * 0.25f;
                lean.roll += (wantRoll - lean.roll) * 0.25f;
            }
            body.rotation.rotationX(Mth.lerp(partialTicks, lean.pitchO, lean.pitch)).rotateZ(Mth.lerp(partialTicks, lean.rollO, lean.roll));
        }

        recoil(entity, model.getBone("recoilHeavy"), 0.9f, 24, partialTicks);
        recoil(entity, model.getBone("recoilLight"), 0.1f, 2, partialTicks);
    }

    /** Slides a barrel straight back on firing (quickly) and lets it run out again (slowly). */
    private static void recoil(VehicleEntity entity, BoneState bone, float travel, int duration, float partialTicks) {
        if (bone == null) return;
        int timer = 0;
        for (int seat = 0; seat < 3; seat++) {
            for (int weapon = 0; weapon < 4; weapon++) timer = Math.max(timer, entity.getShootAnimationTimer(seat, weapon));
        }
        float back = 0f;
        if (timer > 0) {
            float done = Mth.clamp(1f - (timer - partialTicks) / duration, 0f, 1f);
            back = done < 0.12f ? done / 0.12f : 1f - (done - 0.12f) / 0.88f;
        }
        // the model faces -Z, so backwards is +Z; bone offsets are in sixteenths of a block
        bone.z = bone.definition().pivotZ() * 16f + back * travel * 16f;
    }

    private VehicleAnim() {}
}
