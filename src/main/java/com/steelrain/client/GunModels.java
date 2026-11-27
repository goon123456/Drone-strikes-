package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.gun.Attachment;
import com.steelrain.gun.Caliber;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The gun models and how a particular gun is put together from them: which moving parts are where, whether the
 * magazine is in, and the sights and muzzle devices fitted to it. Gun space: the bore along +z, +x the firer's left.
 */
public final class GunModels {
    private static final Map<GunType, BoxModel> GUNS = new EnumMap<>(GunType.class);
    private static final Map<Attachment, BoxModel> PARTS = new EnumMap<>(Attachment.class);
    private static final Map<Caliber, BoxModel> AMMO = new EnumMap<>(Caliber.class);

    public static BoxModel gun(GunType t) { return GUNS.computeIfAbsent(t, k -> BoxModel.load("gun", k.id)); }
    public static BoxModel part(Attachment a) { return PARTS.computeIfAbsent(a, k -> BoxModel.load("gun", k.id)); }
    public static BoxModel ammo(Caliber c) { return AMMO.computeIfAbsent(c, k -> BoxModel.load("gun", k.ammoId)); }

    /** How the moving parts stand for one drawing of a gun. */
    public static final class Pose {
        /** 0 forward to 1 fully back. */
        public float bolt, charge;
        public float trigger;
        /** Magazine moved out of the well (m, gun space) and rocked about its lip (radians). */
        public float magX, magY, magZ, magRock;
        /** Null: as the stack says. Otherwise forces the magazine (or the shell in the hand) shown or hidden. */
        public Boolean magShown;
        public Boolean beltShown;
        /** For the launcher: which kind of rocket is in the hand while {@link #magShown} is forced, or -1. */
        public int loadKind = -1;
        /** The belt lifted off the feed tray and let down towards its box (m, negative is down). */
        public float beltY;
        /** Machine-gun feed cover, 0 shut to 1 open. */
        public float cover;
        /** Bipod legs, 0 folded to 1 down. */
        public float bipod;
        public boolean flash;
    }

    public static final Pose REST = new Pose();

    /** Where on the gun a fitting goes. */
    public static Vector3f mount(BoxModel gun, Attachment a) {
        return gun.point(switch (a.slot) { case OPTIC -> "optic"; case MUZZLE -> "thread"; case UNDER -> "under"; });
    }

    /** The point the eye lines up behind: through the optic if one is fitted, else over the iron sights. */
    public static Vector3f sight(ItemStack stack, GunType type) {
        BoxModel gun = gun(type);
        Attachment optic = GunItem.part(stack, Attachment.Slot.OPTIC);
        if (optic == null) return new Vector3f(gun.point("sight"));
        return new Vector3f(mount(gun, optic)).add(part(optic).point("eye"));
    }

    /** The muzzle, allowing for a suppressor. */
    public static Vector3f muzzle(ItemStack stack, GunType type) {
        BoxModel gun = gun(type);
        Attachment dev = GunItem.part(stack, Attachment.Slot.MUZZLE);
        if (dev == null) return new Vector3f(gun.point("muzzle"));
        return new Vector3f(gun.point("thread")).add(part(dev).point("muzzle"));
    }

    private static void set(BoxModel m, String bone, java.util.function.Consumer<BoxModel.Group> f) {
        if (m.has(bone)) f.accept(m.get(bone));
    }

