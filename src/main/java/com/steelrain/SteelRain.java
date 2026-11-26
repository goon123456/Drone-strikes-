package com.steelrain;

import com.steelrain.block.WeaponBlockEntity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ModRegistry.MODID)
public class SteelRain {
    public SteelRain(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Payloads::register);
        modBus.addListener(RegisterCapabilitiesEvent.class, SteelRain::capabilities);
        modBus.addListener(EntityAttributeCreationEvent.class,
                e -> e.put(ModRegistry.OPERATOR_BODY.get(), OperatorBodyEntity.attributes().build()));
        // Also list our vehicles in Superb Warfare's own vehicle tab.
        modBus.addListener(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.class, e -> e.enqueueWork(() ->
                ModRegistry.vehicles().forEach(v -> com.atsuishio.superbwarfare.api.event.RegisterContainersEvent.CONTAINERS.add(
                        com.atsuishio.superbwarfare.item.container.ContainerBlockItem.createInstance(v)))));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> SteelRainCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> FeedManager.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) com.steelrain.util.Gear.tickPlayer(sp);
        });
        // Explosions cut fibre-optic cables that run close to them.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate.class, e -> {
            if (!e.getLevel().isClientSide) {
                com.steelrain.entity.FpvDroneEntity.explosionAt(e.getLevel(), e.getExplosion().center(), e.getExplosion().radius() + 1);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) FeedManager.onLogout(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) FeedManager.onLogin(sp);
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModRegistry.WEAPON_BE.get(), (be, side) -> be.items);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModRegistry.WEAPON_BE.get(), (be, side) -> be.energyPort);
        // Hoppers, pipes and cables can connect to any block of the vehicle, not just its core.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModRegistry.WEAPON_PART_BE.get(), (part, side) -> {
            WeaponBlockEntity core = part.coreEntity();
            return core == null ? null : core.items;
        });
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModRegistry.WEAPON_PART_BE.get(), (part, side) -> {
            WeaponBlockEntity core = part.coreEntity();
            return core == null ? null : core.energyPort;
        });
    }
}
