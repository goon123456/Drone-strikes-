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
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> SteelRainCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> FeedManager.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) FeedManager.onLogout(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) FeedManager.onLogin(sp);
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModRegistry.WEAPON_BE.get(), (be, side) -> be.items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModRegistry.WEAPON_BE.get(), (be, side) -> be.fuelPort);
    }
}
