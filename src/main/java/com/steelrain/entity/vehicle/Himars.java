package com.steelrain.entity.vehicle;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.item.LaptopItem;
import com.steelrain.util.Sfx;
import com.steelrain.weapon.Launcher;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * M142 HIMARS. Fired from a Field Laptop: the launcher swings round to the target's bearing and raises the pod,
 * then ripples off its rockets one tube at a time. One pod holds six GMLRS rockets or a single ATACMS; when it is
 * empty a new one is loaded from the truck's inventory, which takes thirty seconds.
 */
public class Himars extends SrVehicle implements Launcher {
    public static final int POD_SIZE = 6;
    private static final int RELOAD_TICKS = 20 * 30, COOLDOWN = 12, ENERGY_PER_SHOT = 25_000;
    private static final int MIN_RANGE = 60, MAX_RANGE = 1200, ATACMS_RANGE = 3000;
    private static final float FIRE_ELEVATION = 50f, TRAVERSE_RATE = 3f, ELEVATE_RATE = 1.5f;

    // where things are on the model (vehicle space, metres)
    private static final Vec3 TURRET_PIVOT = new Vec3(0, 1.2, -1.5), POD_PIVOT = new Vec3(0, 1.7, -2.5);
    private static final Vec3[] TUBES = new Vec3[6];
    static {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) TUBES[i * 2 + j] = new Vec3(-0.68 + i * 0.68, 1.96 + j * 0.5, 1.1);
        }
    }

    private int pod, reload, cooldown, salvoLeft, restTimer;
    private boolean podAtacms;
    private double salvoX, salvoZ;
    @Nullable private UUID salvoOwner;
    @Nullable private String salvoOwnerName;

    public Himars(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override public float maxHealth() { return 300; }
    @Override public float tankLitres() { return 100; }
    @Override public int batteryFe() { return 500_000; }
    @Override protected double wheelbase() { return 4.05; }
    @Override protected double length() { return 7.0; }
    @Override protected double width() { return 2.4; }
    @Override public String modelName() { return "himars"; }
    @Override protected ItemStack asItem() { return new ItemStack(ModRegistry.HIMARS_ITEM.get()); }

    /** Driver on the left of the cab, commander on the right. */
    private static final Vec3[] SEATS = {new Vec3(0.5, 0.75, 2.45), new Vec3(-0.5, 0.75, 2.45)};
    @Override protected Vec3[] seats() { return SEATS; }

    // ------------------------------------------------------------------ Launcher

    @Override public String nameKey() { return getType().getDescriptionId(); }
    @Override public int ammoCount() { return pod; }
    @Override public int energyK() { return energy() / 1000; }
    @Override public int minRange() { return MIN_RANGE; }
    @Override public Vec3 launcherPos() { return position(); }

    @Override
    public int maxRangeNow() {
        return (int) Math.round((podAtacms ? ATACMS_RANGE : MAX_RANGE) * Config.RANGE_MULTIPLIER.get());
    }

    @Override
    public int busyTicks() {
        int laying = (int) (Math.abs(turretYaw()) / TRAVERSE_RATE + Math.abs(FIRE_ELEVATION - gunPitch()) / ELEVATE_RATE);
        return cooldown + (salvoLeft > 0 ? laying + salvoLeft * COOLDOWN : 0) + (pod <= 0 ? reload : 0);
    }

    @Nullable
    @Override
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (salvoLeft > 0 || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (pod <= 0) {
            return reload > 0 ? Component.translatable("message.steelrain.reloading", (reload + 19) / 20)
                    : Component.translatable("message.steelrain.no_ammo");
        }
        if (Config.REQUIRE_ENERGY.get() && energy() < ENERGY_PER_SHOT) return Component.translatable("message.steelrain.no_fuel", ENERGY_PER_SHOT);
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < MIN_RANGE) return Component.translatable("message.steelrain.too_close", MIN_RANGE);
        if (dist > maxRangeNow()) return Component.translatable("message.steelrain.too_far", maxRangeNow());
        salvoLeft = Math.max(1, Math.min(count, pod));
        salvoX = x + 0.5;
        salvoZ = z + 0.5;
        salvoOwner = player.getUUID();
        salvoOwnerName = player.getGameProfile().getName();
        if (level() instanceof ServerLevel sl) FlyingWeaponEntity.preload(sl, getX(), getZ());
        return null;
    }

    // ------------------------------------------------------------------ the launcher at work

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (cooldown > 0) cooldown--;
        podTick(level);
        if (salvoLeft > 0) {
            // Lay first: swing the launcher round to the target's bearing and raise the pod. Fire once it is there.
            float bearing = (float) Math.toDegrees(Math.atan2(-(salvoX - getX()), salvoZ - getZ()));
            boolean laid = slew(Mth.wrapDegrees(bearing - getYRot()), FIRE_ELEVATION);
            if (!laid || cooldown > 0) return;
            if (tickCount % 100 == 0) FlyingWeaponEntity.preload(level, getX(), getZ());
            if (fireOne(level)) salvoLeft--;
            else salvoLeft = 0;
            restTimer = 60;
        } else if (restTimer > 0) {
            restTimer--;
        } else {
            slew(0, 0);       // back to the travelling position
        }
    }

    /** Moves the launcher towards a bearing and elevation at its own pace. @return whether it is there */
    private boolean slew(float yaw, float pitch) {
        float y = turretYaw(), p = gunPitch();
        float dy = Mth.clamp(Mth.wrapDegrees(yaw - y), -TRAVERSE_RATE, TRAVERSE_RATE);
        // raise or lower the pod only once it is roughly on its bearing, as the crane arm needs room to swing
        float dp = Math.abs(Mth.wrapDegrees(yaw - y)) < 20 ? Mth.clamp(pitch - p, -ELEVATE_RATE, ELEVATE_RATE) : Mth.clamp(-p, -ELEVATE_RATE, 0);
        if (dy != 0) setTurretYaw(y + dy);
        if (dp != 0) setGunPitch(p + dp);
        return Math.abs(Mth.wrapDegrees(yaw - turretYaw())) < 0.5f && Math.abs(pitch - gunPitch()) < 0.5f;
    }

    /** A point on the launcher (vehicle space, travelling position) as it is now, raised and turned, in the world. */
    private Vec3 launcherPoint(Vec3 p) {
        double e = Math.toRadians(gunPitch()), r = Math.toRadians(turretYaw());
        double y = p.y - POD_PIVOT.y, z = p.z - POD_PIVOT.z;
        Vec3 raised = new Vec3(p.x, POD_PIVOT.y + y * Math.cos(e) + z * Math.sin(e), POD_PIVOT.z + z * Math.cos(e) - y * Math.sin(e));
        double x = raised.x - TURRET_PIVOT.x, zz = raised.z - TURRET_PIVOT.z;
        Vec3 turned = new Vec3(TURRET_PIVOT.x + x * Math.cos(r) - zz * Math.sin(r), raised.y, TURRET_PIVOT.z + x * Math.sin(r) + zz * Math.cos(r));
        return toWorld(turned);
    }

    private boolean fireOne(ServerLevel level) {
        if (pod <= 0 || !useEnergy(ENERGY_PER_SHOT)) return false;
        int tube = podAtacms ? 2 : POD_SIZE - pod;
        pod--;
        Vec3 muzzle = launcherPoint(TUBES[Mth.clamp(tube, 0, 5)]);
        Vec3 dir = launcherPoint(TUBES[Mth.clamp(tube, 0, 5)].add(0, 0, 1)).subtract(muzzle).normalize();
        MissileProfile profile = podAtacms ? MissileProfile.ATACMS : MissileProfile.GMLRS;
        MissileEntity.launch(level, profile, muzzle.add(dir.scale(0.6)), salvoX, salvoZ, salvoOwner, salvoOwnerName, salvoLeft <= 1, dir);
        cooldown = COOLDOWN;
        Sfx.gunfire(level, muzzle, "medium_rocket_fire", "rpg_far", "rpg_veryfar");
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 25, 0.6, 0.3, 0.6, 0.03);
        Vec3 back = muzzle.subtract(dir.scale(4.5));
        level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 30, 0.8, 0.5, 0.8, 0.05);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.05);
        return true;
    }

    /** An empty pod is swapped for a fresh one from the truck's own load, which takes half a minute. */
    private void podTick(ServerLevel level) {
        if (pod > 0 || salvoLeft > 0) return;
        if (reload > 0) {
            if (--reload > 0) return;
            int slot = ammoSlot();
            if (slot < 0) return;
            if (inventory.getItem(slot).is(ModRegistry.ATACMS_MISSILE.get())) {
                inventory.removeItem(slot, 1);
                pod = 1;
                podAtacms = true;
            } else {
                podAtacms = false;
                for (int i = 0; i < inventory.getContainerSize() && pod < POD_SIZE; i++) {
                    if (inventory.getItem(i).is(ModRegistry.GMLRS_ROCKET.get())) pod += inventory.removeItem(i, POD_SIZE - pod).getCount();
                }
            }
            Sfx.play(level, position(), "cannon_reload", 3f, 0.9f);
        } else if (ammoSlot() >= 0) {
            reload = RELOAD_TICKS;
            Sfx.play(level, position(), "missile_reload", 3f, 0.8f);
        }
    }

    private int ammoSlot() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(ModRegistry.GMLRS_ROCKET.get()) || s.is(ModRegistry.ATACMS_MISSILE.get())) return i;
        }
        return -1;
    }

    /** A Field Laptop links or unlinks the truck; everything else is the vehicle's usual handling. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                boolean linked = LaptopItem.toggleVehicle(stack, getUUID());
                player.displayClientMessage(Component.translatable(linked ? "message.steelrain.linked" : "message.steelrain.unlinked", getName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    protected void destroyed(ServerLevel level) {
        // rockets still in the pod cook off
        if (pod > 0) com.steelrain.util.Blast.detonate(level, launcherPoint(new Vec3(0, 2.2, -1.0)), 2f + pod, 1, 20f + pod * 6, 7, true, this, null);
        super.destroyed(level);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Pod", pod);
        tag.putBoolean("PodAtacms", podAtacms);
        tag.putInt("Reload", reload);
        tag.putInt("Salvo", salvoLeft);
        tag.putDouble("SalvoX", salvoX);
        tag.putDouble("SalvoZ", salvoZ);
        if (salvoOwner != null) tag.putUUID("SalvoOwner", salvoOwner);
        if (salvoOwnerName != null) tag.putString("SalvoOwnerName", salvoOwnerName);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        pod = tag.getInt("Pod");
        podAtacms = tag.getBoolean("PodAtacms");
        reload = tag.getInt("Reload");
        salvoLeft = tag.getInt("Salvo");
        salvoX = tag.getDouble("SalvoX");
        salvoZ = tag.getDouble("SalvoZ");
        salvoOwner = tag.hasUUID("SalvoOwner") ? tag.getUUID("SalvoOwner") : null;
        salvoOwnerName = tag.contains("SalvoOwnerName") ? tag.getString("SalvoOwnerName") : null;
    }
}
