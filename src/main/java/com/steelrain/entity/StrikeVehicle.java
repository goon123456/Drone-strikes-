package com.steelrain.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.item.LaptopItem;
import com.steelrain.weapon.Launcher;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Artillery and missile launchers as Superb Warfare vehicles. Superb Warfare provides the vehicle itself
 * (container, crowbar, driving, inventory, energy, damage); Steel Rain adds indirect fire: link the vehicle to a
 * Field Laptop, pick a target on the map, and it lays its launcher and fires Steel Rain munitions from its inventory.
 */
public class StrikeVehicle extends VehicleEntity implements Launcher {
    public enum Kind {
        //        min   max   FE/shot cooldown aim  elevation muzzleHeight muzzleLength
        M109(40, 600, 0, 0, 0, 0f, 2.4, 5.0),
        MORTAR(30, 400, 0, 0, 0, 0f, 1.0, 1.7),
        HIMARS(60, 1200, 25_000, 12, 50, 50f, 2.4, 3.6),
        TOMAHAWK(100, 6000, 125_000, 60, 70, 85f, 2.2, 7.0),
        ISKANDER(150, 8000, 250_000, 100, 80, 88f, 2.8, 7.5);

        final int minRange, maxRange, energyPerShot, cooldown, aimTicks;
        final float elevation;
        final double muzzleHeight, muzzleLength;

        Kind(int minRange, int maxRange, int energyPerShot, int cooldown, int aimTicks, float elevation, double muzzleHeight, double muzzleLength) {
            this.minRange = minRange;
            this.maxRange = maxRange;
            this.energyPerShot = energyPerShot;
            this.cooldown = cooldown;
            this.aimTicks = aimTicks;
            this.elevation = elevation;
            this.muzzleHeight = muzzleHeight;
            this.muzzleLength = muzzleLength;
        }

        /** Only the HIMARS pod swings round on a laptop order; the missile launchers fire over their own nose. */
        boolean traverses() { return this == HIMARS; }

        /** Laid and fired by a gunner in the seat. */
        public boolean manual() { return this == M109 || this == MORTAR; }

        /** Muzzle velocity (blocks per tick) of each propellant charge. */
        public double[] charges() { return this == MORTAR ? new double[]{2.0, 3.0, 4.5} : new double[]{3.0, 4.2, 5.5}; }

        public double minElevation() { return this == MORTAR ? 45 : -3; }
        public double maxElevation() { return this == MORTAR ? 85 : 70; }
    }

    private final Kind kind;
    private int cooldown;
    private int salvoLeft;
    private int aimLeft;
    private int restTimer;
    private double salvoX, salvoZ;
    @Nullable private UUID salvoOwner;
    @Nullable private String salvoOwnerName;

    public StrikeVehicle(EntityType<?> type, Level level) {
        super(type, level);
        this.kind = switch (EntityType.getKey(type).getPath()) {
            case "himars" -> Kind.HIMARS;
            case "tomahawk_launcher" -> Kind.TOMAHAWK;
            case "iskander_launcher" -> Kind.ISKANDER;
            case "mortar_120" -> Kind.MORTAR;
            default -> Kind.M109;
        };
    }

    // ---- ammunition from the Superb Warfare inventory ----

    private boolean accepts(ItemStack s) {
        return switch (kind) {
            case M109 -> s.is(ModRegistry.SHELL_155MM.get()) || s.is(ModRegistry.SHELL_CLUSTER.get())
                    || s.is(ModRegistry.SHELL_SMOKE.get()) || s.is(ModRegistry.SHELL_INCENDIARY.get());
            case HIMARS -> s.is(ModRegistry.GMLRS_ROCKET.get()) || s.is(ModRegistry.ATACMS_MISSILE.get());
            case TOMAHAWK -> s.is(ModRegistry.TOMAHAWK_MISSILE.get());
            case ISKANDER -> s.is(ModRegistry.ISKANDER_MISSILE.get());
            case MORTAR -> s.is(ModRegistry.MORTAR_SHELL.get());
        };
    }