    /** Draws a gun as its stack describes it, with its moving parts posed. */
    public static void render(ItemStack stack, GunType type, Pose p, PoseStack pose, MultiBufferSource buffers, int light) {
        BoxModel m = gun(type);
        boolean mag = GunItem.hasMagazine(stack);
        int rounds = GunItem.rounds(stack);
        float travel = type.boltTravel();
        // a slide or bolt stays back on an empty magazine
        boolean lockedBack = (type == GunType.M17 || type == GunType.M4A1) && mag && rounds == 0 && !GunItem.chambered(stack);
        float bolt = Math.max(p.bolt, lockedBack ? 1f : 0f);
        set(m, "bolt", g -> { g.oz = -travel * bolt; });
        set(m, "charge", g -> g.oz = -0.06f * p.charge);
        set(m, "trigger", g -> g.xRot = 0.35f * p.trigger);
        int mode = GunItem.mode(stack);
        set(m, "selector", g -> {
            g.xRot = 0; g.oz = 0;
            switch (type) {
                case AK74M, SVD -> g.xRot = mode == GunType.Mode.SAFE ? 0f : mode == GunType.Mode.AUTO ? 0.16f : 0.30f;
                case M4A1, PKM, M107 -> g.xRot = mode == GunType.Mode.SAFE ? 0f : mode == GunType.Mode.SEMI ? -1.57f : -3.14f;
                case M17 -> g.xRot = mode == GunType.Mode.SAFE ? -0.3f : 0f;
                case M590 -> g.oz = mode == GunType.Mode.SAFE ? 0f : 0.008f;
            }
        });
        boolean pump = type.action == GunType.Action.PUMP;
        boolean single = type.action == GunType.Action.SINGLE;
        // a launcher shows the rocket that is in it (or on its way in): the anti-tank one, or the slim fragmentation one
        int kind = p.magShown != null && p.loadKind >= 0 ? p.loadKind : stack.getOrDefault(com.steelrain.ModRegistry.LOAD.get(), 0);
        boolean seated = p.magShown != null ? p.magShown : (single ? rounds > 0 : !pump && mag);
        set(m, "mag", g -> {
            g.visible = seated && (!single || kind == 0);
            g.ox = p.magX; g.oy = p.magY; g.oz = p.magZ; g.xRot = p.magRock;
        });
        set(m, "frag", g -> {
            g.visible = seated && single && kind == 1;
            g.ox = p.magX; g.oy = p.magY; g.oz = p.magZ; g.xRot = p.magRock;
        });
        set(m, "belt", g -> {
            g.visible = p.beltShown != null ? p.beltShown : (mag && rounds > 0);
            g.ox = p.magX; g.oy = p.magY + p.beltY; g.oz = p.magZ;
        });
        set(m, "cover", g -> g.xRot = 1.15f * p.cover);
        set(m, "bipod", g -> g.xRot = 1.75f * p.bipod);
        Attachment optic = GunItem.part(stack, Attachment.Slot.OPTIC);
        set(m, "irons", g -> g.xRot = optic != null ? 1.5f : 0f);
        set(m, "muzzle", g -> g.visible = GunItem.part(stack, Attachment.Slot.MUZZLE) == null);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture()));
        m.render(pose, vc, light);
        for (Attachment a : GunItem.parts(stack)) {
            BoxModel am = part(a);
            Vector3f at = mount(m, a);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            am.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(am.texture())), light);
            pose.popPose();
        }
    }

    /** Just the magazine of a gun, with its lip at the origin. */
    public static void renderMagazine(GunType type, PoseStack pose, MultiBufferSource buffers, int light) {
        BoxModel m = gun(type);
        if (!m.has("mag")) return;
        BoxModel.Group g = m.get("mag");
        g.ox = g.oy = g.oz = 0; g.xRot = 0;
        Vector3f pv = BoxModel.pivotOf(g);
        pose.pushPose();
        pose.translate(-pv.x, -pv.y, -pv.z);
        m.renderGroup("mag", pose, buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture())), light);
        if (m.has("belt")) {
            BoxModel.Group b = m.get("belt");
            b.ox = b.oy = b.oz = 0;
            m.renderGroup("belt", pose, buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture())), light);
        }
        pose.popPose();
    }

    /** Turns gun space (bore along +z) to lie across the view, muzzle to the right. */
    public static void faceSideways(PoseStack pose) { pose.mulPose(Axis.YP.rotationDegrees(90)); }

    private GunModels() {}
}
