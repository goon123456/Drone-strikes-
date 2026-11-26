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

    /** Radar heads turn about once a second; jamming dishes sweep slowly from side to side. */
    public static void extras(VehicleEntity entity, VehicleModelInstance model, float partialTicks) {
        float time = entity.tickCount + partialTicks;
        BoneState radar = model.getBone("spinRadar");
        if (radar != null) radar.rotation.rotationY(time * 0.3f);
        BoneState dish = model.getBone("scanDish");
        if (dish != null) dish.rotation.rotationY(Mth.sin(time * 0.02f) * 0.9f);
    }

    private VehicleAnim() {}
}
