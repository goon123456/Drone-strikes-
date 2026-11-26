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
 * Small model format for full-size vehicles and aircraft, measured in blocks. Solids are boxes, tapered and
 * eight-sided sections (fuselages, noses, wheels) and swept panels (wings, fins), arranged in a tree of groups
 * that can be rotated around a pivot (turrets, barrels, propellers, landing gear). Everything shares one tiling
 * "paint" texture and is tinted per vertex, so a model can be any size without worrying about texture layout.
 */
public final class BoxModel {
    public static final class Group {
        final String name;
        final float px, py, pz;          // pivot relative to the parent's pivot
        float tiltX, tiltY, tiltZ;       // fixed rotation (radians)
        /** Animated rotation in radians, set before rendering. Applied Y, then X, then Z. */
        public float xRot, yRot, zRot;
        public boolean visible = true;
        /** Each quad: 4 corners (12), normal (3), colour (3), texture size (2). */
        final List<float[]> quads = new ArrayList<>();
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

    /** Starts a group that pivots around the given point (model space). Later solids go into it. */
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

    public Group get(String name) { return groups.get(name); }

    public boolean has(String name) { return groups.containsKey(name); }

    // ------------------------------------------------------------------ solids

    /** Box from a corner and a size, in model space, added to the current group. */
    public BoxModel box(int rgb, double x, double y, double z, double w, double h, double d) {
        return hex(rgb, new double[][]{
                {x, y, z}, {x + w, y, z}, {x + w, y + h, z}, {x, y + h, z},
                {x, y, z + d}, {x + w, y, z + d}, {x + w, y + h, z + d}, {x, y + h, z + d}});
    }

    /** Same box mirrored left and right. */
    public BoxModel pair(int rgb, double x, double y, double z, double w, double h, double d) {
        box(rgb, x, y, z, w, h, d);
        return box(rgb, -x - w, y, z, w, h, d);
    }

    /**
     * Any six-sided solid: corners 0-3 go around one end, 4-7 around the other in the same order, so corner
     * {@code i} is joined to corner {@code i + 4}.
     */
    public BoxModel hex(int rgb, double[][] p) {
        double cx = 0, cy = 0, cz = 0;
        for (double[] c : p) { cx += c[0] / 8; cy += c[1] / 8; cz += c[2] / 8; }
        double[] centre = {cx, cy, cz};
        quad(rgb, centre, p[0], p[1], p[2], p[3]);
        quad(rgb, centre, p[4], p[5], p[6], p[7]);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quad(rgb, centre, p[i], p[j], p[j + 4], p[i + 4]);
        }
        return this;
    }

    /** Four-sided section running along Z, with a different centre and size at each end. */
    public BoxModel loft(int rgb, double z0, double cx0, double cy0, double w0, double h0,
                         double z1, double cx1, double cy1, double w1, double h1) {
        return hex(rgb, new double[][]{
                {cx0 - w0 / 2, cy0 - h0 / 2, z0}, {cx0 + w0 / 2, cy0 - h0 / 2, z0}, {cx0 + w0 / 2, cy0 + h0 / 2, z0}, {cx0 - w0 / 2, cy0 + h0 / 2, z0},
                {cx1 - w1 / 2, cy1 - h1 / 2, z1}, {cx1 + w1 / 2, cy1 - h1 / 2, z1}, {cx1 + w1 / 2, cy1 + h1 / 2, z1}, {cx1 - w1 / 2, cy1 + h1 / 2, z1}});
    }

    /** Eight-sided section running along Z: the rounded version of {@link #loft}, for fuselages and missiles. */
    public BoxModel tube(int rgb, double z0, double cx0, double cy0, double w0, double h0,
                         double z1, double cx1, double cy1, double w1, double h1) {
        return prism(rgb, 'z', z0, cx0, cy0, w0, h0, z1, cx1, cy1, w1, h1);
    }