    /** First inventory slot holding something this launcher fires, or -1. */
    private int ammoSlot() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (accepts(getItem(i))) return i;
        }
        return -1;
    }

    private MissileProfile profileOf(ItemStack round) {
        return ModRegistry.profileOfAmmo(round, switch (kind) {
            case M109 -> MissileProfile.SHELL;
            case HIMARS -> MissileProfile.GMLRS;
            case TOMAHAWK -> MissileProfile.TOMAHAWK;
            case ISKANDER -> MissileProfile.ISKANDER;
            case MORTAR -> MissileProfile.MORTAR_SHELL;
        });
    }

    private boolean charged(int fe) {
        return !Config.REQUIRE_ENERGY.get() || fe <= 0 || getEnergy() >= fe;
    }

    // ---- Launcher ----

    public Kind kind() { return kind; }
    @Override public boolean manual() { return kind.manual(); }
    @Override public String nameKey() { return getType().getDescriptionId(); }
    @Override public int energyK() { return kind.energyPerShot == 0 ? 0 : getEnergy() / 1000; }
    @Override public int minRange() { return kind.minRange; }
    @Override public int busyTicks() { return cooldown + aimLeft + salvoLeft * kind.cooldown + (pod <= 0 ? reload : 0); }
    @Override public Vec3 launcherPos() { return position(); }

    @Override
    public int ammoCount() {
        if (kind == Kind.HIMARS) return pod;
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (accepts(getItem(i))) n += getItem(i).getCount();
        }
        return n;
    }

    @Override
    public int maxRangeNow() {
        int base = kind == Kind.HIMARS && podAtacms ? 3000 : kind.maxRange;
        return (int) Math.round(base * Config.RANGE_MULTIPLIER.get());
    }

    @Nullable
    @Override
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (isWreck()) return Component.translatable("message.steelrain.no_signal");
        if (kind.manual()) return solution(x, z);
        if (salvoLeft > 0 || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (kind == Kind.HIMARS && pod <= 0) {
            return reload > 0 ? Component.translatable("message.steelrain.reloading", (reload + 19) / 20)
                    : Component.translatable("message.steelrain.no_ammo");
        }
        if (ammoCount() <= 0) return Component.translatable("message.steelrain.no_ammo");
        if (!charged(kind.energyPerShot)) return Component.translatable("message.steelrain.no_fuel", kind.energyPerShot);
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < kind.minRange) return Component.translatable("message.steelrain.too_close", kind.minRange);
        if (dist > maxRangeNow()) return Component.translatable("message.steelrain.too_far", maxRangeNow());
        salvoLeft = Math.max(1, Math.min(Math.min(count, 32), ammoCount()));
        salvoX = x + 0.5;
        salvoZ = z + 0.5;
        salvoOwner = player.getUUID();
        salvoOwnerName = player.getGameProfile().getName();
        aimLeft = kind.aimTicks;
        if (level() instanceof ServerLevel sl) FlyingWeaponEntity.preload(sl, getX(), getZ());
        return null;
    }

    /**
     * The numbers a gun crew needs for a target: compass bearing, and for each charge that can reach it the
     * elevation and time of flight. Worked out from where the gun stands and how high the target is.
     */
    private Component solution(int x, int z) {
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < kind.minRange) return Component.translatable("message.steelrain.too_close", kind.minRange);
        double targetY = level().hasChunk(x >> 4, z >> 4)
                ? level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) : getY();
        double dy = targetY - (getY() + kind.muzzleHeight);
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        StringBuilder sb = new StringBuilder();
        double[] charges = kind.charges();
        for (int i = 0; i < charges.length; i++) {
            double e = elevationFor(kind, charges[i], d, dy);
            if (Double.isNaN(e)) continue;
            double tof = d / (charges[i] * Math.cos(Math.toRadians(e))) / 20.0;
            if (sb.length() > 0) sb.append("   ");
            sb.append(String.format("C%d %.1f\u00B0 %.0fs", i + 1, e, tof));
        }
        if (sb.length() == 0) return Component.translatable("message.steelrain.too_far", (int) d);
        return Component.translatable("message.steelrain.solution", String.format("%.1f", bearing), (int) d, sb.toString());
    }

    /**
     * Elevation in degrees that lands a shell {@code d} blocks away and {@code dy} blocks higher, or NaN if this
     * charge cannot do it within the gun's elevation limits. Howitzers use the flat trajectory when they can;
     * mortars only fire high.
     */
    public static double elevationFor(Kind kind, double v, double d, double dy) {
        double g = MissileEntity.GRAVITY;
        double disc = v * v * v * v - g * (g * d * d + 2 * dy * v * v);
        if (disc < 0) return Double.NaN;
        double root = Math.sqrt(disc);
        double low = Math.toDegrees(Math.atan((v * v - root) / (g * d)));
        double high = Math.toDegrees(Math.atan((v * v + root) / (g * d)));
        if (low >= kind.minElevation() && low <= kind.maxElevation()) return low;
        if (high >= kind.minElevation() && high <= kind.maxElevation()) return high;
        return Double.NaN;
    }

    // ---- HIMARS pod: six rockets or one ATACMS, then a timed reload from the inventory ----

    public static final int POD_SIZE = 6;
    private static final int RELOAD_TICKS = 20 * 30;
    private int pod;
    private boolean podAtacms;
    private int reload;

    private void podTick() {
        if (pod > 0 || salvoLeft > 0) return;
        if (reload > 0) {
            if (--reload > 0) return;
            // The fresh pod is in place: take its contents out of the inventory.
            int slot = ammoSlot();
            if (slot < 0) return;
            if (getItem(slot).is(ModRegistry.ATACMS_MISSILE.get())) {
                removeItem(slot, 1);
                pod = 1;
                podAtacms = true;
            } else {
                podAtacms = false;
                for (int i = 0; i < getContainerSize() && pod < POD_SIZE; i++) {
                    if (getItem(i).is(ModRegistry.GMLRS_ROCKET.get())) pod += removeItem(i, POD_SIZE - pod).getCount();
                }
            }
            level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 2f, 0.7f);
        } else if (ammoSlot() >= 0) {
            reload = RELOAD_TICKS;
            level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 2f, 0.6f);
        }
    }

    /** Direction the launcher has to point in to fire: at the target for turreted guns, straight ahead for the rest. */
    private Vec3 layDirection() {
        double yaw;
        if (kind.traverses()) {
            yaw = Mth.atan2(-(salvoX - getX()), salvoZ - getZ());
        } else {
            yaw = getYRot() * Mth.DEG_TO_RAD;
        }
        double e = kind.elevation * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw) * Math.cos(e), Math.sin(e), Math.cos(yaw) * Math.cos(e));
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (!(level() instanceof ServerLevel level) || isWreck()) return;
        if (kind.manual()) return; // the gunner lays and fires it through Superb Warfare's own controls
        if (cooldown > 0) cooldown--;
        if (kind == Kind.HIMARS) podTick();
        if (salvoLeft > 0) {
            // Lay the launcher first (turret swings round, container or missile raises), then fire the salvo.
            if (kind.aimTicks > 0) turretAutoAimFromVector(layDirection());
            if (aimLeft > 0) {
                aimLeft--;
                return;
            }
            if (tickCount % 100 == 0) FlyingWeaponEntity.preload(level, getX(), getZ());
            if (cooldown == 0) {
                if (fireOne(level)) salvoLeft--;
                else salvoLeft = 0;
                restTimer = 80;
            }
        } else if (restTimer > 0 && kind.aimTicks > 0) {
            // Back to the travelling position.
            if (--restTimer < 40) {
                double yaw = getYRot() * Mth.DEG_TO_RAD;
                turretAutoAimFromVector(new Vec3(-Math.sin(yaw), 0.02, Math.cos(yaw)));
            }
        }
    }

    private boolean fireOne(ServerLevel level) {
        ItemStack round;
        if (kind == Kind.HIMARS) {
            if (pod <= 0 || !charged(kind.energyPerShot)) return false;
            pod--;
            round = new ItemStack(podAtacms ? ModRegistry.ATACMS_MISSILE.get() : ModRegistry.GMLRS_ROCKET.get());
        } else {
            int slot = ammoSlot();
            if (slot < 0 || !charged(kind.energyPerShot)) return false;
            round = removeItem(slot, 1);
        }
        if (Config.REQUIRE_ENERGY.get() && kind.energyPerShot > 0) consumeEnergy(kind.energyPerShot);
        Vec3 dir = layDirection();
        Vec3 muzzle = position().add(0, kind.muzzleHeight, 0).add(dir.scale(kind.muzzleLength));
        MissileEntity.launch(level, profileOf(round), muzzle, salvoX, salvoZ, salvoOwner, salvoOwnerName, salvoLeft <= 1,
                kind == Kind.TOMAHAWK ? new Vec3(0, 1, 0) : dir);
        cooldown = kind.cooldown;
        boolean gun = false;
        level.playSound(null, blockPosition(), gun ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.NEUTRAL, 6f, gun ? 1.6f : 0.6f);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 20, 0.5, 0.3, 0.5, 0.03);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.05);
        return true;
    }

    /** Right-click with a Field Laptop links or unlinks this vehicle; everything else is Superb Warfare's. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                boolean linked = LaptopItem.toggleVehicle(stack, getUUID());
                player.displayClientMessage(Component.translatable(
                        linked ? "message.steelrain.linked" : "message.steelrain.unlinked", getName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SrCooldown", cooldown);
        tag.putInt("SrSalvo", salvoLeft);
        tag.putInt("SrAim", aimLeft);
        tag.putInt("SrPod", pod);
        tag.putBoolean("SrPodAtacms", podAtacms);
        tag.putInt("SrReload", reload);
        tag.putDouble("SrX", salvoX);
        tag.putDouble("SrZ", salvoZ);
        if (salvoOwner != null) tag.putUUID("SrOwner", salvoOwner);
        if (salvoOwnerName != null) tag.putString("SrOwnerName", salvoOwnerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cooldown = tag.getInt("SrCooldown");
        salvoLeft = tag.getInt("SrSalvo");
        aimLeft = tag.getInt("SrAim");
        pod = tag.getInt("SrPod");
        podAtacms = tag.getBoolean("SrPodAtacms");
        reload = tag.getInt("SrReload");
        salvoX = tag.getDouble("SrX");
        salvoZ = tag.getDouble("SrZ");
        salvoOwner = tag.hasUUID("SrOwner") ? tag.getUUID("SrOwner") : null;
        salvoOwnerName = tag.contains("SrOwnerName") ? tag.getString("SrOwnerName") : null;
    }
}
