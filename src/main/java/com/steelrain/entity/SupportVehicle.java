package com.steelrain.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.item.LaptopItem;
import com.steelrain.util.Jamming;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.Launcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Support systems as Superb Warfare vehicles: the UAV Ground Station and Orlan Catapult (which launch Steel Rain
 * aircraft on a Field Laptop order) and the three jammers.
 */
public class SupportVehicle extends VehicleEntity implements Launcher {
    public enum Kind { GROUND_STATION, ORLAN_CATAPULT, JAMMER, TRENCH_JAMMER, KRASUKHA }

    /** Gap between the front of a ground station and the first runway block. */
    public static final int RUNWAY_GAP = 3;

    private final Kind kind;
    private int cooldown;
    private boolean pending;
    private double pendingX, pendingZ;
    @Nullable private UUID pendingOwner;
    @Nullable private String pendingOwnerName;
    @Nullable private UUID aircraftId;
    private boolean switchedOn = true;
    @Nullable private UUID ownerId;
    @Nullable private String ownerName;

    public SupportVehicle(EntityType<?> type, Level level) {
        super(type, level);
        this.kind = switch (EntityType.getKey(type).getPath()) {
            case "orlan_catapult" -> Kind.ORLAN_CATAPULT;
            case "jammer" -> Kind.JAMMER;
            case "trench_jammer" -> Kind.TRENCH_JAMMER;
            case "krasukha" -> Kind.KRASUKHA;
            default -> Kind.GROUND_STATION;
        };
    }

    public Kind kind() { return kind; }
    @Nullable public UUID ownerId() { return ownerId; }
    @Nullable public String ownerName() { return ownerName; }
    private boolean launchesAircraft() { return kind == Kind.GROUND_STATION || kind == Kind.ORLAN_CATAPULT; }
    private boolean isJammer() { return !launchesAircraft(); }

    /** Unit vector the vehicle faces along, on the ground plane. */
    public Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // ---- aircraft ----

    private boolean acceptsAircraft(ItemStack s) {
        AircraftType t = AircraftType.of(s);
        return t != null && (kind == Kind.ORLAN_CATAPULT ? t.runway == 0 : t.runway > 0);
    }

