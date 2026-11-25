package com.steelrain.block;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.entity.ReaperEntity;
import com.steelrain.util.Teams;
import com.steelrain.weapon.MissileProfile;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
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
import java.util.Comparator;
import java.util.UUID;

public class WeaponBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TANK_CAPACITY = 8000;
    public static final int CANISTER_MB = 1000;
    public static final int SLOT_PRIMARY = 0, SLOT_SECONDARY = 1, SLOT_FUEL = 2;

    private final WeaponType type;

    public final ItemStackHandler items = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_PRIMARY -> type.acceptsPrimary(stack);
                case SLOT_SECONDARY -> type.acceptsSecondary(stack);
                default -> stack.is(ModRegistry.FUEL_CANISTER.get())
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
        protected void onContentsChanged(int slot) { setChanged(); }
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

    @Nullable private UUID owner;
    @Nullable private String ownerName;
    private int cooldown;
    private int salvoLeft;
    private double salvoX, salvoZ;
    @Nullable private UUID salvoOwner;
    @Nullable private String salvoOwnerName;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) { return i == 0 ? fuel : cooldown + salvoLeft * type.cooldown; }
        @Override
        public void set(int i, int v) {}
        @Override
        public int getCount() { return 2; }
    };

    public WeaponBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WEAPON_BE.get(), pos, state);
        this.type = state.getBlock() instanceof WeaponBlock wb ? wb.type() : WeaponType.JAMMER;
    }

    public WeaponType type() { return type; }
    public int fuel() { return fuel; }
    public int primaryCount() { return items.getStackInSlot(SLOT_PRIMARY).getCount(); }
    public int secondaryCount() { return items.getStackInSlot(SLOT_SECONDARY).getCount(); }
    public int busyTicks() { return cooldown + salvoLeft * type.cooldown; }

    public void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        setChanged();
    }

    private boolean hasFuel(int mb) {
        return !Config.REQUIRE_FUEL.get() || fuel >= mb;
    }

    private void burn(int mb) {
        fuel = Math.max(0, fuel - mb);
        setChanged();
    }

    // ---- ticking ----

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (cooldown > 0) cooldown--;
        long time = sl.getGameTime();
        if (time % 10 == 0) refuelFromSlot();
        switch (type.kind) {
            case LAUNCHER -> {
                if (salvoLeft > 0 && time % 100 == 0) FlyingWeaponEntity.preload(sl, worldPosition.getX(), worldPosition.getZ());
                if (salvoLeft > 0 && cooldown == 0) {
                    if (fireOne(sl)) salvoLeft--;
                    else salvoLeft = 0;
                    setChanged();
                }
            }
            case AIR_DEFENCE -> {
                if (time % 4 == 0 && cooldown == 0 && primaryCount() > 0) scanSky(sl);
            }
            case JAMMER -> {
                if (time % 10 == 0) jam(sl);
            }
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

    /** @return null when the launch was accepted, otherwise the reason it was refused. */
    @Nullable
    public Component tryLaunch(ServerPlayer player, int x, int z, int count) {
        if (!type.laptopControlled()) return Component.translatable("message.steelrain.not_linkable");
        if (salvoLeft > 0 || cooldown > 0) return Component.translatable("message.steelrain.busy");
        if (primaryCount() <= 0) return Component.translatable("message.steelrain.no_ammo");
        if (!hasFuel(type.fuelPerShot)) return Component.translatable("message.steelrain.no_fuel", type.fuelPerShot);
        double dx = x + 0.5 - (worldPosition.getX() + 0.5), dz = z + 0.5 - (worldPosition.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < type.minRange()) return Component.translatable("message.steelrain.too_close", type.minRange());
        if (dist > type.maxRange()) return Component.translatable("message.steelrain.too_far", type.maxRange());
        salvoLeft = type == WeaponType.REAPER_PAD ? 1 : Math.max(1, Math.min(count, primaryCount()));
        salvoX = x + 0.5;
        salvoZ = z + 0.5;
        salvoOwner = player.getUUID();
        salvoOwnerName = player.getGameProfile().getName();
        // Keep this launcher ticking even if the operator is far away or walks off mid-salvo.
        if (level instanceof ServerLevel sl) FlyingWeaponEntity.preload(sl, worldPosition.getX(), worldPosition.getZ());
        setChanged();
        return null;
    }

    private boolean fireOne(ServerLevel level) {
        if (primaryCount() <= 0 || !hasFuel(type.fuelPerShot)) return false;
        items.extractItem(SLOT_PRIMARY, 1, false);
        if (Config.REQUIRE_FUEL.get()) burn(type.fuelPerShot);
        Vec3 muzzle = Vec3.atBottomCenterOf(worldPosition).add(0, type == WeaponType.ISKANDER_LAUNCHER ? 2.4 : 1.6, 0);
        UUID o = salvoOwner != null ? salvoOwner : owner;
        String on = salvoOwner != null ? salvoOwnerName : ownerName;
        boolean last = salvoLeft <= 1;
        switch (type) {
            case TOMAHAWK_LAUNCHER -> MissileEntity.launch(level, MissileProfile.TOMAHAWK, muzzle, salvoX, salvoZ, o, on, true);
            case ISKANDER_LAUNCHER -> MissileEntity.launch(level, MissileProfile.ISKANDER, muzzle, salvoX, salvoZ, o, on, true);
            case HIMARS -> MissileEntity.launch(level, MissileProfile.GMLRS, muzzle, salvoX, salvoZ, o, on, last);
            case M109 -> MissileEntity.launch(level, MissileProfile.SHELL, muzzle, salvoX, salvoZ, o, on, last);
            case SHAHED_LAUNCHER -> MissileEntity.launch(level, MissileProfile.SHAHED, muzzle, salvoX, salvoZ, o, on, true);
            case REAPER_PAD -> {
                int hellfires = items.extractItem(SLOT_SECONDARY, 4, false).getCount();
                ReaperEntity.launch(level, worldPosition, salvoX, salvoZ, hellfires, o, on);
            }
            default -> { }
        }
        cooldown = type.cooldown;
        boolean gun = type == WeaponType.M109;
        level.playSound(null, worldPosition, gun ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.BLOCKS, 6f, gun ? 1.6f : 0.6f);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 20, 0.5, 0.3, 0.5, 0.03);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.05);
        return true;
    }

    /** A returning Reaper puts itself and its unused missiles back into the pad. */
    public void dockReaper(int hellfires) {
        ItemStack left = items.insertItem(SLOT_PRIMARY, new ItemStack(ModRegistry.MQ9_REAPER.get()), false);
        if (!left.isEmpty() && level != null) Block.popResource(level, worldPosition.above(), left);
        if (hellfires > 0) {
            ItemStack rest = items.insertItem(SLOT_SECONDARY, new ItemStack(ModRegistry.HELLFIRE_MISSILE.get(), hellfires), false);
            if (!rest.isEmpty() && level != null) Block.popResource(level, worldPosition.above(), rest);
        }
        cooldown = 60;
    }

    // ---- air defence ----

    private void scanSky(ServerLevel level) {
        double range = Config.AIR_DEFENCE_RANGE.get();
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        long now = level.getGameTime();
        FlyingWeaponEntity target = level.getEntitiesOfClass(FlyingWeaponEntity.class, new AABB(centre, centre).inflate(range),
                        e -> e.interceptable() && e.engagedUntil < now
                                && e.distanceToSqr(centre) <= range * range
                                && !Teams.friendly(level, owner, ownerName, e.ownerId(), e.ownerName()))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(centre))).orElse(null);
        if (target == null) return;
        items.extractItem(SLOT_PRIMARY, 1, false);
        InterceptorEntity missile = new InterceptorEntity(ModRegistry.INTERCEPTOR.get(), level);
        missile.setPos(centre.add(0, 1.2, 0));
        missile.setOwner(owner, ownerName);
        missile.setTarget(target);
        level.addFreshEntity(missile);
        target.engagedUntil = now + 50;
        cooldown = type.cooldown;
        level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 5f, 1.4f);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 1, centre.z, 15, 0.3, 0.3, 0.3, 0.05);
    }

    // ---- jammer ----

    private void jam(ServerLevel level) {
        if (Config.REQUIRE_FUEL.get()) {
            if (fuel <= 0) return;
            burn(1);
        }
        double range = Config.JAMMER_RANGE.get();
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        for (FlyingWeaponEntity e : level.getEntitiesOfClass(FlyingWeaponEntity.class, new AABB(centre, centre).inflate(range),
                e -> e.jammable() && e.distanceToSqr(centre) <= range * range
                        && !Teams.friendly(level, owner, ownerName, e.ownerId(), e.ownerName()))) {
            e.jam();
        }
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() { return Component.translatable(type.translationKey()); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new WeaponMenu(id, inv, this, data);
    }

    // ---- persistence ----

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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
    }
}
