package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steelrain.ModRegistry;
import com.steelrain.gun.Attachment;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The firer's own view of the gun: held in both hands at the shoulder, brought up so the eye looks along the
 * sights, kicking with each shot, swinging behind the view as it turns, lowered at a run, and taken through the
 * motions of a reload with the left hand carrying the magazine.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class GunHand {
    private static float lagYaw, lagPitch, reloadEnv;
    private static long lastFrame;
    private static final GunModels.Pose POSE = new GunModels.Pose();

    private static float ramp(float p, float a, float b) {
        float t = Mth.clamp((p - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        GunType type = GunClient.held();
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (type == null || player == null) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || GunClient.scoped()) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        draw(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), player, stack, type, e.getPartialTick());
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack, GunType type, float pt) {
        long now = Util.getMillis();
        float dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        BoxModel model = GunModels.gun(type);
        float a = GunClient.ads();
        boolean pistol = type.pistol();
        Attachment optic = GunItem.part(stack, Attachment.Slot.OPTIC);
        Attachment under = GunItem.part(stack, Attachment.Slot.UNDER);

        // ---- the action in hand: where the magazine, bolt and left hand are
        GunModels.Pose bp = POSE;
        bp.bolt = bp.charge = bp.cover = 0; bp.magX = bp.magY = bp.magZ = bp.magRock = 0; bp.magShown = null; bp.beltShown = null; bp.beltY = 0; bp.loadKind = -1;
        bp.trigger = GunClient.triggerHeld ? 1 : 0;
        bp.bipod = GunClient.bipod;
        float sinceShot = now - GunClient.lastShot;
        if (type.action == GunType.Action.PUMP) {
            if (sinceShot > 140 && sinceShot < 600) bp.bolt = Mth.sin((float) Math.PI * (sinceShot - 140) / 460f);
        } else {
            float cycle = (float) Math.min(type.interval() * 50, 95);
            if (sinceShot < cycle) bp.bolt = Mth.sin((float) Math.PI * sinceShot / cycle);
        }
        float p = GunClient.animProgress();
        int anim = GunClient.anim;
        boolean reloading = p >= 0 && anim != GunClient.ANIM_PUMP;
        reloadEnv += ((reloading ? 1f : 0f) - reloadEnv) * (1 - (float) Math.exp(-dt / 85.0));
        Vector3f rest = new Vector3f(model.point("hand"));
        if (under != null) rest = new Vector3f(model.point("under")).add(GunModels.part(under).point("hand"));
        Vector3f left = new Vector3f(rest);
        Vector3f magAt = new Vector3f(model.point("mag")).add(0, pistol ? -0.06f : -0.10f, 0);
        if (p >= 0 && (anim == GunClient.ANIM_RELOAD || anim == GunClient.ANIM_RELOAD_EMPTY)) {
            boolean empty = anim == GunClient.ANIM_RELOAD_EMPTY;
            float out = type.magOutAt(), in = type.magInAt(empty), rack = type.boltAt();
            // the magazine: unlatched and drawn down, carried away, a fresh one brought up and seated
            float down;
            if (p < out + 0.20f) down = 0.11f * ramp(p, out, out + 0.07f) + 0.42f * ramp(p, out + 0.07f, out + 0.20f);
            else down = 0.53f - 0.42f * ramp(p, in - 0.18f, in - 0.05f) - 0.11f * ramp(p, in - 0.05f, in);
            boolean away = p > out + 0.20f && p < in - 0.18f;
            bp.magShown = !away;
            bp.magY = -down;
            bp.magZ = -0.05f * Mth.clamp(down / 0.2f, 0, 1);
            bp.magX = 0.05f * Mth.clamp((down - 0.11f) / 0.3f, 0, 1);
            if (type.rocksMagazine()) bp.magRock = 0.30f * Mth.clamp(down / 0.11f, 0, 1) * (down < 0.2f ? 1 : 0.5f);
            float onMag = ramp(p, out - 0.12f, out) * (1 - ramp(p, in + 0.03f, in + 0.14f));
            Vector3f carried = new Vector3f(magAt).add(bp.magX, bp.magY, bp.magZ);
            if (type.beltFed()) {
                // belt-fed: the feed cover is lifted, what is left of the old belt is taken off the tray and goes
                // away with its box, the new box is hung, its belt drawn up and laid in the tray, the cover is
                // shut and the gun is cocked
                boolean oldBox = p <= out + 0.20f;
                bp.cover = ramp(p, 0.06f, 0.16f) * (1 - ramp(p, 0.76f, 0.84f));
                bp.beltShown = oldBox ? GunClient.animHadRounds : !away;
                bp.beltY = oldBox ? -0.045f * ramp(p, 0.19f, 0.26f) : -0.075f * (1 - ramp(p, 0.65f, 0.72f));
                Vector3f lid = new Vector3f(model.point("cover")).add(0, 0.07f * bp.cover, 0.02f * bp.cover);
                Vector3f belt = new Vector3f(model.point("belt")).add(bp.magX, bp.magY + bp.beltY, bp.magZ);
                left.lerp(lid, ramp(p, 0.02f, 0.06f) * (1 - ramp(p, 0.16f, 0.19f)));
                if (GunClient.animHadRounds) left.lerp(belt, ramp(p, 0.16f, 0.19f) * (1 - ramp(p, 0.26f, 0.29f)));
                left.lerp(carried, ramp(p, 0.26f, 0.30f) * (1 - ramp(p, in + 0.005f, in + 0.03f)));
                left.lerp(belt, ramp(p, in + 0.005f, in + 0.03f) * (1 - ramp(p, 0.725f, 0.755f)));
                left.lerp(lid, ramp(p, 0.725f, 0.755f) * (1 - ramp(p, 0.84f, 0.865f)));
                float onBolt = ramp(p, 0.84f, 0.865f) * (1 - ramp(p, rack + 0.07f, 0.98f));
                bp.bolt = Math.max(bp.bolt, ramp(p, rack - 0.015f, rack + 0.03f) * (1 - ramp(p, rack + 0.03f, rack + 0.06f)));
                left.lerp(new Vector3f(model.point("charge")).add(0, -0.02f, -type.boltTravel() * bp.bolt), onBolt);
            } else {
                left.lerp(carried, onMag);
                if (empty && type.handRacked()) {
                    float onBolt = ramp(p, rack - 0.10f, rack - 0.02f) * (1 - ramp(p, rack + 0.07f, 0.98f));
                    bp.bolt = Math.max(bp.bolt, ramp(p, rack - 0.02f, rack + 0.03f) * (1 - ramp(p, rack + 0.03f, rack + 0.06f)));
                    Vector3f handle = new Vector3f(model.point("charge")).add(0, -0.02f, -type.boltTravel() * bp.bolt);
                    left.lerp(handle, onBolt);
                }
            }
        } else if (p >= 0 && anim == GunClient.ANIM_SHELL && type.action == GunType.Action.SINGLE) {
            // a rocket brought up in front of the muzzle, lined up and pushed back into the tube until it latches
            bp.magShown = true;
            bp.loadKind = GunClient.animLoad;
            float up = ramp(p, 0.12f, 0.55f), in = ramp(p, 0.60f, 0.82f);
            bp.magY = -0.34f * (1 - up);
            bp.magX = 0.10f * (1 - up);
            bp.magZ = 0.20f * (1 - in) - 0.10f * (1 - up);
            float onRocket = ramp(p, 0.0f, 0.12f) * (1 - ramp(p, 0.84f, 0.97f));
            left.lerp(new Vector3f(model.point("mag")).add(bp.magX, bp.magY - 0.03f, bp.magZ + 0.10f), onRocket);
        } else if (p >= 0 && anim == GunClient.ANIM_SHELL) {
            // a shell brought up from the belt to the loading port and thumbed forward into the tube
            bp.magShown = p < 0.82f;
            float up = ramp(p, 0.05f, 0.55f), pushed = ramp(p, 0.55f, 0.82f);
            bp.magY = -0.24f * (1 - up);
            bp.magZ = -0.08f * (1 - up) + 0.055f * pushed;
            bp.magX = 0.04f * (1 - up);
            left.set(new Vector3f(model.point("mag")).add(bp.magX, bp.magY - 0.035f, bp.magZ + 0.02f));
        } else if (p >= 0 && anim == GunClient.ANIM_PUMP) {
            bp.bolt = Math.max(bp.bolt, Mth.sin((float) Math.PI * p));
        }
        if (type.action == GunType.Action.PUMP && !(p >= 0 && anim == GunClient.ANIM_SHELL)) left.add(0, 0, -type.boltTravel() * bp.bolt);

        // ---- where the gun is: at the shoulder, or up so the eye looks along the sights
        Vector3f sight = GunModels.sight(stack, type);
        // the eye sits over the stock, never behind the butt
        float butt = model.bounds()[2];
        float relief = pistol ? 0.46f : optic != null ? 0.14f : type == GunType.RPG7 ? 0.22f : Mth.clamp(sight.z - (butt + 0.09f), 0.15f, 0.33f);
        Vector3f hip = pistol ? new Vector3f(0.105f, -0.115f, -0.44f) : type == GunType.RPG7 ? new Vector3f(0.135f, -0.085f, -0.30f)
                : type == GunType.M107 ? new Vector3f(0.12f, -0.14f, -0.40f) : new Vector3f(0.115f, -0.128f, -0.34f);
        Vector3f aim = new Vector3f(sight.x, -sight.y, sight.z - relief);
        Vector3f at = new Vector3f(hip).lerp(aim, a);
        float yaw = -4f * (1 - a), pitch = 0, roll = 3f * (1 - a);

        // walking makes it bob, less so on aim
        float walk = player.walkDist + (player.walkDist - player.walkDistO) * pt;
        boolean calm = ClientConfig.calm();
        float stride = calm ? 0 : Mth.clamp((float) player.getDeltaMovement().horizontalDistance() * 6f, 0, 1) * (1 - 0.85f * a);
        at.add(Mth.sin(walk * (float) Math.PI) * 0.006f * stride, -Math.abs(Mth.cos(walk * (float) Math.PI)) * 0.007f * stride, 0);
        // it trails a little behind the view as the firer turns
        float vy = player.getViewYRot(pt), vx = player.getViewXRot(pt);
        float f = 1 - (float) Math.exp(-dt / 75.0);
        lagYaw += Mth.wrapDegrees(vy - lagYaw) * f;
        lagPitch += (vx - lagPitch) * f;
        float trail = 1 - 0.75f * a;
        yaw += Mth.clamp(Mth.wrapDegrees(vy - lagYaw), -9, 9) * 0.32f * trail;
        pitch += Mth.clamp(vx - lagPitch, -9, 9) * 0.32f * trail;
        // lowered and turned across the body at a run
        float run = GunClient.sprint;
        if (pistol) { pitch -= 38f * run; at.add(-0.02f * run, -0.02f * run, 0.06f * run); }
        else { yaw += 36f * run; pitch += 10f * run; at.add(-0.03f * run, 0.0f, 0.0f); }
        // just drawn: it comes up from below
        float drawn = 1 - ramp((now - GunClient.drawnAt) / 360f, 0, 1);
        at.add(0, -0.30f * drawn, 0);
        pitch += 32f * drawn;
        // the kick: back into the shoulder, muzzle up
        float j = GunClient.jolt;
        float heavy = pistol ? 2.6f : type == GunType.M590 ? 2.2f : type == GunType.M107 ? 2.6f : type == GunType.SVD || type == GunType.RPG7 ? 1.5f : 1f;
        pitch -= j * 0.9f * heavy * (1 - 0.45f * a);
        roll += GunClient.joltSide * j * 0.8f;
        // reloading: tipped up and rolled so the magazine well shows
        float env = reloadEnv;
        if (type.action == GunType.Action.PUMP) { pitch -= 22f * env; roll += 55f * env; at.add(-0.03f * env, 0.03f * env, 0); }
        // the launcher is tipped muzzle-up and across, so the rocket can be fed in from the front
        else if (type.action == GunType.Action.SINGLE) { pitch -= 16f * env; yaw += 26f * env; roll += 10f * env; at.add(-0.05f * env, -0.06f * env, 0.12f * env); }
        // the belt feeds from the right: the gun is swung across so its feed side and open cover face the firer
        else if (type.beltFed() && type.feedSide() < 0) { yaw -= 42f * env; roll += 12f * env; pitch -= 4f * env; at.add(-0.25f * env, 0.02f * env, -0.13f * env); }
        else if (type.beltFed()) { yaw += 40f * env; roll -= 12f * env; pitch -= 4f * env; at.add(-0.12f * env, 0.03f * env, -0.10f * env); }
        else if (pistol) { pitch -= 24f * env; roll += 22f * env; at.add(-0.04f * env, 0.02f * env, 0.05f * env); }
        else { pitch -= 14f * env; roll += 32f * env; yaw += 6f * env; at.add(-0.02f * env, 0.03f * env, 0); }
        // every reload starts with a small dip as the hand comes off, and ends by settling a touch past rest
        if (p >= 0 && reloading && !calm) {
            at.add(0, -0.014f * Mth.sin((float) Math.PI * Mth.clamp(p / 0.12f, 0, 1)) + 0.007f * Mth.sin((float) Math.PI * Mth.clamp((p - 0.90f) / 0.10f, 0, 1)), 0);
            roll += 2.5f * Mth.sin((float) Math.PI * Mth.clamp((p - 0.90f) / 0.10f, 0, 1));
        }
        // a dead trigger or the selector: a small twitch
        float click = Math.max(0, 1 - (now - Math.max(GunClient.dryAt, GunClient.selectorAt)) / 140f);
        roll += 1.5f * click;

        Vector3f grip = model.point("grip");
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        // drawn a little shortened along the line of sight, as a long lens would show it: a full-length rifle seen
        // through a wide view otherwise looks stretched and fills the screen
        float depth = (pistol ? 0.85f : Mth.lerp(a, 0.62f, 0.72f));
        pose.scale(1, 1, depth);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        // from here on in gun space; it turns about the firing hand
        pose.translate(grip.x, grip.y, grip.z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(-grip.x, -grip.y, -grip.z - j * 0.011f * heavy);

        GunModels.render(stack, type, bp, pose, buffers, light);

        if (!player.isInvisible()) {
            float size = 0.40f;
            Vector3f rightElbow = pistol ? new Vector3f(-0.10f, -0.22f, -0.50f) : new Vector3f(-0.16f, -0.30f, -0.42f);
            Vector3f leftElbow = pistol ? new Vector3f(0.30f, -0.22f, -0.50f) : new Vector3f(0.26f, -0.30f, -0.50f);
            arm(pose, buffers, light, player, true, new Vector3f(grip).add(-0.004f, 0.012f, 0.0f), rightElbow, size);
            if (type.beltFed()) leftElbow.lerp(new Vector3f(-0.30f * -type.feedSide(), -0.42f, -0.30f), env);     // reaching in from underneath
            arm(pose, buffers, light, player, false, new Vector3f(left).add(0.006f, -0.028f, 0), leftElbow, size);
        }

        // muzzle flash, for a frame or two after the shot
        if (sinceShot < 48 && !GunItem.suppressed(stack) && GunClient.lastShot > 0 && !calm) {
            Vector3f mz = GunModels.muzzle(stack, type);
            pose.pushPose();
            pose.translate(mz.x, mz.y, mz.z);
            GunFx.flash(pose, buffers, type, GunClient.lastShot);
            pose.popPose();
        }
        // the red dot: a point of light in the middle of the lens
        if ((optic == Attachment.RED_DOT || optic == Attachment.HOLO) && a > 0.35f) {
            Vector3f lens = new Vector3f(GunModels.mount(model, optic)).add(GunModels.part(optic).point("lens"));
            dot(pose, buffers, lens.x, lens.y, lens.z - 0.004f, optic == Attachment.HOLO ? 0.0007f : 0.0011f, a);
            if (optic == Attachment.HOLO) ring(pose, buffers, lens.x, lens.y, lens.z - 0.004f, 0.0075f, a);
        }
        pose.popPose();
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float r, float alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int al = (int) (255 * Mth.clamp(alpha, 0, 1));
        for (int pass = 0; pass < 2; pass++) {
            float s = pass == 0 ? r * 2.6f : r;
            int aa = pass == 0 ? al / 4 : al;
            vc.addVertex(m, x - s, y - s, z).setColor(255, 40, 30, aa);
            vc.addVertex(m, x - s, y + s, z).setColor(255, 40, 30, aa);
            vc.addVertex(m, x + s, y + s, z).setColor(255, 40, 30, aa);
            vc.addVertex(m, x + s, y - s, z).setColor(255, 40, 30, aa);
        }
    }

    /** The holographic sight's ring, with its four ticks. */
    private static void ring(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float r, float alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int al = (int) (220 * Mth.clamp(alpha, 0, 1));
        float t = r * 0.09f;
        for (int i = 0; i < 24; i++) {
            float a0 = (float) (i * Math.PI / 12), a1 = (float) ((i + 1) * Math.PI / 12);
            vc.addVertex(m, x + Mth.cos(a0) * (r - t), y + Mth.sin(a0) * (r - t), z).setColor(255, 40, 30, al);
            vc.addVertex(m, x + Mth.cos(a0) * (r + t), y + Mth.sin(a0) * (r + t), z).setColor(255, 40, 30, al);
            vc.addVertex(m, x + Mth.cos(a1) * (r + t), y + Mth.sin(a1) * (r + t), z).setColor(255, 40, 30, al);
            vc.addVertex(m, x + Mth.cos(a1) * (r - t), y + Mth.sin(a1) * (r - t), z).setColor(255, 40, 30, al);
        }
        for (int i = 0; i < 4; i++) {
            float c = Mth.cos((float) (i * Math.PI / 2)), s = Mth.sin((float) (i * Math.PI / 2));
            float x0 = x + c * r, y0 = y + s * r, x1 = x + c * r * 1.35f, y1 = y + s * r * 1.35f;
            vc.addVertex(m, x0 - s * t, y0 + c * t, z).setColor(255, 40, 30, al);
            vc.addVertex(m, x1 - s * t, y1 + c * t, z).setColor(255, 40, 30, al);
            vc.addVertex(m, x1 + s * t, y1 - c * t, z).setColor(255, 40, 30, al);
            vc.addVertex(m, x0 + s * t, y0 - c * t, z).setColor(255, 40, 30, al);
        }
    }

    /**
     * One of the player's own arms, drawn so its hand is at a point on the gun and its forearm runs back towards
     * where the elbow would be. Scaled down: a blocky arm at full size would dwarf a gun built to real measurements.
     */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, boolean right,
                            Vector3f hand, Vector3f toElbow, float size) {
        var renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm, sleeve = right ? model.rightSleeve : model.leftSleeve;
        Vector3f along = new Vector3f(toElbow).normalize().negate();          // from the elbow to the hand
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, along.x, along.y, along.z));
        pose.scale(size, size, size);
        pose.translate((right ? 1 : -1) / 16f, -10 / 16f, 0);
        var skin = player.getSkin().texture();
        for (ModelPart part : new ModelPart[]{arm, sleeve}) {
            boolean shown = part.visible;
            float px = part.x, py = part.y, pz = part.z, rx = part.xRot, ry = part.yRot, rz = part.zRot;
            part.visible = true;
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.render(pose, buffers.getBuffer(part == arm ? RenderType.entitySolid(skin) : RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
            part.visible = shown;
            part.setPos(px, py, pz);
            part.setRotation(rx, ry, rz);
        }
        pose.popPose();
    }

    private GunHand() {}
}
