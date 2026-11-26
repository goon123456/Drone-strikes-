package com.steelrain.block;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.util.Teams;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.MissileProfile;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class WeaponBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TANK_CAPACITY = 8000;
    public static final int CANISTER_MB = 1000;
    public static final int SLOT_PRIMARY = 0, SLOT_SECONDARY = 1, SLOT_FUEL = 2;
    /** Gap between the front of a ground station and the first runway block. */
    public static final int RUNWAY_GAP = 3;

    /** Every weapon system currently loaded on the server, per level. Recon drones look through this. */
    private static final Map<Level, Set<WeaponBlockEntity>> LOADED = new WeakHashMap<>();

    public static Set<WeaponBlockEntity> loadedIn(Level level) {
        return LOADED.getOrDefault(level, Collections.emptySet());
    }

    private final WeaponType type;

    public final ItemStackHandler items = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_PRIMARY -> type.acceptsPrimary(stack);
                case SLOT_SECONDARY -> type.acceptsSecondary(stack);
                default -> stack.is(ModRegistry.FUEL_CANISTER.get()) || stack.is(ModRegistry.DRONE_BATTERY.get())
                        || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case SLOT_PRIMARY -> Math.max(1, type.primaryLimit);
                case SLOT_SECONDARY -> Math.max(1, type.secondaryLimit);
                default -> 64;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == SLOT_PRIMARY && loadedCode() != loaded) sync();
        }
    };

    /** Fuel in mB. Any fluid tagged steelrain:launcher_fuel (diesel, gasoline...) counts, as do Fuel Canisters. */
    private int fuel;

    /** Fill-only fluid port so pipes from other mods can pump fuel in. */
    public final IFluidHandler fuelPort = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return TANK_CAPACITY; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return stack.is(ModRegistry.LAUNCHER_FUEL); }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.is(ModRegistry.LAUNCHER_FUEL)) return 0;
            int accepted = Math.min(resource.getAmount(), TANK_CAPACITY - fuel);
            if (accepted > 0 && action.execute()) {
                fuel += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    /** Forge Energy that has not yet added up to a whole mB of fuel. */
    private int energyBuffer;

    /** Receive-only power port: other mods' cables can charge the vehicle instead of refuelling it. */
    public final net.neoforged.neoforge.energy.IEnergyStorage energyPort = new net.neoforged.neoforge.energy.IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (!type.usesFuel() || amount <= 0) return 0;
            int rate = Config.FE_PER_MB.get();
            long room = (long) (TANK_CAPACITY - fuel) * rate - energyBuffer;
            int accepted = (int) Math.max(0, Math.min(amount, room));
            if (accepted > 0 && !simulate) {
                energyBuffer += accepted;
                fuel = Math.min(TANK_CAPACITY, fuel + energyBuffer / rate);
                energyBuffer %= rate;
                setChanged();
            }
            return accepted;
        }

        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return fuel * Config.FE_PER_MB.get() + energyBuffer; }
        @Override public int getMaxEnergyStored() { return TANK_CAPACITY * Config.FE_PER_MB.get(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return type.usesFuel(); }
    };

    @Nullable private UUID owner;
    @Nullable private String ownerName;
    private int cooldown;
    private int salvoLeft;
    private double salvoX, salvoZ;
    @Nullable private UUID salvoOwner;
    @Nullable private String salvoOwnerName;

    // Turret / barrel pose in degrees. yaw is relative to the vehicle's facing, pitch is elevation.
    public float yaw, pitch, yawO, pitchO;
    private float yawTarget, pitchTarget;
    private int restTimer;
    private boolean clientInit;
    private int engageId = -1;
    /** What is sitting in the primary slot, for the renderer: -1 nothing, else 0 or an AircraftType ordinal. */
    private int loaded = -1;
    @Nullable private UUID aircraftId;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) { return i == 0 ? fuel : i == 1 ? busyTicks() : switchedOn ? 1 : 0; }
        @Override
        public void set(int i, int v) {}
        @Override
        public int getCount() { return 3; }
    };

    public WeaponBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WEAPON_BE.get(), pos, state);
        this.type = state.getBlock() instanceof WeaponBlock wb ? wb.type() : WeaponType.JAMMER;
        this.pitch = this.pitchO = this.pitchTarget = type.restPitch;
    }

    public WeaponType type() { return type; }
    public int fuel() { return fuel; }
    public int primaryCount() { return items.getStackInSlot(SLOT_PRIMARY).getCount(); }
    public int secondaryCount() { return items.getStackInSlot(SLOT_SECONDARY).getCount(); }
    public int busyTicks() { return cooldown + salvoLeft * type.cooldown; }
    public int loaded() { return loaded; }
    @Nullable public UUID owner() { return owner; }
    @Nullable public String ownerName() { return ownerName; }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(WeaponBlock.FACING) ? s.getValue(WeaponBlock.FACING) : Direction.NORTH;
    }

    public void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        setChanged();
    }

    private int loadedCode() {
        ItemStack s = items.getStackInSlot(SLOT_PRIMARY);
        if (s.isEmpty()) return -1;
        AircraftType a = AircraftType.of(s);
        return a != null ? a.ordinal() : 0;
    }

    private boolean hasFuel(int mb) {
        return !Config.REQUIRE_FUEL.get() || !type.usesFuel() || fuel >= mb;
    }

    /** Powered systems (radar, turret drives) are dead with an empty tank. */
    private boolean powered() {
        return hasFuel(1);
    }

    // ---- hand loading ----

    /**
     * Right-clicking the vehicle with ammunition or fuel in hand puts it straight in.
     * @return true if the item was something this vehicle takes (whether or not there was room)
     */
    public boolean handLoad(Player player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int slot = type.acceptsPrimary(stack) ? SLOT_PRIMARY : type.acceptsSecondary(stack) ? SLOT_SECONDARY : -1;
        if (slot >= 0) {
            int before = stack.getCount();
            ItemStack rest = items.insertItem(slot, stack.copy(), false);
            int moved = before - rest.getCount();
            if (moved > 0) {
                if (!player.getAbilities().instabuild) stack.shrink(moved);
                if (level != null) level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.3f);
            }
            ItemStack now = items.getStackInSlot(slot);
            player.displayClientMessage(Component.translatable(moved > 0 ? "message.steelrain.loaded" : "message.steelrain.load_full",
                    now.getHoverName(), now.getCount(), Math.min(items.getSlotLimit(slot), now.getMaxStackSize())), true);
            return true;
        }
        if (!type.usesFuel()) return false;
        int add = stack.is(ModRegistry.FUEL_CANISTER.get()) ? CANISTER_MB : stack.is(ModRegistry.DRONE_BATTERY.get()) ? 500 : 0;
        if (add > 0) {
            if (TANK_CAPACITY - fuel >= add) {
                fuel += add;
                if (!player.getAbilities().instabuild) stack.shrink(1);
                setChanged();
                if (level != null) level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1f);
            }
            player.displayClientMessage(Component.translatable("message.steelrain.fuel_level", fuel, TANK_CAPACITY), true);
            return true;
        }
        // Buckets and tanks of diesel from other mods.
        if (stack.getCount() == 1) {
            IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) {
                FluidStack sim = handler.drain(TANK_CAPACITY - fuel, IFluidHandler.FluidAction.SIMULATE);
                if (!sim.isEmpty() && sim.is(ModRegistry.LAUNCHER_FUEL)) {
                    FluidStack drained = handler.drain(sim.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                    fuel = Math.min(TANK_CAPACITY, fuel + drained.getAmount());
                    if (!player.getAbilities().instabuild) player.setItemInHand(player.getUsedItemHand(), handler.getContainer());
                    setChanged();
                    player.displayClientMessage(Component.translatable("message.steelrain.fuel_level", fuel, TANK_CAPACITY), true);
                    return true;
                }
            }
        }
        return false;
    }

    private void burn(int mb) {
        if (!Config.REQUIRE_FUEL.get()) return;
        fuel = Math.max(0, fuel - mb);
        setChanged();
    }

    private void sync() {
        loaded = loadedCode();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            loaded = loadedCode();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) loadedIn(level).remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) loadedIn(level).remove(this);
    }

    // ---- geometry ----

    /** Model space (origin rear-centre-bottom, +Z to the front) to world space. */
    public Vec3 modelToWorld(Vec3 m) {
        double a = facing().toYRot() * Mth.DEG_TO_RAD;
        double mz = m.z - 0.5;
        double cos = Math.cos(a), sin = Math.sin(a);
        return new Vec3(worldPosition.getX() + 0.5 + m.x * cos - mz * sin,
                worldPosition.getY() + m.y,
                worldPosition.getZ() + 0.5 + m.x * sin + mz * cos);
    }

    private float effectivePitch() {
        return type.pitchSpeed > 0 ? pitch : type.fixedPitch();
    }

    /** World position rounds leave from, following the current turret pose. */
    public Vec3 muzzle() {
        double e = effectivePitch() * Mth.DEG_TO_RAD, r = yaw * Mth.DEG_TO_RAD;
        Vec3 m = type.muzzle(), g = type.gunPivot(), t = type.turretPivot();
        double my = m.y * Math.cos(e) + m.z * Math.sin(e);
        double mz = -m.y * Math.sin(e) + m.z * Math.cos(e);
        double px = g.x - t.x + m.x, py = g.y - t.y + my, pz = g.z - t.z + mz;
        double rx = px * Math.cos(r) - pz * Math.sin(r);
        double rz = px * Math.sin(r) + pz * Math.cos(r);
        return modelToWorld(new Vec3(t.x + rx, t.y + py, t.z + rz));
    }

    /** Unit vector along the barrel. */
    public Vec3 aimDirection() {
        return Vec3.directionFromRotation(-effectivePitch(), facing().toYRot() + yaw);
    }

    private void aimStep() {
        yawO = yaw;
        pitchO = pitch;
        if (type.yawSpeed > 0) {
            float d = Mth.wrapDegrees(yawTarget - yaw);
            yaw = Mth.wrapDegrees(yaw + Mth.clamp(d, -type.yawSpeed, type.yawSpeed));
            if (Math.abs(yaw - yawO) > 180) yawO = yaw; // crossed the +-180 seam: skip the interpolation glitch
        }
        if (type.pitchSpeed > 0) {
            pitch += Mth.clamp(pitchTarget - pitch, -type.pitchSpeed, type.pitchSpeed);
        }
    }

    private boolean aligned(float tolerance) {
        return (type.yawSpeed <= 0 || Math.abs(Mth.wrapDegrees(yawTarget - yaw)) <= tolerance)
                && (type.pitchSpeed <= 0 || Math.abs(pitchTarget - pitch) <= tolerance);
    }

    private void aimAt(float yawRel, float elevation) {
        float ny = type.yawSpeed > 0 ? Mth.wrapDegrees(yawRel) : 0;
        float np = type.pitchSpeed > 0 ? elevation : pitchTarget;
        if (Math.abs(Mth.wrapDegrees(ny - yawTarget)) > 0.5f || Math.abs(np - pitchTarget) > 0.5f) {
            yawTarget = ny;
            pitchTarget = np;
            sync();
        }
        restTimer = 100;
    }

    // ---- ticking ----

    public void clientTick() {
        aimStep();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        aimStep();
        if (cooldown > 0) cooldown--;
        long time = sl.getGameTime();
        if (time % 10 == 0) refuelFromSlot();
        switch (type.kind) {
            case LAUNCHER -> {
                if (salvoLeft > 0 && time % 100 == 0) FlyingWeaponEntity.preload(sl, worldPosition.getX(), worldPosition.getZ());
                if (salvoLeft > 0 && cooldown == 0 && aligned(1.5f)) {
                    if (fireOne(sl)) salvoLeft--;
                    else salvoLeft = 0;
                    restTimer = 80;
                    setChanged();
                }
            }
            case AIR_DEFENCE -> airDefenceTick(sl, time);
            case JAMMER -> { }
        }
        if (time % 10 == 0 && (dome || type.kind == WeaponType.Kind.JAMMER)) emit(sl);
        if (salvoLeft == 0 && engageId < 0 && restTimer > 0 && --restTimer == 0) {
            yawTarget = 0;
            pitchTarget = type.restPitch;
            sync();
        }
    }

    private void refuelFromSlot() {
        ItemStack stack = items.getStackInSlot(SLOT_FUEL);
        if (stack.isEmpty() || fuel >= TANK_CAPACITY) return;
        if (stack.is(ModRegistry.FUEL_CANISTER.get())) {
            if (TANK_CAPACITY - fuel >= CANISTER_MB) {
                fuel += CANISTER_MB;
                items.extractItem(SLOT_FUEL, 1, false);
                setChanged();
            }
            return;
        }
        if (stack.is(ModRegistry.DRONE_BATTERY.get())) {
            // Jammers also run off batteries.
            if (TANK_CAPACITY - fuel >= 500) {
                fuel += 500;
                items.extractItem(SLOT_FUEL, 1, false);
                setChanged();
            }
            return;
        }
        if (stack.getCount() != 1) return;
        IFluidHandlerItem handler = stack.copy().getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack sim = handler.drain(TANK_CAPACITY - fuel, IFluidHandler.FluidAction.SIMULATE);
        if (sim.isEmpty() || !sim.is(ModRegistry.LAUNCHER_FUEL)) return;
        FluidStack drained = handler.drain(sim.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        fuel = Math.min(TANK_CAPACITY, fuel + drained.getAmount());
        items.setStackInSlot(SLOT_FUEL, handler.getContainer());
        setChanged();
    }

    // ---- laptop launches ----

    /** The aircraft this station has in the air, if it can be found. */
    @Nullable
    public AircraftEntity airborne() {
        if (aircraftId == null || !(level instanceof ServerLevel sl)) return null;
        return sl.getEntity(aircraftId) instanceof AircraftEntity a && a.isAlive() ? a : null;
    }

    /** @return null when the order was accepted, otherwise the reason it was refused. */
    @Nullable
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (!type.laptopControlled()) return Component.translatable("message.steelrain.not_linkable");
        double dx = x + 0.5 - (worldPosition.getX() + 0.5), dz = z + 0.5 - (worldPosition.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);

        AircraftType aircraft = null;
        int fuelNeeded = type.fuelPerShot;
        int maxRange = type.maxRange();
        if (type.launchesAircraft()) {
            AircraftEntity flying = airborne();
            if (flying != null) {
                // Already up: send it somewhere else instead.
                if (dist > flying.type().maxRange()) return Component.translatable("message.steelrain.too_far", flying.type().maxRange());
                return flying.retask(x + 0.5, z + 0.5) ? null : Component.translatable("message.steelrain.busy");
            }
            aircraft = AircraftType.of(items.getStackInSlot(SLOT_PRIMARY));
            if (aircraft == null) return Component.translatable("message.steelrain.no_aircraft");
            fuelNeeded = aircraft.fuel;
            maxRange = aircraft.maxRange();
        }
        if (!type.launchesAircraft()) maxRange = type.maxRangeFor(items.getStackInSlot(SLOT_PRIMARY));
        if (salvoLeft > 0 || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (primaryCount() <= 0) return Component.translatable("message.steelrain.no_ammo");
        if (!hasFuel(fuelNeeded)) return Component.translatable("message.steelrain.no_fuel", fuelNeeded);
        if (dist < type.minRange()) return Component.translatable("message.steelrain.too_close", type.minRange());
        if (dist > maxRange) return Component.translatable("message.steelrain.too_far", maxRange);
        if (aircraft != null && aircraft.runway > 0) {
            Component problem = checkRunway(aircraft);
            if (problem != null) return problem;
        }
        salvoLeft = type.launchesAircraft() ? 1 : Math.max(1, Math.min(count, primaryCount()));
        salvoX = x + 0.5;
        salvoZ = z + 0.5;
        salvoOwner = player.getUUID();
        salvoOwnerName = player.getGameProfile().getName();
        float bearing = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        yawTarget = type.yawSpeed > 0 ? Mth.wrapDegrees(bearing - facing().toYRot()) : 0;
        if (type.pitchSpeed > 0) pitchTarget = type.firePitch;
        // Keep this launcher ticking even if the operator is far away or walks off mid-salvo.
        if (level instanceof ServerLevel sl) FlyingWeaponEntity.preload(sl, worldPosition.getX(), worldPosition.getZ());
        sync();
        return null;
    }

    public boolean recall() {
        AircraftEntity a = airborne();
        return a != null && a.recall();
    }

    /** Centre of the first runway block, at ground level. */
    public Vec3 runwayStart() {
        BlockPos p = worldPosition.relative(facing(), type.length + RUNWAY_GAP);
        return new Vec3(p.getX() + 0.5, worldPosition.getY(), p.getZ() + 0.5);
    }

    /** The strip in front of the station must be flat, solid and clear across the whole wingspan. */
    @Nullable
    private Component checkRunway(AircraftType aircraft) {
        if (level == null) return null;
        Direction f = facing(), right = f.getClockWise();
        BlockPos first = worldPosition.relative(f, type.length + RUNWAY_GAP);
        int wing = Math.min(20, Mth.ceil(aircraft.span / 2));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < aircraft.runway; i++) {
            for (int j = -wing; j <= wing; j++) {
                m.set(first).move(f, i).move(right, j);
                if (Math.abs(j) <= 2) {
                    m.move(Direction.DOWN);
                    if (!level.getBlockState(m).isFaceSturdy(level, m, Direction.UP)) {
                        return Component.translatable("message.steelrain.runway_ground", aircraft.runway, m.getX(), m.getY(), m.getZ());
                    }
                    m.move(Direction.UP);
                }
                for (int h = 0; h < 4; h++) {
                    if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                        return Component.translatable("message.steelrain.runway_blocked", aircraft.runway, m.getX(), m.getY(), m.getZ());
                    }
                    m.move(Direction.UP);
                }
            }
        }
        return null;
    }

    private boolean fireOne(ServerLevel level) {
        AircraftType aircraft = type.launchesAircraft() ? AircraftType.of(items.getStackInSlot(SLOT_PRIMARY)) : null;
        int fuelNeeded = aircraft != null ? aircraft.fuel : type.fuelPerShot;
        if (primaryCount() <= 0 || !hasFuel(fuelNeeded) || (type.launchesAircraft() && aircraft == null)) return false;
        ItemStack round = items.extractItem(SLOT_PRIMARY, 1, false);
        burn(fuelNeeded);
        Vec3 muzzle = muzzle();
        Vec3 dir = aimDirection();
        UUID o = salvoOwner != null ? salvoOwner : owner;
        String on = salvoOwner != null ? salvoOwnerName : ownerName;
        boolean last = salvoLeft <= 1;
        switch (type) {
            case TOMAHAWK_LAUNCHER -> MissileEntity.launch(level, MissileProfile.TOMAHAWK, muzzle, salvoX, salvoZ, o, on, true, dir);
            case ISKANDER_LAUNCHER -> MissileEntity.launch(level, MissileProfile.ISKANDER, muzzle, salvoX, salvoZ, o, on, true, dir);
            case HIMARS -> MissileEntity.launch(level, ModRegistry.profileOfAmmo(round, MissileProfile.GMLRS), muzzle, salvoX, salvoZ, o, on, last, dir);
            case M109 -> MissileEntity.launch(level, ModRegistry.profileOfAmmo(round, MissileProfile.SHELL), muzzle, salvoX, salvoZ, o, on, last, dir);
            case SHAHED_LAUNCHER -> MissileEntity.launch(level, MissileProfile.SHAHED, muzzle, salvoX, salvoZ, o, on, true, dir);
            case GROUND_STATION, ORLAN_CATAPULT -> {
                int hellfires = aircraft.armed() ? items.extractItem(SLOT_SECONDARY, 4, false).getCount() : 0;
                aircraftId = AircraftEntity.launch(level, aircraft, this, salvoX, salvoZ, hellfires, o, on).getUUID();
            }
            default -> { }
        }
        cooldown = type.cooldown;
        if (aircraft == null || aircraft.runway == 0) {
            boolean gun = type == WeaponType.M109;
            level.playSound(null, BlockPos.containing(muzzle), gun ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.BLOCKS, 6f, gun ? 1.6f : 0.6f);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 20, 0.5, 0.3, 0.5, 0.03);
            level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.05);
        }
        sync();
        return true;
    }

    /** A returning aircraft puts itself and its unused missiles back into the station. */
    public void dock(AircraftType aircraft, int hellfires) {
        aircraftId = null;
        ItemStack left = items.insertItem(SLOT_PRIMARY, new ItemStack(aircraft.item().get()), false);
        if (!left.isEmpty() && level != null) Block.popResource(level, worldPosition.above(type.height), left);
        if (hellfires > 0) {
            ItemStack rest = items.insertItem(SLOT_SECONDARY, new ItemStack(ModRegistry.HELLFIRE_MISSILE.get(), hellfires), false);
            if (!rest.isEmpty() && level != null) Block.popResource(level, worldPosition.above(type.height), rest);
        }
        cooldown = 60;
        sync();
    }

    public void aircraftLost() {
        aircraftId = null;
        setChanged();
    }

    // ---- air defence: missiles and guns ----

    private int rounds;            // cannon rounds left in the belt that is currently loaded
    private boolean armourPiercing;
    private int engageTicks;
    private boolean barrel;        // alternates between the two guns
    private boolean manualFire;
    private int seatId = -1;

    private int beltSlot() { return type == WeaponType.PANTSIR ? SLOT_SECONDARY : SLOT_PRIMARY; }

    private boolean hasRounds() {
        return type.hasGuns() && (rounds > 0 || !items.getStackInSlot(beltSlot()).isEmpty());
    }

    private boolean hasMissile() {
        return type.hasMissiles() && primaryCount() > 0 && hasFuel(type.fuelPerShot);
    }

    /** The player sitting at the gun, if any. */
    @Nullable
    private Player gunner() {
        if (seatId < 0 || level == null) return null;
        Entity seat = level.getEntity(seatId);
        if (seat == null || !seat.isAlive()) {
            seatId = -1;
            manualFire = false;
            return null;
        }
        return seat.getFirstPassenger() instanceof Player p ? p : null;
    }

    /** Seats a player at a gun that can be manned. */
    public boolean mount(Player player) {
        Vec3 seat = type.seat();
        if (seat == null || !(level instanceof ServerLevel sl) || gunner() != null) return false;
        com.steelrain.entity.SeatEntity e = new com.steelrain.entity.SeatEntity(ModRegistry.SEAT.get(), sl);
        e.setGun(worldPosition);
        e.setPos(modelToWorld(seat));
        sl.addFreshEntity(e);
        seatId = e.getId();
        manualFire = false;
        return player.startRiding(e, true);
    }

    public void setManualFire(Player player, boolean firing) {
        if (gunner() == player) manualFire = firing;
    }

    private void airDefenceTick(ServerLevel level, long time) {
        // A human gunner overrides the fire-control computer: the barrels follow their eyes, and they get no aiming help.
        Player gunner = gunner();
        if (gunner != null) {
            engageId = -1;
            aimAt(gunner.getYRot() - facing().toYRot(), Mth.clamp(-gunner.getXRot(), -5, 85));
            // The seat is part of the mount: it swings round with the guns so the gunner always sits behind them.
            Entity seat = level.getEntity(seatId);
            Vec3 s = type.seat(), t = type.turretPivot();
            if (seat != null && s != null) {
                double r = yaw * Mth.DEG_TO_RAD, px = s.x - t.x, pz = s.z - t.z;
                seat.setPos(modelToWorld(new Vec3(t.x + px * Math.cos(r) - pz * Math.sin(r), s.y, t.z + px * Math.sin(r) + pz * Math.cos(r))));
            }
            if (manualFire && time % 2 == 0) fireBullet(level, 0.4, gunner.getUUID());
            return;
        }
        // Radar and turret drives need power. Searching sips fuel; tracking a target takes twice as much.
        if (!powered()) {
            engageId = -1;
            return;
        }
        if (time % 20 == 0) burn(type.idleFuel() * (engageId >= 0 ? 2 : 1));
        boolean patriot = type == WeaponType.PATRIOT;
        if (engageId < 0) {
            if ((hasMissile() && cooldown == 0 || hasRounds()) && time % (patriot ? 10 : 4) == 0) {
                FlyingWeaponEntity target = findTarget(level, patriot);
                if (target != null) {
                    engageId = target.getId();
                    engageTicks = 0;
                }
            }
            return;
        }
        Entity e = level.getEntity(engageId);
        Vec3 from = modelToWorld(type.gunPivot());
        if (!(e instanceof FlyingWeaponEntity target) || !target.isAlive() || ++engageTicks > 400
                || (!hasMissile() && !hasRounds())) {
            engageId = -1;
            return;
        }
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 targetMove = com.steelrain.util.Ballistics.velocity(target);
        double dist = from.distanceTo(targetPos);

        // Guns for close targets; the Pantsir keeps its missiles for far or fast ones.
        boolean guns = hasRounds() && dist <= type.gunRange() && (!hasMissile() || !target.highValue());
        if (!guns && !hasMissile()) {
            if (dist > type.gunRange() * 1.4) engageId = -1;
            guns = hasRounds();
            if (!guns) return;
        }
        double shotSpeed = guns ? type.bulletSpeed() : (patriot ? 6.0 : 4.5);
        // Aim at where the target will be when the shot arrives, plus a little up for the drop of a shell.
        Vec3 aim = com.steelrain.util.Ballistics.intercept(from, targetPos, targetMove, shotSpeed);
        if (aim == null) aim = targetPos;
        if (guns) {
            double t = from.distanceTo(aim) / shotSpeed;
            aim = aim.add(0, 0.5 * com.steelrain.entity.BulletEntity.GRAVITY * t * t, 0);
        }
        Vec3 to = aim.subtract(from);
        float bearing = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        float elevation = (float) (Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        aimAt(bearing - facing().toYRot(), patriot ? type.firePitch : Mth.clamp(elevation, -5, 85));

        if (guns) {
            target.engagedUntil = Math.max(target.engagedUntil, time + 10);
            if (aligned(2.5f) && dist <= type.gunRange() && time % 2 == 0) fireBullet(level, type.dispersion(), owner);
            return;
        }
        if (cooldown > 0 || !aligned(12f) || target.engagedUntil > time) return;

        items.extractItem(SLOT_PRIMARY, 1, false);
        burn(type.fuelPerShot);
        Vec3 muzzle = muzzle();
        InterceptorEntity missile = new InterceptorEntity(
                patriot ? ModRegistry.PATRIOT_INTERCEPTOR.get() : ModRegistry.INTERCEPTOR.get(), level);
        missile.setPos(muzzle);
        missile.setOwner(owner, ownerName);
        missile.setTarget(target, aimDirection());
        level.addFreshEntity(missile);
        target.engagedUntil = time + (patriot ? 160 : 60);
        engageId = -1;
        cooldown = type.cooldown;
        level.playSound(null, BlockPos.containing(muzzle), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 6f, patriot ? 0.5f : 1.4f);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 20, 0.3, 0.3, 0.3, 0.06);
        sync();
    }

    /** One round out of the barrel, in the direction the gun is actually pointing right now. */
    private void fireBullet(ServerLevel level, double dispersionDeg, @Nullable UUID shooter) {
        if (rounds <= 0) {
            ItemStack belt = items.extractItem(beltSlot(), 1, false);
            if (belt.isEmpty()) return;
            rounds = type.roundsPerBelt();
            armourPiercing = ModRegistry.armourPiercing(belt);
        }
        rounds--;
        barrel = !barrel;
        double e = effectivePitch() * Mth.DEG_TO_RAD, r = yaw * Mth.DEG_TO_RAD;
        Vec3 m = type.gunMuzzle(), g = type.gunPivot(), t = type.turretPivot();
        double mx = barrel ? m.x : -m.x;
        double my = m.y * Math.cos(e) + m.z * Math.sin(e);
        double mz = -m.y * Math.sin(e) + m.z * Math.cos(e);
        double px = g.x - t.x + mx, py = g.y - t.y + my, pz = g.z - t.z + mz;
        Vec3 from = modelToWorld(new Vec3(t.x + px * Math.cos(r) - pz * Math.sin(r), t.y + py, t.z + px * Math.sin(r) + pz * Math.cos(r)));
        double spread = Math.tan(dispersionDeg * Mth.DEG_TO_RAD);
        Vec3 dir = aimDirection().add(level.random.nextGaussian() * spread, level.random.nextGaussian() * spread,
                level.random.nextGaussian() * spread).normalize();
        // Armour-piercing: a solid shot that hits much harder. High explosive: less punch, but it bursts.
        com.steelrain.entity.BulletEntity.fire(level, from, dir.scale(type.bulletSpeed()),
                armourPiercing ? type.bulletDamage() * 1.8f : type.bulletDamage(), !armourPiercing, shooter);
        level.playSound(null, BlockPos.containing(from), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 2f);
        if (level.random.nextInt(3) == 0) level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 1, 0.05, 0.05, 0.05, 0.01);
        setChanged();
    }

    @Nullable
    private FlyingWeaponEntity findTarget(ServerLevel level, boolean patriot) {
        double range = patriot ? Config.PATRIOT_RANGE.get()
                : type.hasMissiles() ? Config.AIR_DEFENCE_RANGE.get() : type.gunRange() * 1.3;
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        long now = level.getGameTime();
        boolean gunsOnly = !hasMissile();
        return level.getEntitiesOfClass(FlyingWeaponEntity.class, new AABB(centre, centre).inflate(range),
                        e -> e.interceptable() && (gunsOnly || e.engagedUntil < now)
                                && (patriot ? e.highValue() : e.getY() - worldPosition.getY() <= Config.AIR_DEFENCE_CEILING.get())
                                && e.distanceToSqr(centre) <= range * range
                                && !Teams.friendly(level, owner, ownerName, e.ownerId(), e.ownerName()))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(centre))).orElse(null);
    }

    // ---- jamming ----

    private boolean switchedOn = true;
    private boolean dome;

    public boolean switchedOn() { return switchedOn; }
    public boolean hasDome() { return dome; }

    public void toggle() {
        switchedOn = !switchedOn;
        sync();
    }

    /** Fits a Jammer Dome. @return false if it already has one. */
    public boolean fitDome() {
        if (dome || type.kind == WeaponType.Kind.JAMMER) return false;
        dome = true;
        sync();
        return true;
    }

    /** Keeps this system's jamming bubble alive while it is switched on and has fuel. */
    private void emit(ServerLevel level) {
        Vec3 centre = modelToWorld(new Vec3(0, type.height, type.length / 2.0));
        if (dome && powered()) {
            // The dome runs off the vehicle's own tank.
            if (type.usesFuel() && level.getGameTime() % 100 == 0) burn(1);
            com.steelrain.util.Jamming.emit(level, worldPosition.above(), centre, Config.DOME_JAMMER_RANGE.get(), owner, ownerName);
        }
        if (type.kind != WeaponType.Kind.JAMMER || !switchedOn) return;
        if (Config.REQUIRE_FUEL.get()) {
            if (fuel <= 0) return;
            burn(type.jamFuel());
        }
        com.steelrain.util.Jamming.emit(level, worldPosition, centre, type.jamRadius(), owner, ownerName);
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() { return Component.translatable(type.translationKey()); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new WeaponMenu(id, inv, this, data);
    }

    // ---- persistence and sync ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Fuel", fuel);
        if (owner != null) tag.putUUID("Owner", owner);
        if (ownerName != null) tag.putString("OwnerName", ownerName);
        tag.putInt("Cooldown", cooldown);
        tag.putInt("Salvo", salvoLeft);
        tag.putDouble("SalvoX", salvoX);
        tag.putDouble("SalvoZ", salvoZ);
        if (salvoOwner != null) tag.putUUID("SalvoOwner", salvoOwner);
        if (salvoOwnerName != null) tag.putString("SalvoOwnerName", salvoOwnerName);
        if (aircraftId != null) tag.putUUID("Aircraft", aircraftId);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("YawT", yawTarget);
        tag.putFloat("PitchT", pitchTarget);
        tag.putInt("Rounds", rounds);
        tag.putBoolean("AP", armourPiercing);
        tag.putInt("Energy", energyBuffer);
        tag.putBoolean("On", switchedOn);
        tag.putBoolean("Dome", dome);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Sync")) {
            // Lightweight client update: only the pose targets and what is loaded.
            yawTarget = tag.getFloat("YawT");
            pitchTarget = tag.getFloat("PitchT");
            loaded = tag.getInt("Loaded");
            dome = tag.getBoolean("Dome");
            switchedOn = tag.getBoolean("On");
            if (!clientInit) {
                clientInit = true;
                yaw = yawO = tag.getFloat("Yaw");
                pitch = pitchO = tag.getFloat("Pitch");
            }
            return;
        }
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
        fuel = tag.getInt("Fuel");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.contains("OwnerName") ? tag.getString("OwnerName") : null;
        cooldown = tag.getInt("Cooldown");
        salvoLeft = tag.getInt("Salvo");
        salvoX = tag.getDouble("SalvoX");
        salvoZ = tag.getDouble("SalvoZ");
        salvoOwner = tag.hasUUID("SalvoOwner") ? tag.getUUID("SalvoOwner") : null;
        salvoOwnerName = tag.contains("SalvoOwnerName") ? tag.getString("SalvoOwnerName") : null;
        aircraftId = tag.hasUUID("Aircraft") ? tag.getUUID("Aircraft") : null;
        rounds = tag.getInt("Rounds");
        armourPiercing = tag.getBoolean("AP");
        energyBuffer = tag.getInt("Energy");
        switchedOn = !tag.contains("On") || tag.getBoolean("On");
        dome = tag.getBoolean("Dome");
        if (tag.contains("Pitch")) {
            yaw = yawO = tag.getFloat("Yaw");
            pitch = pitchO = tag.getFloat("Pitch");
            yawTarget = tag.getFloat("YawT");
            pitchTarget = tag.getFloat("PitchT");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Sync", true);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("YawT", yawTarget);
        tag.putFloat("PitchT", pitchTarget);
        tag.putInt("Loaded", loadedCode());
        tag.putBoolean("Dome", dome);
        tag.putBoolean("On", switchedOn);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
