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

    /** Put a file named steelrain_animtest in the game folder to hold doors and hatches open, for checking the models. */
    private static final boolean TEST = new java.io.File("steelrain_animtest").exists();

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
        BoneState radar = model.getBone("move_spinRadar");
        if (radar != null) radar.rotation.rotationY(time * 0.3f);
        BoneState dish = model.getBone("move_scanDish");
        if (dish != null) dish.rotation.rotationY(Mth.sin(time * 0.02f) * 0.9f);

        BoneState body = model.getBone("move_body");
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

        recoil(entity, model.getBone("move_recoilHeavy"), 0.9f, 24, partialTicks);
        recoil(entity, model.getBone("move_recoilLight"), 0.1f, 2, partialTicks);

        State st = state(entity);
        float parked = Mth.lerp(partialTicks, st.parkedO, st.parked);
        float open = TEST ? 1f : Mth.lerp(partialTicks, st.hatchO, st.hatch);
        Lean lean = LEANS.get(entity);
        float pitch = lean == null ? 0f : Mth.lerp(partialTicks, lean.pitchO, lean.pitch);
        float roll = lean == null ? 0f : Mth.lerp(partialTicks, lean.rollO, lean.roll);
        for (int i = 1; i <= 16; i++) {
            // stabiliser jacks wind up clear of the ground to drive and back down to park
            BoneState jack = model.getBone("move_jack" + i);
            if (jack != null) offset(jack, 0f, (1f - parked) * 0.4f, 0f);
            // wheel chocks are stowed while it is moving
            BoneState chock = model.getBone("move_chock" + i);
            if (chock != null) chock.visible = parked > 0.6f;
            // hatch lids swing up and back on their hinges
            BoneState hatch = model.getBone("move_hatch" + i);
            if (hatch != null) hatch.rotation.rotationX(-open * 1.75f);
            // aerials whip against the hull's movement and quiver while driving
            BoneState whip = model.getBone("move_sway" + i);
            if (whip != null) {
                float quiver = (float) Math.min(1.0, Math.abs(st.speed) * 3.0) * Mth.sin(time * 1.3f + i * 2.1f) * 0.035f;
                whip.rotation.rotationX(-pitch * 5f + quiver).rotateZ(-roll * 5f + quiver * 0.6f);
            }
        }
        // the Iskander's roof doors open as the missile starts to rise
        BoneState doorL = model.getBone("move_doorL"), doorR = model.getBone("move_doorR");
        if (doorL != null && doorR != null) {
            float raised = Math.abs(Mth.lerp(partialTicks, entity.getTurretXRotO(), entity.getTurretXRot()));
            float swing = (TEST ? 1f : Mth.clamp(raised / 6f, 0f, 1f)) * 1.3f;
            doorL.rotation.rotationZ(swing);
            doorR.rotation.rotationZ(-swing);
        }
        suspension(entity, model, st, partialTicks);
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
        offset(bone, 0f, 0f, back * travel);                 // the model faces -Z, so backwards is +Z
    }

    /** Slow per-vehicle state that is stepped once a tick and eased between ticks. */
    private static final class State {
        float parked = 1f, parkedO = 1f, hatch, hatchO;
        double speed;
        int still, tick = -1, sampled = -1;
        final float[] bump = new float[32], bumpO = new float[32];
    }

    private static final Map<VehicleEntity, State> STATES = new java.util.WeakHashMap<>();
    private static final Map<BoneState, float[]> REST = new java.util.WeakHashMap<>();
    private static final String[] WHEELS = new String[32];

    static {
        for (int i = 0; i < 16; i++) {
            WHEELS[i * 2] = "wheelL" + (i + 1);
            WHEELS[i * 2 + 1] = "wheelR" + (i + 1);
        }
    }

    private static State state(VehicleEntity entity) {
        State st = STATES.computeIfAbsent(entity, e -> new State());
        if (st.tick != entity.tickCount) {
            st.tick = entity.tickCount;
            st.speed = entity.getDeltaMovement().horizontalDistance();
            st.still = st.speed < 0.01 ? st.still + 1 : 0;
            st.parkedO = st.parked;
            st.hatchO = st.hatch;
            // parked after a second at rest; jacks take a couple of seconds to travel, hatches under a second
            st.parked = Mth.clamp(st.parked + (st.still > 20 ? 0.03f : -0.06f), 0f, 1f);
            boolean crewed = !entity.getPassengers().isEmpty();
            st.hatch = Mth.clamp(st.hatch + (crewed && st.still > 20 ? 0.08f : -0.12f), 0f, 1f);
        }
        return st;
    }

    /** Moves a bone from wherever it rests, in blocks. */
    private static void offset(BoneState bone, float dx, float dy, float dz) {
        float[] rest = REST.computeIfAbsent(bone, b -> new float[]{b.x, b.y, b.z});
        bone.x = rest[0] + dx * 16f;
        bone.y = rest[1] + dy * 16f;
        bone.z = rest[2] + dz * 16f;
    }

    /**
     * Wheeled vehicles: each wheel rides up over a bump and drops into a dip on its own. The ground height is
     * read under every wheel once a tick; the overall slope is taken out (Superb Warfare already tilts the whole
     * vehicle to that) so only the unevenness is left.
     */
    private static void suspension(VehicleEntity entity, VehicleModelInstance model, State st, float partialTicks) {
        if (model.getBone("trackMovL0") != null) return;       // tracks would come away from the wheels
        BoneState[] bones = new BoneState[WHEELS.length];
        int count = 0;
        for (int i = 0; i < WHEELS.length; i++) {
            BoneState b = model.getBone(WHEELS[i]);
            if (b == null) b = model.getBone(WHEELS[i] + "Turn");
            bones[i] = b;
            if (b != null) count++;
        }
        if (count < 4) return;
        if (st.sampled != entity.tickCount) {
            st.sampled = entity.tickCount;
            net.minecraft.world.phys.Vec3 fwd = entity.getViewVector(1f).multiply(1, 0, 1).normalize();
            double lx = fwd.z, lz = -fwd.x;                    // the vehicle's left
            float[] h = new float[WHEELS.length];
            double sz = 0, szz = 0, sh = 0, szh = 0, side = 0;
            for (int i = 0; i < WHEELS.length; i++) {
                if (bones[i] == null) continue;
                var def = bones[i].definition();
                double vx = def.pivotX(), vz = -def.pivotZ();  // model space faces -Z
                double wx = entity.getX() + lx * vx + fwd.x * vz, wz = entity.getZ() + lz * vx + fwd.z * vz;
                var from = new net.minecraft.world.phys.Vec3(wx, entity.getY() + 1.0, wz);
                var hit = entity.level().clip(new net.minecraft.world.level.ClipContext(from, from.add(0, -2.0, 0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
                h[i] = (float) (hit.getLocation().y - entity.getY());
                sz += vz; szz += vz * vz; sh += h[i]; szh += vz * h[i];
                side += (i % 2 == 0 ? 1 : -1) * h[i];
            }
            double slope = (count * szz - sz * sz) < 1e-6 ? 0 : (count * szh - sz * sh) / (count * szz - sz * sz);
            double mean = sh / count, tilt = side / count;
            for (int i = 0; i < WHEELS.length; i++) {
                st.bumpO[i] = st.bump[i];
                if (bones[i] == null) continue;
                double vz = -bones[i].definition().pivotZ();
                float want = Mth.clamp((float) (h[i] - mean - slope * (vz - sz / count) - (i % 2 == 0 ? tilt : -tilt)), -0.3f, 0.3f);
                st.bump[i] += (want - st.bump[i]) * 0.5f;
            }
        }
        for (int i = 0; i < WHEELS.length; i++) {
            if (bones[i] != null) offset(bones[i], 0f, Mth.lerp(partialTicks, st.bumpO[i], st.bump[i]), 0f);
        }
    }

    private VehicleAnim() {}
}
