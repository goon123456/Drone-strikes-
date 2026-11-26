package com.steelrain.entity.vehicle;

import com.steelrain.entity.MissileEntity;
import com.steelrain.weapon.Launcher;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A gun laid and fired by hand, Arma Reforger style. The gunner looks where the gun should point and the turret and
 * barrel follow at their own slow pace; left click fires the round in the breech, and the crew loads the next one of
 * the chosen type from the vehicle's store. Nothing guides the shell: it leaves the muzzle at the charge's velocity and
 * falls under gravity, so the range table and the Field Laptop's firing solution are what put it on target.
 */
public abstract class SrGun extends SrVehicle implements Launcher {
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(SrGun.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHELL = SynchedEntityData.defineId(SrGun.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOADED = SynchedEntityData.defineId(SrGun.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOADING = SynchedEntityData.defineId(SrGun.class, EntityDataSerializers.INT);

    public static final int ACTION_FIRE = 1, ACTION_CHARGE_UP = 2, ACTION_CHARGE_DOWN = 3, ACTION_NEXT_SHELL = 4;

    protected SrGun(EntityType<?> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ what each gun defines

    /** The seat that lays and fires the gun. */
    public abstract int gunnerSeat();

    /** Muzzle velocity of each propellant charge, blocks per tick. */
    public abstract double[] charges();

    public abstract double minElevation();

    public abstract double maxElevation();

    /** Degrees per tick the turret turns and the barrel rises or drops. */
    protected abstract float traverseRate();

    protected abstract float elevateRate();

    /** Pivots and the muzzle in vehicle space with the gun in its travelling position. */
    protected abstract Vec3 turretPivot();

    protected abstract Vec3 barrelPivot();

    protected abstract Vec3 muzzle();

    protected abstract int reloadTicks();

    /** Rounds it takes, in the order the shell-type key steps through them. */
    public abstract List<Item> shells();

    /** Range-table step for the gun sight. */
    public int rangeStep() { return 50; }

    protected abstract void fireEffects(ServerLevel level, Vec3 muzzle, Vec3 dir);

    // ------------------------------------------------------------------ state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CHARGE, 0);
        b.define(SHELL, 0);
        b.define(LOADED, -1);
        b.define(LOADING, 0);
    }

    public int charge() { return entityData.get(CHARGE); }
    public int selectedShell() { return entityData.get(SHELL); }
    /** Shell type in the breech, or -1 when it is empty. */
    public int loaded() { return entityData.get(LOADED); }
    /** Ticks left until the round being loaded is in. */
    public int loading() { return entityData.get(LOADING); }

    public boolean isGunner(Entity e) { return hasPassenger(e) && seatOf(e) == gunnerSeat(); }

    @Override
    protected Vec3 seatPosition(int seat) {
        return seat == gunnerSeat() ? onTurret(seats()[seat], false) : seats()[seat];
    }

    /** A point on the turret (and, if {@code onBarrel}, the barrel) as it is laid now, in vehicle space. */
    public Vec3 onTurret(Vec3 p, boolean onBarrel) {
        Vec3 tp = turretPivot(), bp = barrelPivot();
        if (onBarrel) {
            double e = Math.toRadians(gunPitch());
            double y = p.y - bp.y, z = p.z - bp.z;
            p = new Vec3(p.x, bp.y + y * Math.cos(e) + z * Math.sin(e), bp.z + z * Math.cos(e) - y * Math.sin(e));
        }
        double r = Math.toRadians(turretYaw());
        double x = p.x - tp.x, z = p.z - tp.z;
        return new Vec3(tp.x + x * Math.cos(r) - z * Math.sin(r), p.y, tp.z + x * Math.sin(r) + z * Math.cos(r));
    }

    /** Where the barrel points, in the world. */
    public Vec3 gunDirection() {
        Vec3 m = toWorld(onTurret(muzzle(), true)), ahead = toWorld(onTurret(muzzle().add(0, 0, 1), true));
        return ahead.subtract(m).normalize();
    }

    // ------------------------------------------------------------------ crew at work

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        Entity gunner = inSeat(gunnerSeat());
        if (gunner != null) {
            // the gunner looks where the gun should go; the turret and barrel follow at their own pace
            float wantYaw = Mth.wrapDegrees(gunner.getYRot() - getYRot());
            float wantPitch = (float) Mth.clamp(-gunner.getXRot(), minElevation(), maxElevation());
            float dy = Mth.clamp(Mth.wrapDegrees(wantYaw - turretYaw()), -traverseRate(), traverseRate());
            float dp = Mth.clamp(wantPitch - gunPitch(), -elevateRate(), elevateRate());
            if (Math.abs(dy) > 0.01f) setTurretYaw(turretYaw() + dy);
            if (Math.abs(dp) > 0.01f) setGunPitch(gunPitch() + dp);
        } else if (getPassengers().isEmpty() && Math.abs(gunPitch()) > 0.01f) {
            setGunPitch(gunPitch() - Mth.clamp(gunPitch(), -elevateRate(), elevateRate()));   // left alone: barrel down
        }
        loadTick(level);
    }

    /** The crew rams the next round of the chosen type while there is one in the store and someone aboard. */
    private void loadTick(ServerLevel level) {
        if (loaded() >= 0) return;
        int timer = loading();
        Item want = shells().get(Mth.clamp(selectedShell(), 0, shells().size() - 1));
        int slot = slotOf(want);
        if (timer > 0) {
            if (slot < 0) { entityData.set(LOADING, 0); return; }       // the last one was taken out meanwhile
            entityData.set(LOADING, --timer);
            if (timer == 0) {
                inventory.removeItem(slot, 1);
                entityData.set(LOADED, selectedShell());
                com.steelrain.util.Sfx.play(level, position(), "cannon_reload", 2.5f, 0.85f);
            }
        } else if (slot >= 0 && !getPassengers().isEmpty()) {
            entityData.set(LOADING, reloadTicks());
        }
    }

    private int slotOf(Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) return i;
        return -1;
    }

    @Override
    public void action(ServerPlayer player, int action) {
        super.action(player, action);
        if (!isGunner(player)) return;
        switch (action) {
            case ACTION_FIRE -> fire(player);
            case ACTION_CHARGE_UP, ACTION_CHARGE_DOWN -> {
                int n = charges().length;
                entityData.set(CHARGE, Math.floorMod(charge() + (action == ACTION_CHARGE_UP ? 1 : -1), n));
            }
            case ACTION_NEXT_SHELL -> {
                entityData.set(SHELL, (selectedShell() + 1) % shells().size());
                if (loading() > 0) entityData.set(LOADING, 0);          // start ramming the new type instead
            }
            default -> { }
        }
    }

    private void fire(ServerPlayer gunner) {
        if (!(level() instanceof ServerLevel level) || loaded() < 0) return;
        Item round = shells().get(Mth.clamp(loaded(), 0, shells().size() - 1));
        Vec3 dir = gunDirection();
        Vec3 muzzle = toWorld(onTurret(muzzle(), true));
        MissileProfile profile = profileOf(new ItemStack(round));
        MissileEntity.shoot(level, profile, muzzle, dir.scale(charges()[Mth.clamp(charge(), 0, charges().length - 1)]),
                gunner.getUUID(), gunner.getGameProfile().getName());
        entityData.set(LOADED, -1);
        markFired();
        fireEffects(level, muzzle, dir);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 18, 0.4, 0.4, 0.4, 0.04);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 10, 0.15, 0.15, 0.15, 0.08);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x + dir.x, muzzle.y + dir.y, muzzle.z + dir.z, 12, 0.5, 0.5, 0.5, 0.05);
    }

    protected abstract MissileProfile profileOf(ItemStack round);

    // ------------------------------------------------------------------ the Field Laptop: firing solutions only

    @Override public boolean manual() { return true; }
    @Override public String nameKey() { return getType().getDescriptionId(); }
    @Override public int energyK() { return 0; }
    @Override public int busyTicks() { return 0; }
    @Override public Vec3 launcherPos() { return position(); }

    @Override
    public int ammoCount() {
        int n = loaded() >= 0 ? 1 : 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (shells().stream().anyMatch(s::is)) n += s.getCount();
        }
        return n;
    }

    @Override
    public int maxRangeNow() {
        double v = charges()[charges().length - 1];
        return (int) (v * v / MissileEntity.GRAVITY);
    }

    /** The laptop cannot fire a hand-laid gun: it works out bearing, range and, per charge, elevation and flight time. */
    @Nullable
    @Override
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < minRange()) return Component.translatable("message.steelrain.too_close", minRange());
        double targetY = level().hasChunk(x >> 4, z >> 4)
                ? level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) : getY();
        double dy = targetY - toWorld(muzzle()).y;
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        StringBuilder sb = new StringBuilder();
        double[] charges = charges();
        for (int i = 0; i < charges.length; i++) {
            double e = elevationFor(charges[i], d, dy, minElevation(), maxElevation());
            if (Double.isNaN(e)) continue;
            double tof = d / (charges[i] * Math.cos(Math.toRadians(e))) / 20.0;
            if (sb.length() > 0) sb.append("   ");
            sb.append(String.format("C%d %.1f° %.0fs", i + 1, e, tof));
        }
        if (sb.length() == 0) return Component.translatable("message.steelrain.too_far", (int) d);
        return Component.translatable("message.steelrain.solution", String.format("%.1f", bearing), (int) d, sb.toString());
    }

    /**
     * Elevation in degrees that lands a shell {@code d} blocks away and {@code dy} higher, or NaN if the charge
     * cannot do it within the gun's limits. Uses the flat trajectory where it can, else the high one.
     */
    public static double elevationFor(double v, double d, double dy, double min, double max) {
        double g = MissileEntity.GRAVITY;
        double disc = v * v * v * v - g * (g * d * d + 2 * dy * v * v);
        if (disc < 0) return Double.NaN;
        double root = Math.sqrt(disc);
        double low = Math.toDegrees(Math.atan((v * v - root) / (g * d)));
        double high = Math.toDegrees(Math.atan((v * v + root) / (g * d)));
        if (low >= min && low <= max) return low;
        if (high >= min && high <= max) return high;
        return Double.NaN;
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CHARGE, tag.getInt("Charge"));
        entityData.set(SHELL, tag.getInt("ShellType"));
        entityData.set(LOADED, tag.contains("Loaded") ? tag.getInt("Loaded") : -1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Charge", charge());
        tag.putInt("ShellType", selectedShell());
        tag.putInt("Loaded", loaded());
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(com.steelrain.ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                boolean linked = com.steelrain.item.LaptopItem.toggleVehicle(held, getUUID());
                player.displayClientMessage(Component.translatable(linked ? "message.steelrain.linked" : "message.steelrain.unlinked", getName()), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }
}
