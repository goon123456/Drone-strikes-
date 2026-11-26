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
        modBus.addListener(RegisterPayloadHandlersEvent.class, Payloads::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, SteelRain::fitDome);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.EntityTickEvent.Post.class, SteelRain::domeTick);
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

    private static final String DOME = "SteelRainDome", DOME_OWNER = "SteelRainDomeOwner", DOME_NAME = "SteelRainDomeOwnerName";

    /** A Jammer Dome can be fitted to any Superb Warfare vehicle by right-clicking it. */
    private static void fitDome(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v)
                || !e.getItemStack().is(ModRegistry.JAMMER_DOME.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getLevel().isClientSide) return;
        net.minecraft.nbt.CompoundTag data = v.getPersistentData();
        if (data.getBoolean(DOME) || v instanceof com.steelrain.entity.SupportVehicle s && s.kind().ordinal() >= 2) {
            e.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.steelrain.dome_refused"), true);
            return;
        }
        data.putBoolean(DOME, true);
        data.putUUID(DOME_OWNER, e.getEntity().getUUID());
        data.putString(DOME_NAME, e.getEntity().getGameProfile().getName());
        if (!e.getEntity().getAbilities().instabuild) e.getItemStack().shrink(1);
        e.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.steelrain.dome_fitted"), true);
    }

    /** A fitted dome keeps a small bubble up while the vehicle has energy, drawing a trickle from it. */
    private static void domeTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v)
                || !(v.level() instanceof net.minecraft.server.level.ServerLevel level) || v.tickCount % 10 != 0) return;
        net.minecraft.nbt.CompoundTag data = v.getPersistentData();
        if (!data.getBoolean(DOME) || v.isWreck()) return;
        if (Config.REQUIRE_ENERGY.get() && v.hasEnergyStorage()) {
            if (v.getEnergy() < 250) return;
            if (v.tickCount % 100 == 0) v.consumeEnergy(250);
        }
        com.steelrain.util.Jamming.emit(level, v.getUUID().toString() + "dome", v.position().add(0, v.getBbHeight(), 0),
                Config.DOME_JAMMER_RANGE.get(), data.hasUUID(DOME_OWNER) ? data.getUUID(DOME_OWNER) : null,
                data.contains(DOME_NAME) ? data.getString(DOME_NAME) : null);
    }
}
