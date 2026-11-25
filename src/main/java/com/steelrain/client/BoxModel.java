package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny model format for full-size vehicles: coloured boxes, measured in blocks, arranged in a tree of groups that
 * can be rotated around a pivot (turrets, barrels, tail fins). All boxes share one tiling "paint" texture and are
 * tinted per vertex, so a model can be any size without worrying about texture layout.
 */
public final class BoxModel {
    public static final class Group {
        final String name;
        final float px, py, pz;          // pivot relative to the parent's pivot
        float tiltX, tiltY, tiltZ;       // fixed rotation (radians)
        /** Animated rotation in radians, set before rendering. Applied Y, then X, then Z. */
        public float xRot, yRot, zRot;
        public boolean visible = true;
        final List<float[]> boxes = new ArrayList<>();
        final List<Group> children = new ArrayList<>();
        final float ax, ay, az;          // pivot in model space

        Group(String name, float px, float py, float pz, float ax, float ay, float az) {
            this.name = name;
            this.px = px; this.py = py; this.pz = pz;
            this.ax = ax; this.ay = ay; this.az = az;
        }
    }

    private final Group root = new Group("root", 0, 0, 0, 0, 0, 0);
    private final Map<String, Group> groups = new HashMap<>();
    private Group current = root;

    public BoxModel() {
        groups.put("root", root);
    }

    /** Starts a group that pivots around the given point (model space). Later boxes go into it. */
    public BoxModel group(String name, String parent, double x, double y, double z) {
        Group p = groups.get(parent);
        Group g = new Group(name, (float) x - p.ax, (float) y - p.ay, (float) z - p.az, (float) x, (float) y, (float) z);
        p.children.add(g);
        groups.put(name, g);
        current = g;
        return this;
    }

    /** Fixed rotation of the current group, in radians. */
    public BoxModel tilt(double x, double y, double z) {
        current.tiltX = (float) x; current.tiltY = (float) y; current.tiltZ = (float) z;
        return this;
    }

    public BoxModel in(String name) {
        current = groups.get(name);
        return this;
    }

    /** Box from a corner and a size, in model space, added to the current group. */
    public BoxModel box(int rgb, double x, double y, double z, double w, double h, double d) {
        current.boxes.add(new float[]{
                (float) x - current.ax, (float) y - current.ay, (float) z - current.az,
                (float) (x + w) - current.ax, (float) (y + h) - current.ay, (float) (z + d) - current.az,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f});
        return this;
    }

    /** Same box mirrored left and right. */
    public BoxModel pair(int rgb, double x, double y, double z, double w, double h, double d) {
        box(rgb, x, y, z, w, h, d);
        return box(rgb, -x - w, y, z, w, h, d);
    }

    public Group get(String name) { return groups.get(name); }

    public void render(PoseStack pose, VertexConsumer vc, int light) {
        render(root, pose, vc, light);
    }

    private void render(Group g, PoseStack pose, VertexConsumer vc, int light) {
        if (!g.visible) return;
        pose.pushPose();
        pose.translate(g.px, g.py, g.pz);
        float ry = g.tiltY + g.yRot, rx = g.tiltX + g.xRot, rz = g.tiltZ + g.zRot;
        if (ry != 0) pose.mulPose(Axis.YP.rotation(ry));
        if (rx != 0) pose.mulPose(Axis.XP.rotation(rx));
        if (rz != 0) pose.mulPose(Axis.ZP.rotation(rz));
        PoseStack.Pose p = pose.last();
        for (float[] b : g.boxes) emit(p, vc, light, b);
        for (Group c : g.children) render(c, pose, vc, light);
        pose.popPose();
    }

    private static void emit(PoseStack.Pose p, VertexConsumer vc, int light, float[] b) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        float r = b[6], g = b[7], bl = b[8];
        Matrix4f m = p.pose();
        // -Y, +Y, -Z, +Z, -X, +X ; texture coordinates follow the box size so the paint grain keeps its scale
        quad(p, m, vc, light, r, g, bl, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x1 - x0, z1 - z0);
        quad(p, m, vc, light, r, g, bl, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, x1 - x0, z1 - z0);
        quad(p, m, vc, light, r, g, bl, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1 - x0, y1 - y0);
        quad(p, m, vc, light, r, g, bl, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x1 - x0, y1 - y0);
        quad(p, m, vc, light, r, g, bl, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, z1 - z0, y1 - y0);
        quad(p, m, vc, light, r, g, bl, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, z1 - z0, y1 - y0);
    }

    private static void quad(PoseStack.Pose p, Matrix4f m, VertexConsumer vc, int light, float r, float g, float b,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float u, float v) {
        vertex(p, m, vc, light, r, g, b, nx, ny, nz, ax, ay, az, 0, v);
        vertex(p, m, vc, light, r, g, b, nx, ny, nz, bx, by, bz, u, v);
        vertex(p, m, vc, light, r, g, b, nx, ny, nz, cx, cy, cz, u, 0);
        vertex(p, m, vc, light, r, g, b, nx, ny, nz, dx, dy, dz, 0, 0);
    }

    private static void vertex(PoseStack.Pose p, Matrix4f m, VertexConsumer vc, int light, float r, float g, float b,
                               float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, 1f).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