    /** Round tube of one diameter along Z, centred on the given axis. */
    public BoxModel tube(int rgb, double cx, double cy, double z0, double z1, double d) {
        return prism(rgb, 'z', z0, cx, cy, d, d, z1, cx, cy, d, d);
    }

    /** Eight-sided section standing upright: radomes, sensor balls, drums, masts. */
    public BoxModel drum(int rgb, double cx, double cz, double y0, double d0, double y1, double d1) {
        return prism(rgb, 'y', y0, cx, cz, d0, d0, y1, cx, cz, d1, d1);
    }

    /** Eight-sided section lying across the model: a wheel or roller centred at (y, z), from x0 to x1. */
    public BoxModel wheel(int rgb, double x0, double x1, double y, double z, double d) {
        return prism(rgb, 'x', x0, z, y, d, d, x1, z, y, d, d);
    }

    /** The same wheel on both sides. */
    public BoxModel wheels(int rgb, double x0, double x1, double y, double z, double d) {
        wheel(rgb, x0, x1, y, z, d);
        return wheel(rgb, -x1, -x0, y, z, d);
    }

    private BoxModel prism(int rgb, char axis, double a0, double u0, double v0, double w0, double h0,
                           double a1, double u1, double v1, double w1, double h1) {
        // An octagon is drawn as a ring of eight sides plus end caps.
        double[][] e0 = ring(axis, a0, u0, v0, w0, h0), e1 = ring(axis, a1, u1, v1, w1, h1);
        double[] centre = point(axis, (a0 + a1) / 2, (u0 + u1) / 2, (v0 + v1) / 2);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            quad(rgb, centre, e0[i], e0[j], e1[j], e1[i]);
        }
        for (double[][] e : new double[][][]{e0, e1}) {
            quad(rgb, centre, e[0], e[1], e[2], e[3]);
            quad(rgb, centre, e[0], e[3], e[4], e[7]);
            quad(rgb, centre, e[4], e[5], e[6], e[7]);
        }
        return this;
    }

    private static double[][] ring(char axis, double a, double u, double v, double w, double h) {
        double k = 0.2929;   // how much of each side the corner cut takes: this makes a regular octagon
        double x0 = u - w / 2, x1 = u + w / 2, y0 = v - h / 2, y1 = v + h / 2, cw = w * k, ch = h * k;
        double[][] uv = {{x0 + cw, y0}, {x1 - cw, y0}, {x1, y0 + ch}, {x1, y1 - ch}, {x1 - cw, y1}, {x0 + cw, y1}, {x0, y1 - ch}, {x0, y0 + ch}};
        double[][] out = new double[8][];
        for (int i = 0; i < 8; i++) out[i] = point(axis, a, uv[i][0], uv[i][1]);
        return out;
    }

    private static double[] point(char axis, double a, double u, double v) {
        return switch (axis) {
            case 'x' -> new double[]{a, v, u};
            case 'y' -> new double[]{u, a, v};
            default -> new double[]{u, v, a};
        };
    }

    /**
     * A wing or tailplane panel from a root to a tip. Each end is given by its sideways position, height, where
     * its leading edge is along Z, its chord (front to back) and its thickness.
     */
    public BoxModel wing(int rgb, double x0, double y0, double lead0, double chord0, double t0,
                         double x1, double y1, double lead1, double chord1, double t1) {
        // The thickness is measured square to the panel, so steep panels (V tails) keep their thickness.
        double dx = x1 - x0, dy = y1 - y0, len = Math.max(1e-6, Math.hypot(dx, dy));
        double nx = -dy / len, ny = dx / len;
        return hex(rgb, new double[][]{
                {x0 - nx * t0 / 2, y0 - ny * t0 / 2, lead0}, {x0 + nx * t0 / 2, y0 + ny * t0 / 2, lead0},
                {x0 + nx * t0 / 2, y0 + ny * t0 / 2, lead0 - chord0}, {x0 - nx * t0 / 2, y0 - ny * t0 / 2, lead0 - chord0},
                {x1 - nx * t1 / 2, y1 - ny * t1 / 2, lead1}, {x1 + nx * t1 / 2, y1 + ny * t1 / 2, lead1},
                {x1 + nx * t1 / 2, y1 + ny * t1 / 2, lead1 - chord1}, {x1 - nx * t1 / 2, y1 - ny * t1 / 2, lead1 - chord1}});
    }

    /** The same panel on both sides. */
    public BoxModel wings(int rgb, double x0, double y0, double lead0, double chord0, double t0,
                          double x1, double y1, double lead1, double chord1, double t1) {
        wing(rgb, x0, y0, lead0, chord0, t0, x1, y1, lead1, chord1, t1);
        return wing(rgb, -x0, y0, lead0, chord0, t0, -x1, y1, lead1, chord1, t1);
    }

    private void quad(int rgb, double[] centre, double[] a, double[] b, double[] c, double[] d) {
        // Face normal from the diagonals, turned to point away from the middle of the solid.
        double ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
        double vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9) return;   // an end that tapers to nothing has no face
        nx /= len; ny /= len; nz /= len;
        double mx = (a[0] + b[0] + c[0] + d[0]) / 4 - centre[0], my = (a[1] + b[1] + c[1] + d[1]) / 4 - centre[1],
                mz = (a[2] + b[2] + c[2] + d[2]) / 4 - centre[2];
        if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        Group g = current;
        current.quads.add(new float[]{
                (float) a[0] - g.ax, (float) a[1] - g.ay, (float) a[2] - g.az,
                (float) b[0] - g.ax, (float) b[1] - g.ay, (float) b[2] - g.az,
                (float) c[0] - g.ax, (float) c[1] - g.ay, (float) c[2] - g.az,
                (float) d[0] - g.ax, (float) d[1] - g.ay, (float) d[2] - g.az,
                (float) nx, (float) ny, (float) nz,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                (float) dist(a, b), (float) dist(a, d)});
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    // ------------------------------------------------------------------ drawing

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
        Matrix4f m = p.pose();
        for (float[] q : g.quads) {
            // texture coordinates follow the face size so the paint grain keeps its scale
            vertex(p, m, vc, light, q, 0, 0, q[19]);
            vertex(p, m, vc, light, q, 3, q[18], q[19]);
            vertex(p, m, vc, light, q, 6, q[18], 0);
            vertex(p, m, vc, light, q, 9, 0, 0);
        }
        for (Group c : g.children) render(c, pose, vc, light);
        pose.popPose();
    }

    private static void vertex(PoseStack.Pose p, Matrix4f m, VertexConsumer vc, int light, float[] q, int i, float u, float v) {
        vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(q[15], q[16], q[17], 1f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, q[12], q[13], q[14]);
    }

    /**
     * Every face in model space with the fixed tilts applied, as 12 corner coordinates, a normal and a colour.
     * Used by the offline preview tool, not by the game.
     */
    public List<float[]> flatten() {
        List<float[]> out = new ArrayList<>();
        flatten(root, new Matrix4f(), out);
        return out;
    }

    private void flatten(Group g, Matrix4f parent, List<float[]> out) {
        if (!g.visible) return;
        Matrix4f m = new Matrix4f(parent).translate(g.px, g.py, g.pz)
                .rotateY(g.tiltY + g.yRot).rotateX(g.tiltX + g.xRot).rotateZ(g.tiltZ + g.zRot);
        for (float[] q : g.quads) {
            float[] o = new float[18];
            for (int i = 0; i < 12; i += 3) {
                org.joml.Vector3f v = m.transformPosition(q[i], q[i + 1], q[i + 2], new org.joml.Vector3f());
                o[i] = v.x; o[i + 1] = v.y; o[i + 2] = v.z;
            }
            org.joml.Vector3f n = m.transformDirection(q[12], q[13], q[14], new org.joml.Vector3f());
            o[12] = n.x; o[13] = n.y; o[14] = n.z;
            o[15] = q[15]; o[16] = q[16]; o[17] = q[17];
            out.add(o);
        }
        for (Group c : g.children) flatten(c, m, out);
    }
}
