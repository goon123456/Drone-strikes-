package com.steelrain.client;

import com.steelrain.ModRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Blocky models built in code. Model space: +Z is the nose, +Y is up, units are pixels (1/16 block).
 * Textures are 256x256 split into four flat-colour quadrants: 0 primary, 1 secondary, 2 accent, 3 dark.
 */
public final class Models {
    public static final ModelLayerLocation FPV = layer("fpv_drone");
    public static final ModelLayerLocation SHAHED = layer("shahed_136");
    public static final ModelLayerLocation REAPER = layer("mq9_reaper");
    public static final ModelLayerLocation TOMAHAWK = layer("tomahawk");
    public static final ModelLayerLocation ISKANDER = layer("iskander");
    public static final ModelLayerLocation GMLRS = layer("gmlrs");
    public static final ModelLayerLocation SHELL = layer("shell_155mm");
    public static final ModelLayerLocation HELLFIRE = layer("hellfire");
    public static final ModelLayerLocation INTERCEPTOR = layer("pantsir_missile");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, name), "main");
    }

    private static final class B {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        int n;

        B box(int q, float x, float y, float z, float w, float h, float d) {
            root.addOrReplaceChild("p" + n++, CubeListBuilder.create().texOffs((q & 1) * 128, (q >> 1) * 128)
                    .addBox(x, y, z, w, h, d), PartPose.ZERO);
            return this;
        }

        /** Box positioned relative to a pivot and rotated around it (radians). */
        B rot(int q, float px, float py, float pz, float rx, float ry, float rz,
              float x, float y, float z, float w, float h, float d) {
            root.addOrReplaceChild("p" + n++, CubeListBuilder.create().texOffs((q & 1) * 128, (q >> 1) * 128)
                    .addBox(x, y, z, w, h, d), PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
            return this;
        }

        LayerDefinition build() { return LayerDefinition.create(mesh, 256, 256); }
    }

    private static final float D45 = (float) (Math.PI / 4);

    public static LayerDefinition fpv() {
        B b = new B()
                .box(0, -2, -1, -3, 4, 2, 6)
                .box(3, -1.5f, 1, -2, 3, 1, 4)
                .box(2, -1, -0.5f, 3, 2, 2, 1)
                .box(2, -1, -2.5f, -2, 2, 1.5f, 7)
                .rot(3, 0, 0, 0, 0, D45, 0, -0.5f, -0.25f, -6.5f, 1, 0.5f, 13)
                .rot(3, 0, 0, 0, 0, -D45, 0, -0.5f, -0.25f, -6.5f, 1, 0.5f, 13);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                b.box(1, sx * 4.4f - 2, 0.5f, sz * 4.4f - 2, 4, 0.3f, 4);
                b.box(3, sx * 4.4f - 0.5f, -0.25f, sz * 4.4f - 0.5f, 1, 1, 1);
            }
        }
        return b.build();
    }

    public static LayerDefinition shahed() {
        return new B()
                .box(0, -1.5f, -1.5f, -10, 3, 3, 20)
                .box(0, -1, -1, 10, 2, 2, 2)
                .box(0, -12, -0.5f, -10, 24, 1, 5)
                .box(0, -8, -0.5f, -5, 16, 1, 5)
                .box(0, -4, -0.5f, 0, 8, 1, 5)
                .box(3, -12.5f, -2.5f, -10, 1, 5, 5)
                .box(3, 11.5f, -2.5f, -10, 1, 5, 5)
                .box(3, -1, -1, -11, 2, 2, 1)
                .box(1, -3.5f, -0.5f, -11.6f, 7, 1, 0.5f)
                .build();
    }

    public static LayerDefinition reaper() {
        return new B()
                .box(0, -2, -2, -16, 4, 4, 30)
                .box(0, -2.5f, -1, 12, 5, 4, 6)
                .box(0, -1.5f, -1.5f, 18, 3, 3, 2)
                .box(0, 2, 0, -2, 28, 1, 5)
                .box(0, -30, 0, -2, 28, 1, 5)
                .rot(0, 0, 1, -15, 0, 0, 0.7f, -0.5f, 0, -2, 1, 10, 4)
                .rot(0, 0, 1, -15, 0, 0, -0.7f, -0.5f, 0, -2, 1, 10, 4)
                .box(0, -0.5f, -6, -17, 1, 4, 3)
                .box(3, -4, -0.5f, -17.5f, 8, 1, 1)
                .box(3, -1.5f, -4, 10, 3, 2, 3)
                .box(2, 8, -1.5f, -2, 1.5f, 1.5f, 7)
                .box(2, -9.5f, -1.5f, -2, 1.5f, 1.5f, 7)
                .box(2, 14, -1.5f, -2, 1.5f, 1.5f, 7)
                .box(2, -15.5f, -1.5f, -2, 1.5f, 1.5f, 7)
                .build();
    }

    public static LayerDefinition tomahawk() {
        return new B()
                .box(0, -1.5f, -1.5f, -18, 3, 3, 36)
                .box(2, -1, -1, 18, 2, 2, 3)
                .box(3, -9, -0.25f, -2, 18, 0.5f, 4)
                .box(3, -4, -0.25f, -18, 8, 0.5f, 3)
                .box(3, -0.25f, -4, -18, 0.5f, 8, 3)
                .box(3, -1, -2.5f, -12, 2, 1, 5)
                .box(1, -1.6f, -1.6f, 12, 3.2f, 3.2f, 1)
                .build();
    }

    public static LayerDefinition iskander() {
        return new B()
                .box(0, -2.5f, -2.5f, -18, 5, 5, 32)
                .box(0, -2, -2, 14, 4, 4, 4)
                .box(2, -1.25f, -1.25f, 18, 2.5f, 2.5f, 4)
                .box(2, -0.5f, -0.5f, 22, 1, 1, 2)
                .box(3, -2.6f, -2.6f, -8, 5.2f, 5.2f, 2)
                .box(3, -5, -0.5f, -18, 10, 1, 4)
                .box(3, -0.5f, -5, -18, 1, 10, 4)
                .box(1, -1.5f, -1.5f, -19, 3, 3, 1)
                .build();
    }

    public static LayerDefinition gmlrs() {
        return new B()
                .box(0, -1, -1, -12, 2, 2, 24)
                .box(2, -0.6f, -0.6f, 12, 1.2f, 1.2f, 2)
                .box(3, -3, -0.25f, -12, 6, 0.5f, 3)
                .box(3, -0.25f, -3, -12, 0.5f, 6, 3)
                .build();
    }

    public static LayerDefinition shell() {
        return new B()
                .box(0, -1, -1, -3, 2, 2, 6)
                .box(2, -0.6f, -0.6f, 3, 1.2f, 1.2f, 2)
                .box(1, -1.1f, -1.1f, -2.5f, 2.2f, 2.2f, 1)
                .build();
    }

    public static LayerDefinition hellfire() {
        return new B()
                .box(0, -1, -1, -7, 2, 2, 14)
                .box(1, -0.8f, -0.8f, 7, 1.6f, 1.6f, 1.5f)
                .box(3, -2.5f, -0.25f, -7, 5, 0.5f, 3)
                .box(3, -0.25f, -2.5f, -7, 0.5f, 5, 3)
                .box(3, -2, -0.25f, 1, 4, 0.5f, 2)
                .box(3, -0.25f, -2, 1, 0.5f, 4, 2)
                .build();
    }

    public static LayerDefinition interceptor() {
        return new B()
                .box(0, -1.25f, -1.25f, -12, 2.5f, 2.5f, 10)
                .box(1, -0.75f, -0.75f, -2, 1.5f, 1.5f, 14)
                .box(3, -3, -0.25f, -12, 6, 0.5f, 3)
                .box(3, -0.25f, -3, -12, 0.5f, 6, 3)
                .box(3, -1.75f, -0.25f, -2, 3.5f, 0.5f, 2)
                .box(3, -0.25f, -1.75f, -2, 0.5f, 3.5f, 2)
                .build();
    }

    private Models() {}
}
