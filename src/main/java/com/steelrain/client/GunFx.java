package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steelrain.ModRegistry;
import com.steelrain.gun.Caliber;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunLogic;
import com.steelrain.gun.GunType;
import com.steelrain.net.Payloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What a shot looks and sounds like: the report (arriving late from far away, as sound does), the muzzle flash, the
 * spent case thrown clear, tracers burning along the bullet's path, and the crack of a bullet passing close by.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class GunFx {
    private static final class Round {
        Vec3 pos, prev, vel;
        final Vec3 from;
        final Caliber caliber;
        final boolean tracer, mine;
        boolean heard, done;
        int age, shooter;
        Round(Vec3 from, Vec3 vel, Caliber c, boolean tracer, boolean mine) {
            this.from = from; this.pos = from; this.prev = from; this.vel = vel; this.caliber = c; this.tracer = tracer; this.mine = mine;
        }
    }

    private static final class Case {
        Vec3 pos, prev, vel;
        float spin, turn;
        final float size;
        final int colour;
        int age;
        boolean landed;
        Case(Vec3 pos, Vec3 vel, float size, int colour) { this.pos = pos; this.prev = pos; this.vel = vel; this.size = size; this.colour = colour; }
    }

    private record Flash(Vec3 at, Vec3 dir, long time, GunType type) {}
    private record Report(long at, Vec3 pos, String sound, float volume, float pitch) {}
    private record Ejection(long at, int shooter, GunType type) {}

    private static final List<Round> ROUNDS = new ArrayList<>();
    private static final List<Case> CASES = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Report> REPORTS = new ArrayList<>();
    private static final List<Ejection> EJECTIONS = new ArrayList<>();

    private static final BoxModel CASE_MODEL = new BoxModel().tube(0xFFFFFF, 0, 0, -0.5, 0.5, 0.28);

    static void shot(Payloads.GunShot p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || p.gun() < 0 || p.gun() >= GunType.VALUES.length) return;
        GunType type = GunType.VALUES[p.gun()];
        Entity shooter = mc.level.getEntity(p.shooter());
        boolean mine = shooter == mc.player;
        boolean suppressed = (p.flags() & Payloads.GunShot.SUPPRESSED) != 0;
        Vec3 dir = p.velocity().normalize();
        int calId = p.flags() >> Payloads.GunShot.CALIBER_SHIFT;
        Caliber cal = calId >= 0 && calId < Caliber.values().length ? Caliber.values()[calId] : type.caliber;
        Round round = new Round(p.from(), p.velocity(), cal, (p.flags() & Payloads.GunShot.TRACER) != 0, mine);
        round.shooter = p.shooter();
        ROUNDS.add(round);
        if ((p.flags() & Payloads.GunShot.PRIMARY) == 0) return;

        // the report: the gun's own voice close by, a duller thump from far off, and late by the speed of sound
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double d = ear.distanceTo(p.from());
        long now = Util.getMillis();
        float pitch = 0.96f + mc.level.random.nextFloat() * 0.08f;
        String sound;
        float volume;
        if (suppressed) {
            sound = type.pistol() ? "gun_pistol_suppressed" : "gun_suppressed";
            volume = 2.2f;
        } else if (d < 90) {
            sound = "gun_" + type.id + "_fire";
            volume = 7f;
        } else {
            sound = type.pistol() ? "gun_pistol_far" : cal == Caliber.R762 || cal == Caliber.R76251 || cal == Caliber.G12 || cal == Caliber.B50 || cal.rocket() ? "gun_heavy_far" : "gun_rifle_far";
            volume = 28f;
        }
        REPORTS.add(new Report(mine ? now : now + (long) (d / 343.0 * 1000), p.from(), sound, volume, pitch));

        if (mine) GunClient.recoil(type, mc.player.getMainHandItem());
        boolean firstPerson = mine && mc.options.getCameraType().isFirstPerson();
        Vec3 muzzle = p.from().add(dir.scale(type.pistol() ? 0.6 : 1.1)).add(0, -0.12, 0);
        if (!suppressed && !firstPerson) FLASHES.add(new Flash(muzzle, dir, now, type));
        if (!firstPerson && mc.level.random.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.WHITE_SMOKE, muzzle.x, muzzle.y, muzzle.z, dir.x * 0.05, 0.01, dir.z * 0.05);
        }
        // the empty case: at once from a self-loader, when the action is worked on a pump gun
        if (!cal.rocket()) EJECTIONS.add(new Ejection(now + (type.action == GunType.Action.PUMP ? 330 : 0), p.shooter(), type));
    }

    /** The server says this firer's rocket has gone off: stop drawing it. */
    static void rocketEnded(int shooter) {
        for (Round r : ROUNDS) if (r.shooter == shooter && r.caliber.rocket()) { r.done = true; break; }
    }

    /** The firer's right, level with the ground. */
    private static Vec3 sideways(Vec3 dir) {
        Vec3 s = new Vec3(dir.z, 0, -dir.x);
        return s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize().scale(-1);
    }

    private static void eject(Minecraft mc, Ejection ej) {
        Entity e = mc.level.getEntity(ej.shooter);
        if (e == null) return;
        double vel0 = 0;
        Vec3 dir = e.getLookAngle(), right = sideways(dir);
        if (ej.type == GunType.PKM) right = right.scale(-1);
        if (ej.type == GunType.M249 || ej.type == GunType.M240) vel0 = -0.06;                 // these drop theirs out underneath                       // the PKM throws its cases to the left
        var r = mc.level.random;
        Vec3 at = e.getEyePosition().add(dir.scale(ej.type.pistol() ? 0.5 : 0.42)).add(right.scale(0.2)).add(0, -0.13, 0);
        Vec3 vel = right.scale(0.11 + r.nextDouble() * 0.05).add(0, vel0 != 0 ? vel0 : 0.09 + r.nextDouble() * 0.05, 0).add(dir.scale(-0.03 + r.nextDouble() * 0.05))
                .add(e.getDeltaMovement());
        float size = switch (ej.type.caliber) { case R762, R76251 -> 0.054f; case G12 -> 0.068f; case P9 -> 0.02f; case B50 -> 0.10f; default -> 0.042f; };
        int colour = switch (ej.type.caliber) { case R545, R762 -> 0x5C6848; case G12 -> 0xA8201E; default -> 0xC8A04A; };
        Case c = new Case(at, vel, size, colour);
        c.turn = (r.nextFloat() - 0.5f) * 1.6f;
        CASES.add(c);
        if (CASES.size() > 160) CASES.remove(0);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { ROUNDS.clear(); CASES.clear(); FLASHES.clear(); REPORTS.clear(); EJECTIONS.clear(); return; }
        if (mc.isPaused()) return;
        long now = Util.getMillis();
        for (Iterator<Report> it = REPORTS.iterator(); it.hasNext(); ) {
            Report r = it.next();
            if (r.at > now) continue;
            it.remove();
            mc.level.playLocalSound(r.pos.x, r.pos.y, r.pos.z, GunLogic.sound(r.sound), SoundSource.PLAYERS, r.volume, r.pitch, false);
        }
        for (Iterator<Ejection> it = EJECTIONS.iterator(); it.hasNext(); ) {
            Ejection ej = it.next();
            if (ej.at > now) continue;
            it.remove();
            eject(mc, ej);
        }
        FLASHES.removeIf(f -> now - f.time > 70);
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        for (Iterator<Round> it = ROUNDS.iterator(); it.hasNext(); ) {
            Round r = it.next();
            if (r.done || ++r.age > (r.caliber.rocket() ? 90 : 100) || r.vel.lengthSqr() < 4) { it.remove(); continue; }
            if (r.caliber.rocket() && r.age > 1) {                                   // the motor's smoke, strung out behind it
                for (int k = 0; k < 3; k++) {
                    Vec3 at = r.pos.subtract(r.vel.scale(k / 3.0));
                    mc.level.addParticle(k == 0 ? ParticleTypes.FLAME : ParticleTypes.SMOKE, true, at.x, at.y, at.z, 0, 0.005, 0);
                }
            }
            r.prev = r.pos;
            Vec3 to = r.pos.add(r.vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(r.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) { to = hit.getLocation(); r.done = true; }
            // somebody else's bullet going past the ear: the crack of a supersonic round, the buzz of a slow one
            if (!r.mine && !r.heard) {
                Vec3 seg = to.subtract(r.pos);
                double len2 = seg.lengthSqr();
                double t = len2 < 1e-9 ? 0 : Mth.clamp(ear.subtract(r.pos).dot(seg) / len2, 0, 1);
                Vec3 near = r.pos.add(seg.scale(t));
                if (near.distanceToSqr(ear) < 3.2 * 3.2 && near.distanceToSqr(r.from) > 10 * 10) {
                    r.heard = true;
                    boolean fast = r.vel.length() > 17.2 && !r.caliber.rocket();
                    mc.level.playLocalSound(near.x, near.y, near.z, GunLogic.sound(fast ? "bullet_crack" : "bullet_whizz"), SoundSource.PLAYERS,
                            fast ? 1.0f : 0.8f, 0.9f + mc.level.random.nextFloat() * 0.25f, false);
                }
            }
            r.pos = to;
            r.vel = r.caliber.advance(r.vel);
        }
        for (Iterator<Case> it = CASES.iterator(); it.hasNext(); ) {
            Case c = it.next();
            if (++c.age > 70) { it.remove(); continue; }
            c.prev = c.pos;
            if (c.landed && c.vel.lengthSqr() < 0.0004) continue;
            Vec3 to = c.pos.add(c.vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(c.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                to = hit.getLocation().add(n.scale(0.01));
                c.vel = c.vel.subtract(n.scale(1.4 * c.vel.dot(n))).scale(0.45);
                if (!c.landed && to.distanceToSqr(ear) < 100) {
                    mc.level.playLocalSound(to.x, to.y, to.z, GunLogic.sound("gun_casing"), SoundSource.PLAYERS, 0.35f, 0.9f + mc.level.random.nextFloat() * 0.3f, false);
                }
                c.landed = true;
                c.turn *= 0.4f;
            }
            c.pos = to;
            c.vel = c.vel.add(0, -0.045, 0).scale(0.98);
            c.spin += c.turn;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ROUNDS.isEmpty() && CASES.isEmpty() && FLASHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        if (!CASES.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Models.PAINT));
            for (Case c : CASES) {
                Vec3 at = c.prev.lerp(c.pos, pt);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
                pose.pushPose();
                pose.translate(at.x, at.y + c.size * 0.14, at.z);
                pose.mulPose(Axis.YP.rotation(c.spin * 0.7f));
                pose.mulPose(Axis.XP.rotation(c.landed ? 0 : c.spin));
                pose.scale(c.size, c.size, c.size);
                tinted(pose, vc, light, c.colour);
                pose.popPose();
            }
            buffers.endBatch(RenderType.entityCutoutNoCull(Models.PAINT));
        }

        for (Round r : ROUNDS) {                                                   // rockets in flight are drawn as themselves
            if (!r.caliber.rocket() || r.age < 1) continue;
            Vec3 at = r.prev.lerp(r.pos, pt);
            if (r.mine && at.distanceToSqr(r.from) < 2.5 * 2.5) continue;
            BoxModel rocket = GunModels.ammo(r.caliber);
            Vec3 d = r.vel.normalize();
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotation((float) Mth.atan2(d.x, d.z)));
            pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(d.y, -1, 1))));
            rocket.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(rocket.texture())), LevelRenderer.getLightColor(mc.level, BlockPos.containing(at)));
            pose.popPose();
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        for (Round r : ROUNDS) {
            if (!r.tracer || r.age < 1) continue;
            Vec3 head = r.prev.lerp(r.pos, pt);
            // nothing is drawn in the firer's face: the trace lights a little way out
            if (head.distanceToSqr(r.from) < 9 * 9) continue;
            Vec3 dir = r.pos.subtract(r.prev);
            if (dir.lengthSqr() < 1e-6) continue;
            dir = dir.normalize();
            double length = Math.min(Math.min(9, r.vel.length() * 0.3), head.distanceTo(r.from) - 6);
            Vec3 tail = head.subtract(dir.scale(Math.max(0.5, length)));
            Vec3 toCam = cam.subtract(head);
            Vec3 side = dir.cross(toCam);
            if (side.lengthSqr() < 1e-8) continue;
            double width = Mth.clamp(toCam.length() * 0.0035, 0.025, 0.4);
            side = side.normalize().scale(width);
            boolean green = r.caliber == Caliber.R545 || r.caliber == Caliber.R762;      // Russian tracers burn green
            float cr = green ? 0.55f : 1f, cg = green ? 1f : 0.42f, cb = green ? 0.45f : 0.18f;
            vertex(glow, m, head.add(side), cr, cg, cb, 0.95f);
            vertex(glow, m, head.subtract(side), cr, cg, cb, 0.95f);
            vertex(glow, m, tail.subtract(side.scale(0.4)), cr, cg, cb, 0f);
            vertex(glow, m, tail.add(side.scale(0.4)), cr, cg, cb, 0f);
        }
        long now = Util.getMillis();
        for (Flash f : FLASHES) {
            pose.pushPose();
            pose.translate(f.at.x, f.at.y, f.at.z);
            pose.mulPose(Axis.YP.rotation((float) Mth.atan2(f.dir.x, f.dir.z)));
            pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(f.dir.y, -1, 1))));
            flashQuads(pose, glow, f.type, f.time, 1f - (now - f.time) / 70f);
            pose.popPose();
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    private static void tinted(PoseStack pose, VertexConsumer vc, int light, int rgb) {
        // the shared case model is white; its colour comes from the vertex tint
        for (float[] q : CASE_MODEL.flatten()) {
            var p = pose.last();
            for (int i = 0; i < 12; i += 3) {
                vc.addVertex(p.pose(), q[i], q[i + 1], q[i + 2]).setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f)
                        .setUv(0.5f, 0.5f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, q[12], q[13], q[14]);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    /** The flash at the muzzle of a gun drawn in gun space with the muzzle at the origin (first person). */
    static void flash(PoseStack pose, MultiBufferSource buffers, GunType type, long shotAt) {
        flashQuads(pose, buffers.getBuffer(RenderType.lightning()), type, shotAt, 1f - (Util.getMillis() - shotAt) / 48f);
    }

    /** A star of bright petals along +z from the origin, different for every shot. */
    private static void flashQuads(PoseStack pose, VertexConsumer vc, GunType type, long seed, float strength) {
        if (strength <= 0) return;
        java.util.Random rnd = new java.util.Random(seed * 31 + type.ordinal());
        float size = (type.pistol() ? 0.09f : type == GunType.M590 ? 0.2f : type == GunType.M107 || type == GunType.RPG7 ? 0.34f : type.caliber == Caliber.R762 || type.caliber == Caliber.R76251 ? 0.19f : 0.15f) * (0.8f + rnd.nextFloat() * 0.5f);
        int petals = type == GunType.AK74M ? 4 : 3;
        float a = Mth.clamp(strength, 0, 1);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(rnd.nextFloat() * 6.28f));
        for (int i = 0; i < petals; i++) {
            pose.mulPose(Axis.ZP.rotation((float) Math.PI / petals));
            Matrix4f m = pose.last().pose();
            float w = size * 0.42f, l = size * (1.2f + rnd.nextFloat() * 0.8f);
            vc.addVertex(m, 0, 0, 0).setColor(1f, 0.95f, 0.75f, a);
            vc.addVertex(m, -w, 0, l * 0.45f).setColor(1f, 0.72f, 0.25f, a * 0.7f);
            vc.addVertex(m, 0, 0, l).setColor(1f, 0.5f, 0.1f, 0f);
            vc.addVertex(m, w, 0, l * 0.45f).setColor(1f, 0.72f, 0.25f, a * 0.7f);
        }
        // seen from in front or behind: a small bright star across the bore
        for (int i = 0; i < 4; i++) {
            pose.mulPose(Axis.ZP.rotation((float) Math.PI / 2));
            Matrix4f m = pose.last().pose();
            float r = size * (0.45f + rnd.nextFloat() * 0.3f), w = size * 0.16f;
            vc.addVertex(m, 0, 0, 0.02f).setColor(1f, 0.95f, 0.8f, a);
            vc.addVertex(m, r * 0.4f, -w, 0.02f).setColor(1f, 0.75f, 0.3f, a * 0.6f);
            vc.addVertex(m, r, 0, 0.02f).setColor(1f, 0.5f, 0.1f, 0f);
            vc.addVertex(m, r * 0.4f, w, 0.02f).setColor(1f, 0.75f, 0.3f, a * 0.6f);
        }
        pose.popPose();
    }

    private GunFx() {}
}
