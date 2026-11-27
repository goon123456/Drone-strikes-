package com.steelrain.gun;

import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's side of handling a gun: the trigger and the rate of fire, feeding rounds from the magazine, the
 * steps of a reload, the selector, the sights and fitting attachments. The player's game only says what the
 * fingers are doing; what the gun does about it is decided here.
 */
public final class GunLogic {
    public static final int ACT_RELOAD = 0, ACT_MODE = 1, ACT_ZERO = 2, ACT_FIT = 3, ACT_REMOVE = 4, ACT_SIDE = 5;
    /** What everyone nearby is told about, so they can animate and hear it. */
    public static final int EV_RELOAD = 0, EV_RELOAD_EMPTY = 1, EV_SHELL = 2, EV_PUMP = 3, EV_DRY = 4, EV_SELECTOR = 5, EV_CANCEL = 6, EV_ROCKET = 7, EV_BURST = 8, EV_JAM = 9;

    private static final class State {
        GunType type;
        int slot = -1;
        boolean trigger, aiming, latched, dry;
        /** The trigger went down at some point since the last tick, even if it is already up again: a quick tap. */
        boolean pulse;
        double nextShot;
        int reload = -1, reloadTotal;
        boolean reloadEmpty, magOut, magIn;
        int pendingLoad;
        @javax.annotation.Nullable net.minecraft.core.BlockPos lit;
        int shots;
        long lastShot;
        /** Barrel heat 0..1 and whether it has cooked off into a stoppage. Only the belt guns build it up. */
        float heat;
        boolean jammed;
    }

