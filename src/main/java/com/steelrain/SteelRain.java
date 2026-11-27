package com.steelrain;

import com.steelrain.command.SteelRainCommand;
import com.steelrain.entity.OperatorBodyEntity;
import com.steelrain.net.Payloads;
import com.steelrain.util.FeedManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ModRegistry.MODID)
public class SteelRain {
    public SteelRain(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) container.registerConfig(ModConfig.Type.CLIENT, com.steelrain.client.ClientConfig.SPEC);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Payloads::register);
        modBus.addListener(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class, SteelRain::capabilities);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, SteelRain::fitDome);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.EntityTickEvent.Post.class, SteelRain::domeTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking.class, SteelRain::domeSeen);
        modBus.addListener(EntityAttributeCreationEvent.class,
                e -> e.put(ModRegistry.OPERATOR_BODY.get(), OperatorBodyEntity.attributes().build()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> SteelRainCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> FeedManager.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                com.steelrain.util.Gear.tickPlayer(sp);
                com.steelrain.gun.GunLogic.tick(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class, e -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) com.steelrain.gun.Bullets.tick(level);
        });
        // Explosions cut fibre-optic cables that run close to them.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate.class, e -> {
            if (!e.getLevel().isClientSide) {
                com.steelrain.entity.FpvDroneEntity.explosionAt(e.getLevel(), e.getExplosion().center(), e.getExplosion().radius() + 1);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) { FeedManager.onLogout(sp); com.steelrain.gun.GunLogic.forget(sp); }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) FeedManager.onLogin(sp);
        });
    }

    /** Hoppers and power cables can connect to any block of an emplacement. */
    private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        var items = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        var power = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
        event.registerBlockEntity(items, ModRegistry.WEAPON_BE.get(), (be, side) -> be.items);
        event.registerBlockEntity(power, ModRegistry.WEAPON_BE.get(), (be, side) -> be.energyPort);
        event.registerBlockEntity(items, ModRegistry.WEAPON_PART_BE.get(), (part, side) -> part.coreEntity() == null ? null : part.coreEntity().items);
        event.registerBlockEntity(power, ModRegistry.WEAPON_PART_BE.get(), (part, side) -> part.coreEntity() == null ? null : part.coreEntity().energyPort);
        // our own vehicles take FE from any mod's chargers into their battery
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.HIMARS.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.M109.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.JAMMER.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.TRENCH_JAMMER.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.KRASUKHA.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.ZU23.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.GEPARD.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.PANTSIR.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.PATRIOT.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.TOMAHAWK_LAUNCHER.get(), (v, ctx) -> v.energyPort);
        event.registerEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY, ModRegistry.ISKANDER_LAUNCHER.get(), (v, ctx) -> v.energyPort);
    }

    private static final String DOME = "SteelRainDome", DOME_OWNER = "SteelRainDomeOwner", DOME_NAME = "SteelRainDomeOwnerName";

    /** A Jammer Dome can be fitted to any Superb Warfare vehicle by right-clicking it. */
    private static void fitDome(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        net.minecraft.world.entity.Entity v = e.getTarget();
        if (!domeCarrier(v) || !e.getItemStack().is(ModRegistry.JAMMER_DOME.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getLevel().isClientSide) return;
        net.minecraft.nbt.CompoundTag data = v.getPersistentData();
        if (data.getBoolean(DOME) || v instanceof com.steelrain.entity.vehicle.Jammer) {
            e.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.steelrain.dome_refused"), true);
            return;
        }
        data.putBoolean(DOME, true);
        data.putUUID(DOME_OWNER, e.getEntity().getUUID());
        data.putString(DOME_NAME, e.getEntity().getGameProfile().getName());
        if (!e.getEntity().getAbilities().instabuild) e.getItemStack().shrink(1);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(v, new com.steelrain.net.Payloads.Dome(v.getId()));
        e.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.steelrain.dome_fitted"), true);
    }

    /** A player who comes within sight of a vehicle is told if it carries a dome, so their game can draw it. */
    private static void domeSeen(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking e) {
        if (domeCarrier(e.getTarget()) && e.getTarget().getPersistentData().getBoolean(DOME)
                && e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.steelrain.net.Payloads.Dome(e.getTarget().getId()));
        }
    }

    private static boolean domeCarrier(net.minecraft.world.entity.Entity e) {
        return e instanceof com.steelrain.entity.vehicle.SrVehicle || com.steelrain.compat.Sbw.isVehicle(e);
    }

    /** A fitted dome keeps a small bubble up while the vehicle has energy, drawing a trickle from it. */
    private static void domeTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) {
        net.minecraft.world.entity.Entity v = e.getEntity();
        if (!domeCarrier(v) || !(v.level() instanceof net.minecraft.server.level.ServerLevel level) || v.tickCount % 10 != 0) return;
        net.minecraft.nbt.CompoundTag data = v.getPersistentData();
        if (!data.getBoolean(DOME)) return;
        if (com.steelrain.compat.Sbw.isVehicle(v)) {
            if (!com.steelrain.compat.Sbw.domePower(v, 250, v.tickCount % 100 == 0)) return;
        } else if (v instanceof com.steelrain.entity.vehicle.SrVehicle own && Config.REQUIRE_ENERGY.get()) {
            if (own.energy() < 250) return;
            if (v.tickCount % 100 == 0) own.useEnergy(250);
        }
        com.steelrain.util.Jamming.emit(level, v.getUUID().toString() + "dome", v.position().add(0, v.getBbHeight(), 0),
                Config.DOME_JAMMER_RANGE.get(), data.hasUUID(DOME_OWNER) ? data.getUUID(DOME_OWNER) : null,
                data.contains(DOME_NAME) ? data.getString(DOME_NAME) : null);
    }
}
