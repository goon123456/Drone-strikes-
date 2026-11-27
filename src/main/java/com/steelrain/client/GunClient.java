package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.gun.Attachment;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunLogic;
import com.steelrain.gun.GunType;
import com.steelrain.net.Payloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handling a gun on the firer's side: reading the trigger, sights and keys, bringing the sights up, the kick of
 * each shot and the muzzle settling again, the drift of the sights with breathing and tiredness, and the timing of
 * the reload the hands go through.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class GunClient {
    // ------------------------------------------------------------------ what is held
    @Nullable private static GunType held;
    private static int heldSlot = -1;
    static long drawnAt;
    private static boolean sentTrigger, sentAim;
    static boolean aimHeld, triggerHeld;

    @Nullable
    public static GunType held() { return held; }

    public static ItemStack stack() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getMainHandItem();
    }

    // ------------------------------------------------------------------ sights, stance, steadiness
    private static float ads;
    private static long lastFrame;
    /** 0 rested to 1 out of breath from running; and the lungful being held, 1 full to 0 spent. */
    static float fatigue, breath = 1f;
    /** How far the muzzle is crowded against a wall, 0 clear to 1, for raising the gun to a port position. */
    public static float blocked;
    static boolean holding;
    private static boolean winded;
    static float bipod, sprint;
    private static double swayX, swayY;
    /** The field of view the world was last drawn with (degrees), for the scope reticles. */
    static double worldFov = 70;

    /** How far the sights are up, 0 at the hip to 1 on aim, eased. */
    public static float ads() { float t = Mth.clamp(ads, 0, 1); return t * t * (3 - 2 * t); }

    @Nullable
    static Attachment optic() {
        ItemStack s = stack();
        return s.getItem() instanceof GunItem ? GunItem.part(s, Attachment.Slot.OPTIC) : null;
    }

    /** The flip magnifier's position, and where the variable scope's ring is set. */
    public static boolean magnifierOn = true;
    static float varZoom = 1f;
    private static net.minecraft.world.effect.MobEffectInstance nightSight;

    /** The magnification of the sight picture if the sight in use is looked through like a telescope, else 0. */
    public static float scopeZoom() {
        ItemStack s = stack();
        Attachment o = optic();
        if (o == null) return 0;
        if (o == Attachment.LPVO) return varZoom;
        if (o.zoom > 1f) return o.zoom;
        if ((o == Attachment.RED_DOT || o == Attachment.HOLO) && magnifierOn && GunItem.part(s, Attachment.Slot.MAGNIFIER) != null) return Attachment.MAGNIFIER.zoom;
        return 0;
    }

    /** Looking through a magnifying scope: the gun is not drawn and the scope picture fills the view. */
    public static boolean scoped() {
        return held != null && scopeZoom() > 0 && ads() > 0.9f && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** How much the view is magnified right now. */
    public static float zoom() {
        if (held == null) return 1f;
        Attachment o = optic();
        float a = ads();
        float z = scopeZoom();
        if (z > 0) return Mth.lerp(Mth.clamp((a - 0.7f) / 0.3f, 0, 1), 1f + 0.2f * a, Math.max(1f, z));
        return 1f + 0.22f * a;
    }

    // ------------------------------------------------------------------ recoil
    private static float kickPitch, kickYaw, settle;
    /** View offset still to be bled back after a flinch punch, so the kick fully recovers. */
    private static float flinchReturnPitch, flinchReturnYaw;
    static long lastShot;

    /** Being shot throws the view: a punch applied at once, then returned over the next fraction of a second. */
    public static void flinch(float punchPitch, float punchYaw) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (ClientConfig.calm()) { punchPitch *= 0.4f; punchYaw *= 0.4f; }
        p.setXRot(Mth.clamp(p.getXRot() + punchPitch, -90, 90)); p.xRotO += punchPitch;
        p.setYRot(p.getYRot() + punchYaw); p.yRotO += punchYaw;
        flinchReturnPitch += punchPitch; flinchReturnYaw += punchYaw;
    }
    /** The firer's own barrel heat (0..1) and stoppage, mirrored for the gauge; belt guns only. */
    public static float heat;
    public static boolean jammed;
    /** The gun's own jump in the hands: back, muzzle up, a twist. Springs back by itself. */
    static float jolt, joltSide;
    private static int burst;

    static void recoil(GunType type, ItemStack stack) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        long now = Util.getMillis();
        burst = now - lastShot < 220 ? burst + 1 : 0;
        lastShot = now;
        float k = 1f;
        if (ads > 0.6f) k *= 0.85f;
        if (p.isCrouching()) k *= 0.82f;
        if (bipod > 0.5f) k *= 0.4f;
        Attachment under = GunItem.part(stack, Attachment.Slot.UNDER);
        if (under == Attachment.FOREGRIP) k *= 0.8f;
        else if (under == Attachment.ANGLED_GRIP) k *= 0.9f;
        boolean calm = ClientConfig.calm();
        if (calm) k *= 0.45f;
        if (GunItem.part(stack, Attachment.Slot.MUZZLE) != null) k *= 0.9f;
        var r = p.getRandom();
        // the first rounds of a burst climb, then the muzzle wanders more than it rises
        float climb = burst < 3 ? 1.1f : 0.7f;
        kickPitch += type.recoilUp * k * climb * (0.85f + r.nextFloat() * 0.3f);
        float drift = (float) Math.sin(burst * 0.9) * 0.6f + (r.nextFloat() - 0.5f) * 1.6f;
        kickYaw += type.recoilSide * k * drift * (burst < 2 ? 0.5f : 1f);
        jolt = Math.min(1.6f, jolt + (calm ? 0.3f : 1f));
        joltSide = (r.nextFloat() - 0.5f) * 2f;
        if (type.beltFed()) heat = Math.min(1f, heat + 0.011f);     // mirrors the server's HEAT_PER_SHOT for the gauge
    }

    /** Recoil and sway are applied to where the player looks, a little every frame, so they are smooth. */
    @SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = Util.getMillis();
        float dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        if (p == null || mc.isPaused()) return;
        // the flinch bleed runs whether or not a gun is in the hands: anyone shot is thrown
        if (flinchReturnPitch != 0 || flinchReturnYaw != 0) {
            float f = 1 - (float) Math.exp(-dt / 130.0);
            float dp = flinchReturnPitch * f, dy = flinchReturnYaw * f;
            flinchReturnPitch -= dp; flinchReturnYaw -= dy;
            p.setXRot(Mth.clamp(p.getXRot() - dp, -90, 90)); p.xRotO -= dp;
            p.setYRot(p.getYRot() - dy); p.yRotO -= dy;
            if (Math.abs(flinchReturnPitch) < 0.002f) flinchReturnPitch = 0;
            if (Math.abs(flinchReturnYaw) < 0.002f) flinchReturnYaw = 0;
        }
        if (heat > 0 && now - lastShot > 60) heat = Math.max(0, heat - (float) (dt / 50.0 * 0.0045));
        if (held == null) {
            ads = 0; kickPitch = kickYaw = settle = 0; jolt = 0;
            return;
        }
        boolean busy = reloading() || now - drawnAt < 250;
        boolean want = aimHeld && !p.isSprinting() && !busy && mc.screen == null;
        float rate = dt / (held.adsMs * (GunItem.part(stack(), Attachment.Slot.OPTIC) != null ? 1.1f : 1f)
                * (GunItem.part(stack(), Attachment.Slot.UNDER) == Attachment.ANGLED_GRIP ? 0.85f : 1f));
        ads = Mth.clamp(ads + (want ? rate : -rate * 1.2f), 0, 1);
        sprint += ((p.isSprinting() && !want ? 1f : 0f) - sprint) * Math.min(1, dt / 110f);
        boolean rest = (held.bipod() || GunItem.part(stack(), Attachment.Slot.UNDER) == Attachment.BIPOD) && p.isCrouching() && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() < 1e-4;
        bipod += ((rest ? 1f : 0f) - bipod) * Math.min(1, dt / 160f);
        jolt *= (float) Math.exp(-dt / 55.0);

        float dPitch = 0, dYaw = 0;
        if (kickPitch != 0 || kickYaw != 0) {
            float f = 1 - (float) Math.exp(-dt / 28.0);
            float up = kickPitch * f, side = kickYaw * f;
            kickPitch -= up; kickYaw -= side;
            dPitch -= up; dYaw += side;
            settle += up * 0.5f;
            if (Math.abs(kickPitch) < 0.002f) kickPitch = 0;
            if (Math.abs(kickYaw) < 0.002f) kickYaw = 0;
        }
        if (settle > 0 && now - lastShot > 110) {                 // the muzzle comes part of the way back down
            float back = settle * (1 - (float) Math.exp(-dt / 170.0));
            settle -= back;
            dPitch += back;
            if (settle < 0.003f) settle = 0;
        }
        // the sights never sit quite still: slow figure of eight from breathing, wider when tired or standing
        double t = now / 1000.0;
        float amp = 0.20f + held.weightKg * 0.035f;
        if (p.isCrouching()) amp *= 0.6f;
        amp *= 1f + fatigue * 2.2f;
        if (winded) amp *= 1.5f;
        if (holding) amp *= 0.12f;
        amp *= 1f - 0.85f * bipod;
        if (GunItem.part(stack(), Attachment.Slot.UNDER) != null) amp *= 0.85f;
        if (ClientConfig.calm()) amp *= 0.3f;
        amp *= ads();
        double sx = amp * (Math.sin(t * 1.15) * 0.8 + Math.sin(t * 2.9 + 1.3) * 0.25);
        double sy = amp * (Math.sin(t * 2.3 + 0.6) * 0.5 + Math.sin(t * 0.8) * 0.35);
        dYaw += (float) (sx - swayX);
        dPitch += (float) (sy - swayY);
        swayX = sx; swayY = sy;
        if (dPitch != 0 || dYaw != 0) {
            float pitch = Mth.clamp(p.getXRot() + dPitch, -90, 90);
            float moved = pitch - p.getXRot();
            p.setXRot(pitch); p.xRotO += moved;
            p.setYRot(p.getYRot() + dYaw); p.yRotO += dYaw;
        }
    }

    // ------------------------------------------------------------------ reload and other actions in the hands
    static final int ANIM_NONE = 0, ANIM_RELOAD = 1, ANIM_RELOAD_EMPTY = 2, ANIM_SHELL = 3, ANIM_PUMP = 4;
    static int anim;
    static long animAt, animMs;
    static long dryAt, selectorAt;
    /** Whether the magazine coming off at the start of this reload still had rounds in it. */
    static boolean animHadRounds;
    /** Which kind of rocket is being loaded. */
    static int animLoad;

    public static boolean reloading() { return anim == ANIM_RELOAD || anim == ANIM_RELOAD_EMPTY || anim == ANIM_SHELL; }

    /** Progress of the action in hand, 0 to 1; -1 when there is none. */
    static float animProgress() {
        if (anim == ANIM_NONE) return -1;
        float f = (Util.getMillis() - animAt) / (float) animMs;
        if (f >= 1f) {
            // a shell reload holds its pose for a moment in case the next shell follows
            if (anim == ANIM_SHELL && f < 1.25f) return 1f;
            anim = ANIM_NONE;
            return -1;
        }
        return f;
    }

    private record Cue(long at, int entity, String sound, float volume, float pitch) {}
    private static final List<Cue> CUES = new ArrayList<>();

    private static void cue(int entity, float atTick, String sound) {
        CUES.add(new Cue(Util.getMillis() + (long) (atTick * 50), entity, sound, 0.9f, 1f));
    }

    static void event(Payloads.GunEvent p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null) return;
        boolean mine = e == mc.player;
        GunType type = e instanceof net.minecraft.world.entity.LivingEntity l && l.getMainHandItem().getItem() instanceof GunItem g ? g.type : null;
        long now = Util.getMillis();
        switch (p.kind()) {
            case GunLogic.EV_RELOAD, GunLogic.EV_RELOAD_EMPTY -> {
                if (type == null) return;
                boolean empty = p.kind() == GunLogic.EV_RELOAD_EMPTY;
                int total = p.arg();
                if (mine) { anim = empty ? ANIM_RELOAD_EMPTY : ANIM_RELOAD; animAt = now; animMs = total * 50L; animHadRounds = GunItem.rounds(stack()) > 0; }
                if (type.beltFed()) {
                    cue(p.entityId(), total * 0.08f, "gun_cover");
                    if (!mine || animHadRounds) cue(p.entityId(), total * 0.20f, "gun_belt");
                    cue(p.entityId(), total * type.magOutAt(), "gun_mag_out");
                    cue(p.entityId(), total * type.magInAt(true), "gun_mag_in");
                    cue(p.entityId(), total * 0.68f, "gun_belt");
                    cue(p.entityId(), total * 0.80f, "gun_cover");
                    cue(p.entityId(), total * type.boltAt(), "gun_bolt");
                } else {
                    cue(p.entityId(), total * type.magOutAt(), "gun_mag_out");
                    cue(p.entityId(), total * type.magInAt(empty), "gun_mag_in");
                    if (empty) cue(p.entityId(), total * type.boltAt(), type.handRacked() ? "gun_bolt" : "gun_bolt_release");
                }
            }
            case GunLogic.EV_SHELL -> {
                if (type == null) return;
                if (mine) { anim = ANIM_SHELL; animAt = now; animMs = type.reloadTicks * 50L; }
                cue(p.entityId(), type.reloadTicks - 3, "gun_shell_in");
            }
            case GunLogic.EV_ROCKET -> {
                if (type == null) return;
                if (mine) { anim = ANIM_SHELL; animAt = now; animMs = type.reloadTicks * 50L; animLoad = p.arg(); }
                cue(p.entityId(), type.reloadTicks - 14, "gun_rocket_in");
            }
            case GunLogic.EV_BURST -> GunFx.rocketEnded(p.entityId());
            case GunLogic.EV_PUMP -> {
                if (mine) { anim = ANIM_PUMP; animAt = now; animMs = 450; }
                cue(p.entityId(), 2, "gun_pump");
            }
            case GunLogic.EV_DRY -> {
                if (mine) dryAt = now;
                play(e, "gun_dry", 0.7f, 1f);
            }
            case GunLogic.EV_SELECTOR -> {
                if (mine) selectorAt = now;
                play(e, "gun_selector", 0.7f, 1f);
            }
            case GunLogic.EV_CANCEL -> {
                CUES.removeIf(c -> c.entity == p.entityId());
                if (mine) anim = ANIM_NONE;
            }
            case GunLogic.EV_JAM -> {
                boolean on = p.arg() == 1;
                if (mine) { jammed = on; if (on) heat = 1f; else heat *= 0.3f; }
                if (on) play(e, "gun_dry", 0.8f, 0.7f);
                else play(e, "gun_bolt", 0.7f, 1f);
            }
            default -> { }
        }
    }

    static void play(Entity at, String sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(at.getX(), at.getEyeY() - 0.2, at.getZ(), GunLogic.sound(sound), SoundSource.PLAYERS, volume, pitch, false);
    }

    // ------------------------------------------------------------------ input
    private static void send(int action, int arg) { PacketDistributor.sendToServer(new Payloads.GunAction(action, arg)); }

    /** Right click raises the sights unless it is plainly meant for something in front of the player. */
    private static boolean useIsForWorld(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult eh) return !(eh.getEntity() instanceof net.minecraft.world.entity.LivingEntity) || eh.getEntity() instanceof net.minecraft.world.entity.npc.Npc;
        if (mc.hitResult instanceof BlockHitResult bh && mc.level != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            if (mc.player.isShiftKeyDown()) return false;
            BlockState s = mc.level.getBlockState(bh.getBlockPos());
            var b = s.getBlock();
            return s.getMenuProvider(mc.level, bh.getBlockPos()) != null || mc.level.getBlockEntity(bh.getBlockPos()) instanceof net.minecraft.world.MenuProvider
                    || b instanceof net.minecraft.world.level.block.DoorBlock || b instanceof net.minecraft.world.level.block.TrapDoorBlock
                    || b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.ButtonBlock
                    || b instanceof net.minecraft.world.level.block.LeverBlock || b instanceof net.minecraft.world.level.block.BedBlock
                    || b instanceof com.steelrain.block.WeaponBlock || b instanceof com.steelrain.block.WeaponPartBlock
                    || b instanceof net.minecraft.world.level.block.CraftingTableBlock;
        }
        return false;
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if (held == null) return;
        if (e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        } else if (e.isUseItem() && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && !useIsForWorld(Minecraft.getInstance())) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = Util.getMillis();
        for (Iterator<Cue> it = CUES.iterator(); it.hasNext(); ) {
            Cue c = it.next();
            if (c.at > now) continue;
            it.remove();
            Entity at = mc.level == null ? null : mc.level.getEntity(c.entity);
            if (at != null) play(at, c.sound, c.volume, c.pitch);
        }
        ItemStack stack = p == null ? ItemStack.EMPTY : p.getMainHandItem();
        GunType type = stack.getItem() instanceof GunItem g && !p.isSpectator() && !FpvClient.piloting() ? g.type : null;
        int slot = p == null ? -1 : p.getInventory().selected;
        if (type != held || slot != heldSlot) {
            held = type; heldSlot = slot;
            drawnAt = now; anim = ANIM_NONE;
            heat = 0; jammed = false;
            sentTrigger = sentAim = false;
            ads = 0;
        }
        if (type == null) {
            if (nightSight != null && p != null) {
                if (p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION) == nightSight) p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                nightSight = null;
            }
            aimHeld = triggerHeld = false;
            fatigue = Math.max(0, fatigue - 0.005f);
            return;
        }
        boolean free = mc.screen == null && mc.mouseHandler.isMouseGrabbed();
        triggerHeld = free && mc.options.keyAttack.isDown();
        aimHeld = free && mc.options.keyUse.isDown() && !(useIsForWorld(mc) && ads < 0.05f);
        if ((triggerHeld || aimHeld) && p.isSprinting()) p.setSprinting(false);
        boolean aiming = ads > 0.6f;
        if (triggerHeld != sentTrigger || aiming != sentAim) {
            sentTrigger = triggerHeld; sentAim = aiming;
            PacketDistributor.sendToServer(new Payloads.GunInput(triggerHeld, aiming));
        }
        while (ClientSetup.GUN_RELOAD.consumeClick()) if (free) send(GunLogic.ACT_RELOAD, 0);
        while (ClientSetup.GUN_MODE.consumeClick()) if (free) send(GunLogic.ACT_MODE, 0);
        while (ClientSetup.GUN_ZERO_UP.consumeClick()) if (free) send(GunLogic.ACT_ZERO, 1);
        while (ClientSetup.GUN_ZERO_DOWN.consumeClick()) if (free) send(GunLogic.ACT_ZERO, -1);
        while (ClientSetup.GUN_MAGNIFIER.consumeClick()) if (free && GunItem.part(stack, Attachment.Slot.MAGNIFIER) != null) {
            magnifierOn = !magnifierOn;
            play(p, "gun_selector", 0.6f, 1.3f);
        }
        while (ClientSetup.GUN_SIDE.consumeClick()) if (free) send(GunLogic.ACT_SIDE, 0);
        // a night sight amplifies what light there is, for as long as the eye is at it
        boolean night = scoped() && optic() == Attachment.NV_SCOPE;
        if (night && nightSight == null && !p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            nightSight = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, true, false, false);
            p.addEffect(nightSight);
        } else if (!night && nightSight != null) {
            if (p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION) == nightSight) p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            nightSight = null;
        }
        while (ClientSetup.GUN_FIT.consumeClick()) if (free && !reloading()) mc.setScreen(new GunScreen());
        // running leaves the firer out of breath; a held breath steadies the sights for a few seconds
        fatigue = Mth.clamp(fatigue + (p.isSprinting() ? 0.009f : -0.0045f), 0, 1);
        boolean crowd = mc.options.getCameraType().isFirstPerson() && com.steelrain.gun.GunLogic.muzzleBlocked(p, type);
        blocked += ((crowd ? 1f : 0f) - blocked) * 0.4f;
        boolean wantHold = free && ClientSetup.GUN_BREATH.isDown() && ads > 0.8f && !winded;
        holding = wantHold && breath > 0;
        breath = Mth.clamp(breath + (holding ? -0.011f : 0.008f), 0, 1);
        if (breath <= 0) winded = true;
        if (winded && breath > 0.45f) winded = false;
    }

    /** The scroll wheel turns the power ring of a variable scope while the eye is at it. */
    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (held == null || !aimHeld || optic() != Attachment.LPVO || e.getScrollDeltaY() == 0) return;
        varZoom = Mth.clamp(varZoom + (float) Math.signum(e.getScrollDeltaY()) * 0.5f, 1f, Attachment.LPVO.zoom);
        e.setCanceled(true);
    }

    /** Aiming narrows the view; a magnifying scope narrows it a great deal. */
    @SubscribeEvent
    public static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov()) return;
        if (held != null && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            double half = Math.toRadians(e.getFOV() / 2);
            e.setFOV(Math.toDegrees(2 * Math.atan(Math.tan(half) / zoom())));
        }
        worldFov = e.getFOV();
    }

    /** ...and the mouse slows to match, so the sight picture moves at the same speed across the screen. */
    @SubscribeEvent
    public static void turn(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent e) {
        if (held == null) return;
        float z = zoom();
        if (z > 1.01f) e.setMouseSensitivity(e.getMouseSensitivity() / Math.pow(z, 0.8));
    }

    /** No vanilla crosshair with a gun in the hands: the gun's own sights, or the scatter marks, replace it. */
    @SubscribeEvent
    public static void crosshair(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) {
        if (held == null) return;
        var name = e.getName();
        if (name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) e.setCanceled(true);
        // nothing but the scope picture while looking through a telescopic sight
        else if (scoped() && name.getNamespace().equals("minecraft") && !name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT)
                && !name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SUBTITLE_OVERLAY)) e.setCanceled(true);
    }

    private GunClient() {}
}