    /** Heat one shot adds to a sustained-fire gun's barrel; about ninety rounds flat-out before it cooks off. */
    private static final float HEAT_PER_SHOT = 0.011f, HEAT_COOL = 0.0045f;

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static SoundEvent sound(String name) {
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, name));
        return e != null ? e : SoundEvents.UI_BUTTON_CLICK.value();
    }

    public static void forget(ServerPlayer player) {
        State st = STATES.remove(player.getUUID());
        if (st != null) douse(player.serverLevel(), st);
    }

    /** Takes away the patch of light a weapon light was throwing. */
    private static void douse(ServerLevel level, State st) {
        if (st.lit != null && level.isLoaded(st.lit) && level.getBlockState(st.lit).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
            level.setBlock(st.lit, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        st.lit = null;
    }

    /**
     * A weapon light: the game has no moving lights, so the spot the beam falls on is lit with an invisible light
     * block, moved as the gun moves and removed when the light goes off or the gun is put away.
     */
    private static void shine(ServerPlayer player, ItemStack stack, State st) {
        ServerLevel level = player.serverLevel();
        boolean on = stack.getItem() instanceof GunItem && GunItem.part(stack, Attachment.Slot.SIDE) == Attachment.FLASHLIGHT
                && stack.getOrDefault(ModRegistry.ACTIVE.get(), false);
        if (!on) { if (st.lit != null) douse(level, st); return; }
        if (player.tickCount % 2 != 0) return;
        Vec3 eye = player.getEyePosition(), to = eye.add(player.getLookAngle().scale(14));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 spot = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation().subtract(player.getLookAngle().scale(0.6));
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(spot);
        if (pos.equals(st.lit)) return;
        douse(level, st);
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 14), 2 | 16);
            st.lit = pos;
        }
    }

    public static void input(ServerPlayer player, boolean trigger, boolean aiming) {
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        if (trigger != st.trigger) { st.latched = false; st.dry = false; }       // a fresh pull, or the finger came off
        if (trigger) st.pulse = true;
        st.trigger = trigger;
        st.aiming = aiming;
    }

    public static void action(ServerPlayer player, int action, int arg) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        switch (action) {
            case ACT_RELOAD -> startReload(player, stack, gun.type, st);
            case ACT_MODE -> {
                if (st.reload >= 0) return;
                stack.set(ModRegistry.FIRE_MODE.get(), (GunItem.modeIndex(stack) + 1) % gun.type.modes.length);
                event(player, EV_SELECTOR, GunItem.mode(stack));
            }
            case ACT_ZERO -> {
                int[] zeros = GunItem.zeros(stack);
                int now = Mth.clamp(stack.getOrDefault(ModRegistry.ZERO.get(), 0), 0, zeros.length - 1);
                int next = Mth.clamp(now + (arg > 0 ? 1 : -1), 0, zeros.length - 1);
                if (next != now) stack.set(ModRegistry.ZERO.get(), next);
            }
            case ACT_SIDE -> {
                if (GunItem.part(stack, Attachment.Slot.SIDE) == null) return;
                stack.set(ModRegistry.ACTIVE.get(), !stack.getOrDefault(ModRegistry.ACTIVE.get(), false));
                event(player, EV_SELECTOR, GunItem.mode(stack));
            }
            case ACT_FIT -> fit(player, stack, gun.type, arg);
            case ACT_REMOVE -> remove(player, stack, arg);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ attachments

    private static void fit(ServerPlayer player, ItemStack stack, GunType type, int ordinal) {
        if (ordinal < 0 || ordinal >= Attachment.VALUES.length) return;
        Attachment a = Attachment.VALUES[ordinal];
        if (!type.accepts(a)) return;
        Item item = ModRegistry.ATTACHMENTS.get(a).get();
        Inventory inv = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) { slot = i; break; }
        if (slot < 0 && !player.getAbilities().instabuild) return;
        List<Attachment> parts = new ArrayList<>(GunItem.parts(stack));
        Attachment old = GunItem.part(stack, a.slot);
        if (old == a) return;
        if (slot >= 0 && !player.getAbilities().instabuild) inv.getItem(slot).shrink(1);
        if (old != null) {
            parts.remove(old);
            give(player, new ItemStack(ModRegistry.ATTACHMENTS.get(old).get()));
        }
        parts.add(a);
        GunItem.setParts(stack, parts);
        stack.set(ModRegistry.ZERO.get(), 0);
        player.serverLevel().playSound(null, player.blockPosition(), sound("gun_selector"), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    private static void remove(ServerPlayer player, ItemStack stack, int slotOrdinal) {
        if (slotOrdinal < 0 || slotOrdinal >= Attachment.Slot.values().length) return;
        Attachment old = GunItem.part(stack, Attachment.Slot.values()[slotOrdinal]);
        if (old == null) return;
        List<Attachment> parts = new ArrayList<>(GunItem.parts(stack));
        parts.remove(old);
        GunItem.setParts(stack, parts);
        stack.set(ModRegistry.ZERO.get(), 0);
        give(player, new ItemStack(ModRegistry.ATTACHMENTS.get(old).get()));
        player.serverLevel().playSound(null, player.blockPosition(), sound("gun_selector"), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.7f);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (player.getAbilities().instabuild && player.getInventory().contains(stack)) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator() || !player.isAlive()) {
            if (STATES.containsKey(player.getUUID())) forget(player);
            return;
        }
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        shine(player, stack, st);
        boolean pulled = st.trigger || st.pulse;                 // a tap that was over before this tick still counts, once
        st.pulse = false;
        long now = player.serverLevel().getGameTime();
        int slot = player.getInventory().selected;
        if (st.type != gun.type || st.slot != slot) {                       // just drawn: it takes a moment to bring up
            if (st.reload >= 0) event(player, EV_CANCEL, 0);
            st.type = gun.type; st.slot = slot; st.reload = -1; st.latched = st.trigger; st.shots = 0;
            st.nextShot = now + 7;
            st.heat = 0; st.jammed = false;                   // heat and a stoppage belong to the gun just put away
        }
        // the barrel cools whenever it is not being fired this tick
        if (gun.type.beltFed() && st.heat > 0 && now - st.lastShot > 1) {
            st.heat = Math.max(0, st.heat - HEAT_COOL);
            if (st.heat < 0.001f && st.heat != 0) st.heat = 0;
        }
        if (st.reload >= 0) { reloadTick(player, stack, gun.type, st); return; }
        if (now - st.lastShot > 6) st.shots = 0;
        if (!pulled || st.latched) return;
        int mode = GunItem.mode(stack);
        if (st.jammed) {                                            // cooked off: nothing happens until it is cleared
            if (!st.dry) { st.dry = true; event(player, EV_JAM, 1); player.displayClientMessage(Component.translatable("message.steelrain.gun_jammed"), true); }
            st.latched = true;
            return;
        }
        if (muzzleBlocked(player, gun.type)) return;              // barrel against a wall: can't fire, but not latched
        while (st.nextShot <= now + 1e-6) {
            if (mode == GunType.Mode.SAFE || !ready(stack, gun.type)) {
                if (!st.dry) {
                    st.dry = true;
                    event(player, EV_DRY, 0);
                    if (mode == GunType.Mode.SAFE) player.displayClientMessage(Component.translatable("message.steelrain.gun_safe"), true);
                    else if (gun.type.magazineFed() && !GunItem.hasMagazine(stack)) player.displayClientMessage(Component.translatable("message.steelrain.gun_no_magazine"), true);
                }
                st.latched = true;
                break;
            }
            shoot(player, stack, gun.type, st);
            st.nextShot = Math.max(st.nextShot, now - 1) + gun.type.interval();
            if (gun.type.beltFed()) {
                st.heat = Math.min(1f, st.heat + HEAT_PER_SHOT);
                if (st.heat >= 1f) { st.jammed = true; event(player, EV_JAM, 1); st.latched = true; break; }
            }
            if (mode != GunType.Mode.AUTO) { st.latched = true; break; }
        }
    }

    private static boolean ready(ItemStack stack, GunType type) {
        return type.firesFromFeed() ? GunItem.rounds(stack) > 0 : GunItem.chambered(stack);
    }

    /** How far the muzzle reaches in front of the eye, for the "barrel against a wall" check. */
    public static double muzzleReach(GunType type) { return type.pistol() ? 0.55 : 1.2; }

    /** True when there is a solid block right in front of the muzzle, so the shot would hit the wall at the firer's nose. */
    public static boolean muzzleBlocked(net.minecraft.world.entity.player.Player player, GunType type) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(muzzleReach(type))),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    private static void shoot(ServerPlayer player, ItemStack stack, GunType type, State st) {
        ServerLevel level = player.serverLevel();
        boolean creative = player.getAbilities().instabuild;
        int rounds = GunItem.rounds(stack);
        int before = GunItem.loaded(stack);
        Caliber cal = GunItem.caliber(stack);
        if (type.firesFromFeed()) {
            if (!creative || type.action == GunType.Action.SINGLE) stack.set(ModRegistry.ROUNDS.get(), rounds - 1);
        } else if (!creative) {
            if (rounds > 0) stack.set(ModRegistry.ROUNDS.get(), rounds - 1);     // the action feeds the next round
            else stack.set(ModRegistry.CHAMBER.get(), false);
        }
        int kind = type.loads().length > 1 ? 0 : stack.getOrDefault(ModRegistry.LOAD.get(), 0);
        boolean tracer = kind == GunItem.KIND_TRACER || (type.tracerEvery() > 0 ? before % type.tracerEvery() == 0 : before <= type.tracerLast());
        boolean suppressed = GunItem.suppressed(stack);

        // where the barrel points: the line of sight, raised for the range the sights are set to, plus the gun's
        // own scatter and the firer's unsteadiness
        double lift = Math.toDegrees(cal.elevation(GunItem.zeroRange(stack), type.velocityFactor));
        double sigma = type.moa * 0.000291 / 2;
        boolean laser = GunItem.part(stack, Attachment.Slot.SIDE) == Attachment.LASER && stack.getOrDefault(ModRegistry.ACTIVE.get(), false);
        if (!st.aiming) sigma += Math.toRadians(type.pistol() ? 1.1 : 1.6) * (laser ? 0.6 : 1.0);
        Vec3 moved = player.getKnownMovement();
        sigma += Math.min(0.05, Math.hypot(moved.x, moved.z) * 0.12);
        if (!player.onGround() && !player.isPassenger()) sigma += Math.toRadians(2.5);
        if (st.heat > 0.4f) sigma += Math.toRadians(2.2) * (st.heat - 0.4f) / 0.6f;    // a hot barrel walks its shots
        Vec3 eye = player.getEyePosition();
        double speed = cal.speed() * type.velocityFactor;
        float damage = kind == GunItem.KIND_TRAINING ? 0f : type == GunType.SVD || type == GunType.M110 ? 1.1f : 1f;
        for (int i = 0; i < cal.pellets; i++) {
            double spread = cal.pellets > 1 ? sigma + Math.toRadians(1.1) : sigma;
            float pitch = player.getXRot() - (float) lift + (float) Math.toDegrees(level.random.nextGaussian() * spread);
            float yaw = player.getYRot() + (float) Math.toDegrees(level.random.nextGaussian() * spread);
            Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(speed * (0.985 + level.random.nextDouble() * 0.03));
            int flags = (i == 0 ? Payloads.GunShot.PRIMARY : 0) | (tracer ? Payloads.GunShot.TRACER : 0) | (suppressed ? Payloads.GunShot.SUPPRESSED : 0)
                    | cal.ordinal() << Payloads.GunShot.CALIBER_SHIFT;
            PacketDistributor.sendToPlayersNear(level, null, eye.x, eye.y, eye.z, suppressed ? 160 : 420,
                    new Payloads.GunShot(player.getId(), type.ordinal(), flags, eye, vel));
            Bullets.fire(level, player, eye, vel, cal, damage);
        }
        if (cal.rocket()) backblast(level, player, eye);
        st.shots++;
        st.lastShot = level.getGameTime();
        level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT, eye);
    }

    /**
     * The launcher throws as much gas backwards as rocket forwards: anyone close behind it is burned, and with a
     * wall at the firer's back the blast comes straight back at them.
     */
    private static void backblast(ServerLevel level, ServerPlayer player, Vec3 eye) {
        Vec3 back = player.getLookAngle().scale(-1);
        Vec3 from = eye.add(0, -0.1, 0);
        for (int i = 1; i <= 6; i++) {
            Vec3 at = from.add(back.scale(i * 0.6));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 2, 0.15 * i * 0.3, 0.1, 0.15 * i * 0.3, 0.01);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, from.x + back.x, from.y + back.y, from.z + back.z, 12, 0.2, 0.15, 0.2, 0.08);
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(from, from).inflate(5), x -> x != player && x.isAlive())) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(from);
            double d = to.length();
            if (d > 4.5 || d < 1e-3 || to.dot(back) / d < 0.82) continue;                  // within about 35 degrees of straight back
            e.hurt(level.damageSources().explosion(player, player), (float) (9 * (1 - d / 4.5)) + 1);
            e.igniteForSeconds(2);
        }
        var wall = level.clip(new net.minecraft.world.level.ClipContext(from, from.add(back.scale(1.8)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !player.getAbilities().invulnerable) {
            player.hurt(level.damageSources().explosion(null, null), 5f);
            player.displayClientMessage(Component.translatable("message.steelrain.gun_backblast"), true);
        }
    }

    // ------------------------------------------------------------------ reloading

    private static int bestMagazine(ServerPlayer player, GunType type) {
        Inventory inv = player.getInventory();
        int best = -1, most = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MagazineItem m && m.gun == type && MagazineItem.rounds(s) > most) { most = MagazineItem.rounds(s); best = i; }
        }
        return best;
    }

    private static int findAmmo(ServerPlayer player, GunType type) { return findAmmo(player, type.caliber); }

    private static int findAmmo(ServerPlayer player, Caliber cal) {
        Item ammo = ModRegistry.AMMO.get(cal).get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ammo)) return i;
        return -1;
    }

    /** Which rocket a launcher would be loaded with next: the kind it does not hold now if the firer has one, else any. */
    private static int nextLoad(ServerPlayer player, ItemStack stack, GunType type) {
        Caliber[] loads = type.loads();
        boolean creative = player.getAbilities().instabuild;
        boolean loaded = GunItem.rounds(stack) > 0;
        int now = stack.getOrDefault(ModRegistry.LOAD.get(), 0);
        // an empty tube takes the kind it last held if there is one; a loaded tube is changed to the other kind
        for (int k = loaded ? 1 : 0; k < loads.length + (loaded ? 0 : 0); k++) {
            int i = (now + k) % loads.length;
            if (creative || findAmmo(player, loads[i]) >= 0) return i;
        }
        return -1;
    }

    private static void startReload(ServerPlayer player, ItemStack stack, GunType type, State st) {
        if (st.reload >= 0) return;
        if (st.jammed) { st.jammed = false; event(player, EV_JAM, 0); }     // working the action clears the stoppage
        st.heat *= 0.3f;
        boolean creative = player.getAbilities().instabuild;
        if (type.action == GunType.Action.SINGLE) {
            // an empty tube takes a rocket; a loaded one has its rocket drawn and the other kind put in
            int load = nextLoad(player, stack, type);
            if (load < 0) {
                if (GunItem.rounds(stack) <= 0) player.displayClientMessage(Component.translatable("message.steelrain.gun_no_rockets"), true);
                return;
            }
            if (GunItem.rounds(stack) > 0) {
                stack.set(ModRegistry.ROUNDS.get(), 0);
                if (!creative) give(player, new ItemStack(ModRegistry.AMMO.get(GunItem.caliber(stack)).get()));
            }
            st.reload = 0; st.reloadTotal = type.reloadTicks; st.magIn = false;
            st.pendingLoad = load;
            event(player, EV_ROCKET, load);
            return;
        }
        if (type.action == GunType.Action.PUMP) {
            if (GunItem.rounds(stack) >= type.capacity) return;
            if (!creative && findAmmo(player, type) < 0) { player.displayClientMessage(Component.translatable("message.steelrain.gun_no_ammo"), true); return; }
            st.reload = 0; st.reloadTotal = type.reloadTicks;
            event(player, EV_SHELL, 0);
            return;
        }
        int best = bestMagazine(player, type);
        if (!creative) {
            if (best < 0) { player.displayClientMessage(Component.translatable("message.steelrain.gun_no_magazines"), true); return; }
            if (MagazineItem.rounds(player.getInventory().getItem(best)) <= GunItem.rounds(stack)) return;   // the one fitted is the fullest
        } else if (GunItem.rounds(stack) >= type.capacity) {
            return;
        }
        st.reloadEmpty = type.firesFromFeed() || !GunItem.chambered(stack);
        st.reloadTotal = st.reloadEmpty ? type.reloadEmptyTicks : type.reloadTicks;
        st.reload = 0; st.magOut = false; st.magIn = false;
        event(player, st.reloadEmpty ? EV_RELOAD_EMPTY : EV_RELOAD, st.reloadTotal);
    }

    private static void reloadTick(ServerPlayer player, ItemStack stack, GunType type, State st) {
        boolean creative = player.getAbilities().instabuild;
        st.reload++;
        if (type.action == GunType.Action.SINGLE) {
            if (!st.magIn && st.reload >= st.reloadTotal - 12) {
                st.magIn = true;
                Caliber cal = type.loads()[st.pendingLoad];
                int slot = findAmmo(player, cal);
                if (slot < 0 && !creative) { st.reload = -1; event(player, EV_CANCEL, 0); return; }
                if (!creative) player.getInventory().getItem(slot).shrink(1);
                stack.set(ModRegistry.LOAD.get(), st.pendingLoad);
                stack.set(ModRegistry.ROUNDS.get(), 1);
            }
            if (st.reload >= st.reloadTotal) { st.reload = -1; st.latched = st.trigger; }
            return;
        }
        if (type.action == GunType.Action.PUMP) {
            // one shell per cycle; pulling the trigger stops the loading after the shell in hand
            if (st.reload == st.reloadTotal - 3) {
                int slot = findAmmo(player, type);
                if (slot >= 0 || creative) {
                    if (!creative) player.getInventory().getItem(slot).shrink(1);
                    stack.set(ModRegistry.ROUNDS.get(), GunItem.rounds(stack) + 1);
                }
            }
            if (st.reload >= st.reloadTotal) {
                boolean more = GunItem.rounds(stack) < type.capacity && (creative || findAmmo(player, type) >= 0) && !st.trigger;
                if (more) { st.reload = 0; event(player, EV_SHELL, 0); return; }
                st.reload = -1;
                st.latched = st.trigger;
                if (!GunItem.chambered(stack) && GunItem.rounds(stack) > 0) {         // work the action to chamber the first shell
                    stack.set(ModRegistry.ROUNDS.get(), GunItem.rounds(stack) - 1);
                    stack.set(ModRegistry.CHAMBER.get(), true);
                    event(player, EV_PUMP, 0);
                    st.nextShot = player.serverLevel().getGameTime() + 9;
                }
            }
            return;
        }
        int outAt = Math.round(st.reloadTotal * type.magOutAt()), inAt = Math.round(st.reloadTotal * type.magInAt(st.reloadEmpty));
        if (!st.magOut && st.reload >= outAt) {
            st.magOut = true;
            int left = GunItem.rounds(stack);
            if (left >= 0) {
                stack.set(ModRegistry.ROUNDS.get(), -1);
                if (!creative) {
                    ItemStack old = ((MagazineItem) ModRegistry.MAGAZINES.get(type).get()).filled(left);
                    old.set(ModRegistry.LOAD.get(), stack.getOrDefault(ModRegistry.LOAD.get(), 0));
                    give(player, old);
                }
            }
        }
        if (!st.magIn && st.reload >= inAt) {
            st.magIn = true;
            if (creative) {
                stack.set(ModRegistry.ROUNDS.get(), type.capacity);
            } else {
                int best = bestMagazine(player, type);
                if (best < 0) { st.reload = -1; event(player, EV_CANCEL, 0); return; }
                ItemStack mag = player.getInventory().getItem(best);
                stack.set(ModRegistry.ROUNDS.get(), MagazineItem.rounds(mag));
                stack.set(ModRegistry.LOAD.get(), mag.getOrDefault(ModRegistry.LOAD.get(), 0));
                mag.shrink(1);
            }
        }
        if (st.reload >= st.reloadTotal) {
            st.reload = -1;
            st.latched = st.trigger;
            if (type.action == GunType.Action.CLOSED_BOLT && !GunItem.chambered(stack) && GunItem.rounds(stack) > 0) {
                stack.set(ModRegistry.ROUNDS.get(), GunItem.rounds(stack) - 1);
                stack.set(ModRegistry.CHAMBER.get(), true);
            }
        }
    }

    /** A rocket went off (or fell dead): the firer's game stops drawing it. */
    static void rocketEnded(ServerLevel level, net.minecraft.world.entity.Entity shooter, Vec3 at) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 420, new Payloads.GunEvent(shooter.getId(), EV_BURST, 0));
    }

    private static void event(ServerPlayer player, int kind, int arg) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, player.getX(), player.getY(), player.getZ(), 48,
                new Payloads.GunEvent(player.getId(), kind, arg));
    }

    private GunLogic() {}
}
