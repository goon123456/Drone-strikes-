package com.steelrain.entity.vehicle;

import com.steelrain.Config;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.MissileEntity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A missile launcher fired from a Field Laptop that fires over its own nose: on an order it stops, raises the
 * missile (or the container of missiles) about its rear end, fires, and lowers it again for travel. The guidance
 * does the rest, so nothing has to turn towards the target.
 */
public abstract class SrErector extends SrVehicle implements Launcher {
    /** Whether a missile sits on the launcher, so every game can show or hide it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ARMED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SrErector.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private int cooldown, salvoLeft, restTimer, nextTube;
    private double salvoX, salvoZ;
    @Nullable private UUID salvoOwner;
    @Nullable private String salvoOwnerName;

    protected SrErector(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ARMED, false);
    }

    public boolean armed() { return entityData.get(ARMED); }

    protected abstract Item ammo();
    protected abstract MissileProfile profile();
    protected abstract int maxRange();
    protected abstract int energyPerShot();
    /** Ticks between rounds of a salvo. */
    protected abstract int cooldownTicks();
    protected abstract float fireElevation();
    protected abstract float elevateRate();
    /** The point the launcher rail or container tips up about, in vehicle space. */
    protected abstract Vec3 erectorPivot();
    /** Where the missiles leave, in vehicle space with the launcher lowered; several are used in turn. */
    protected abstract Vec3[] tubes();
    protected abstract void launchSound(ServerLevel level, Vec3 at);

    @Override protected boolean hasPowerUnit() { return true; }

    // ------------------------------------------------------------------ Launcher

    @Override public String nameKey() { return getType().getDescriptionId(); }
    @Override public int energyK() { return energy() / 1000; }
    @Override public Vec3 launcherPos() { return position(); }
    @Override public int maxRangeNow() { return (int) Math.round(maxRange() * Config.RANGE_MULTIPLIER.get()); }

    @Override
    public int ammoCount() {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(ammo())) n += inventory.getItem(i).getCount();
        return n;
    }

    @Override
    public int busyTicks() {
        int raising = (int) (Math.abs(fireElevation() - gunPitch()) / elevateRate());
        return cooldown + (salvoLeft > 0 ? raising + salvoLeft * cooldownTicks() : 0);
    }

    @Nullable
    @Override
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (salvoLeft > 0 || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (ammoCount() <= 0) return Component.translatable("message.steelrain.no_ammo");
        if (Config.REQUIRE_ENERGY.get() && energy() < energyPerShot()) return Component.translatable("message.steelrain.no_fuel", energyPerShot());
        if (Math.abs(getX() - xo) + Math.abs(getZ() - zo) > 0.03) return Component.translatable("message.steelrain.moving");
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < minRange()) return Component.translatable("message.steelrain.too_close", minRange());
        if (dist > maxRangeNow()) return Component.translatable("message.steelrain.too_far", maxRangeNow());
        salvoLeft = Math.max(1, Math.min(count, ammoCount()));
        salvoX = x + 0.5;
        salvoZ = z + 0.5;
        salvoOwner = player.getUUID();
        salvoOwnerName = player.getGameProfile().getName();
        if (level() instanceof ServerLevel sl) FlyingWeaponEntity.preload(sl, getX(), getZ());
        return null;
    }

    // ------------------------------------------------------------------ raising and firing

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (tickCount % 5 == 0 || cooldown > 0) entityData.set(ARMED, cooldown < cooldownTicks() - 2 && ammoCount() > 0);
        if (cooldown > 0) cooldown--;
        if (salvoLeft > 0) {
            if (!raiseTo(fireElevation()) || cooldown > 0) return;
            if (tickCount % 100 == 0) FlyingWeaponEntity.preload(level, getX(), getZ());
            if (fire(level)) salvoLeft--;
            else salvoLeft = 0;
            restTimer = 80;
        } else if (restTimer > 0) {
            restTimer--;
        } else {
            raiseTo(0);
        }
    }

    private boolean raiseTo(float elevation) {
        float p = gunPitch(), d = Mth.clamp(elevation - p, -elevateRate(), elevateRate());
        if (Math.abs(d) > 0.001f) setGunPitch(p + d);
        return Math.abs(elevation - gunPitch()) < 0.5f;
    }

    /** A point on the launcher as it is raised now, in the world. */
    protected Vec3 onLauncher(Vec3 p) {
        Vec3 pv = erectorPivot();
        double e = Math.toRadians(gunPitch()), y = p.y - pv.y, z = p.z - pv.z;
        return toWorld(new Vec3(p.x, pv.y + y * Math.cos(e) + z * Math.sin(e), pv.z + z * Math.cos(e) - y * Math.sin(e)));
    }

    private boolean fire(ServerLevel level) {
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) if (inventory.getItem(i).is(ammo())) slot = i;
        if (slot < 0 || !useEnergy(energyPerShot())) return false;
        inventory.removeItem(slot, 1);
        Vec3[] tubes = tubes();
        Vec3 tube = tubes[nextTube++ % tubes.length];
        Vec3 muzzle = onLauncher(tube), dir = onLauncher(tube.add(0, 0, 1)).subtract(muzzle).normalize();
        MissileEntity.launch(level, profile(), muzzle.add(dir.scale(0.8)), salvoX, salvoZ, salvoOwner, salvoOwnerName, salvoLeft <= 1, dir);
        cooldown = cooldownTicks();
        launchSound(level, muzzle);
        Vec3 back = muzzle.subtract(dir.scale(3));
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 30, 0.8, 0.5, 0.8, 0.04);
        level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 40, 1.2, 0.4, 1.2, 0.06);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 20, 0.3, 0.3, 0.3, 0.06);
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(com.steelrain.ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                boolean linked = com.steelrain.item.LaptopItem.toggleVehicle(held, getUUID());
                player.displayClientMessage(Component.translatable(linked ? "message.steelrain.linked" : "message.steelrain.unlinked", getName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Salvo", salvoLeft);
        tag.putInt("Cooldown", cooldown);
        tag.putDouble("SalvoX", salvoX);
        tag.putDouble("SalvoZ", salvoZ);
        if (salvoOwner != null) tag.putUUID("SalvoOwner", salvoOwner);
        if (salvoOwnerName != null) tag.putString("SalvoOwnerName", salvoOwnerName);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        salvoLeft = tag.getInt("Salvo");
        cooldown = tag.getInt("Cooldown");
        salvoX = tag.getDouble("SalvoX");
        salvoZ = tag.getDouble("SalvoZ");
        salvoOwner = tag.hasUUID("SalvoOwner") ? tag.getUUID("SalvoOwner") : null;
        salvoOwnerName = tag.contains("SalvoOwnerName") ? tag.getString("SalvoOwnerName") : null;
    }
}