    private int aircraftSlot() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (acceptsAircraft(getItem(i))) return i;
        }
        return -1;
    }

    @Nullable
    @Override
    public AircraftEntity airborne() {
        if (aircraftId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(aircraftId) instanceof AircraftEntity a && a.isAlive() ? a : null;
    }

    @Override
    public boolean recall() {
        AircraftEntity a = airborne();
        return a != null && a.recall();
    }

    /** Centre of the first runway block, at ground level. */
    public Vec3 runwayStart() {
        Vec3 p = position().add(forward().scale(3 + RUNWAY_GAP + 0.5));
        return new Vec3(p.x, getY(), p.z);
    }

    public Vec3 catapultMuzzle() { return position().add(0, 1.0, 0).add(forward().scale(1.6)); }

    public Vec3 catapultDirection() {
        double e = 15 * Mth.DEG_TO_RAD;
        return forward().scale(Math.cos(e)).add(0, Math.sin(e), 0);
    }

    /** The strip in front of the station must be flat, solid and clear across the whole wingspan. */
    @Nullable
    private Component checkRunway(AircraftType aircraft) {
        Vec3 f = forward(), right = new Vec3(-f.z, 0, f.x), start = runwayStart();
        int wing = Math.min(20, Mth.ceil(aircraft.span / 2));
        int y = Mth.floor(getY() + 0.01);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < aircraft.runway; i++) {
            for (int j = -wing; j <= wing; j++) {
                double x = start.x + f.x * i + right.x * j, z = start.z + f.z * i + right.z * j;
                if (Math.abs(j) <= 2) {
                    m.set(Mth.floor(x), y - 1, Mth.floor(z));
                    if (!level().getBlockState(m).isFaceSturdy(level(), m, Direction.UP)) {
                        return Component.translatable("message.steelrain.runway_ground", aircraft.runway, m.getX(), m.getY(), m.getZ());
                    }
                }
                for (int h = 0; h < 4; h++) {
                    m.set(Mth.floor(x), y + h, Mth.floor(z));
                    if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
                        return Component.translatable("message.steelrain.runway_blocked", aircraft.runway, m.getX(), m.getY(), m.getZ());
                    }
                }
            }
        }
        return null;
    }

    // ---- Launcher (Field Laptop) ----

    @Override public String nameKey() { return getType().getDescriptionId(); }
    @Override public boolean aircraftStation() { return true; }
    @Override public boolean hasSecondAmmo() { return kind == Kind.GROUND_STATION; }
    @Override public int ammoCount() { return aircraftSlot() >= 0 ? 1 : 0; }
    @Override public int energyK() { return kind == Kind.ORLAN_CATAPULT ? 0 : getEnergy() / 1000; }
    @Override public int minRange() { return 50; }
    @Override public int busyTicks() { return cooldown; }
    @Override public Vec3 launcherPos() { return position(); }

    @Override
    public int secondCount() {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (getItem(i).is(ModRegistry.HELLFIRE_MISSILE.get())) n += getItem(i).getCount();
        }
        return n;
    }

    @Override
    public int maxRangeNow() {
        AircraftEntity air = airborne();
        if (air != null) return air.type().maxRange();
        int slot = aircraftSlot();
        return slot < 0 ? 0 : AircraftType.of(getItem(slot)).maxRange();
    }

    @Nullable
    @Override
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (!launchesAircraft() || isWreck()) return Component.translatable("message.steelrain.not_linkable");
        double dx = x + 0.5 - getX(), dz = z + 0.5 - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        AircraftEntity flying = airborne();
        if (flying != null) {
            // Already up: send it somewhere else instead.
            if (dist > flying.type().maxRange()) return Component.translatable("message.steelrain.too_far", flying.type().maxRange());
            return flying.retask(x + 0.5, z + 0.5) ? null : Component.translatable("message.steelrain.busy");
        }
        int slot = aircraftSlot();
        if (slot < 0) return Component.translatable("message.steelrain.no_aircraft");
        AircraftType aircraft = AircraftType.of(getItem(slot));
        if (pending || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (Config.REQUIRE_ENERGY.get() && aircraft.energy > 0 && getEnergy() < aircraft.energy) {
            return Component.translatable("message.steelrain.no_fuel", aircraft.energy);
        }
        if (dist < minRange()) return Component.translatable("message.steelrain.too_close", minRange());
        if (dist > aircraft.maxRange()) return Component.translatable("message.steelrain.too_far", aircraft.maxRange());
        if (aircraft.runway > 0) {
            Component problem = checkRunway(aircraft);
            if (problem != null) return problem;
        }
        pending = true;
        pendingX = x + 0.5;
        pendingZ = z + 0.5;
        pendingOwner = player.getUUID();
        pendingOwnerName = player.getGameProfile().getName();
        return null;
    }

    private void launchAircraft(ServerLevel level) {
        pending = false;
        int slot = aircraftSlot();
        if (slot < 0) return;
        AircraftType aircraft = AircraftType.of(removeItem(slot, 1));
        if (aircraft == null) return;
        if (Config.REQUIRE_ENERGY.get() && aircraft.energy > 0) consumeEnergy(aircraft.energy);
        int hellfires = 0;
        if (aircraft.armed()) {
            for (int i = 0; i < getContainerSize() && hellfires < 4; i++) {
                if (getItem(i).is(ModRegistry.HELLFIRE_MISSILE.get())) hellfires += removeItem(i, 4 - hellfires).getCount();
            }
        }
        aircraftId = AircraftEntity.launch(level, aircraft, this, pendingX, pendingZ, hellfires, pendingOwner, pendingOwnerName).getUUID();
        cooldown = 100;
    }

    /** A returning aircraft puts itself and its unused missiles back into the station's inventory. */
    public void dock(AircraftType aircraft, int hellfires) {
        aircraftId = null;
        stow(new ItemStack(aircraft.item().get()));
        if (hellfires > 0) stow(new ItemStack(ModRegistry.HELLFIRE_MISSILE.get(), hellfires));
        cooldown = 60;
    }

    private void stow(ItemStack stack) {
        ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(getInventory(), stack, false);
        if (!rest.isEmpty()) spawnAtLocation(rest, 1f);
    }

    public void aircraftLost() { aircraftId = null; }

    // ---- jamming ----

    private double jamRadius() {
        return switch (kind) {
            case TRENCH_JAMMER -> Config.TRENCH_JAMMER_RANGE.get();
            case KRASUKHA -> Config.STRATEGIC_JAMMER_RANGE.get();
            default -> Config.JAMMER_RANGE.get();
        };
    }

    /** FE drawn every half second while switched on. */
    private int jamEnergy() { return kind == Kind.KRASUKHA ? 1250 : 250; }

    public boolean switchedOn() { return switchedOn; }

    @Override
    public void baseTick() {
        super.baseTick();
        if (!(level() instanceof ServerLevel level) || isWreck()) return;
        if (cooldown > 0) cooldown--;
        if (pending && cooldown == 0) launchAircraft(level);
        if (isJammer() && switchedOn && tickCount % 10 == 0) {
            if (Config.REQUIRE_ENERGY.get()) {
                if (getEnergy() < jamEnergy()) return;
                consumeEnergy(jamEnergy());
            }
            Jamming.emit(level, getUUID(), position().add(0, getBbHeight(), 0), jamRadius(), ownerId, ownerName);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ownerId == null && !level().isClientSide) {
            // Whoever sets it up owns it: that decides whose drones a jammer leaves alone.
            ownerId = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
        if (stack.is(ModRegistry.FIELD_LAPTOP.get())) {
            if (!level().isClientSide) {
                if (!launchesAircraft()) {
                    player.displayClientMessage(Component.translatable("message.steelrain.not_linkable"), true);
                } else {
                    boolean linked = LaptopItem.toggleVehicle(stack, getUUID());
                    player.displayClientMessage(Component.translatable(
                            linked ? "message.steelrain.linked" : "message.steelrain.unlinked", getName()), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isJammer() && player.isShiftKeyDown() && stack.isEmpty()) {
            if (!level().isClientSide) {
                switchedOn = !switchedOn;
                ownerId = player.getUUID();
                ownerName = player.getGameProfile().getName();
                player.displayClientMessage(Component.translatable(switchedOn ? "message.steelrain.jammer_on" : "message.steelrain.jammer_off"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SrCooldown", cooldown);
        tag.putBoolean("SrOn", switchedOn);
        if (aircraftId != null) tag.putUUID("SrAircraft", aircraftId);
        if (ownerId != null) tag.putUUID("SrOwner", ownerId);
        if (ownerName != null) tag.putString("SrOwnerName", ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cooldown = tag.getInt("SrCooldown");
        switchedOn = !tag.contains("SrOn") || tag.getBoolean("SrOn");
        aircraftId = tag.hasUUID("SrAircraft") ? tag.getUUID("SrAircraft") : null;
        ownerId = tag.hasUUID("SrOwner") ? tag.getUUID("SrOwner") : null;
        ownerName = tag.contains("SrOwnerName") ? tag.getString("SrOwnerName") : null;
    }
}
