package com.steelrain.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelrain.ModRegistry;
import com.steelrain.weapon.AircraftType;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping EXIT_DRONE = new KeyMapping("key.steelrain.exit_drone",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.steelrain");

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.FPV.get(), c -> new EntityModelRenderer<>(c, Models.FPV, 0.14f, 0.25f));
        e.registerEntityRenderer(ModRegistry.FIBER_FPV.get(), c -> new EntityModelRenderer<>(c, Models.FIBER_FPV, 0.14f, 0.25f));
        e.registerEntityRenderer(ModRegistry.BULLET.get(), c -> new EntityModelRenderer<>(c, Models.BULLET, 0.1f, 1f).fullBright());
        e.registerEntityRenderer(ModRegistry.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.MAVIC_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.MAVIC, 0.12f, 0f));
        e.registerEntityRenderer(ModRegistry.SHAHED.get(), c -> new EntityModelRenderer<>(c, Models.SHAHED, 0.3f, 1f));
        e.registerEntityRenderer(ModRegistry.TOMAHAWK.get(), c -> new EntityModelRenderer<>(c, Models.TOMAHAWK, 0.4f, 1f));
        e.registerEntityRenderer(ModRegistry.ISKANDER.get(), c -> new EntityModelRenderer<>(c, Models.ISKANDER, 0.5f, 1f));
        e.registerEntityRenderer(ModRegistry.GMLRS.get(), c -> new EntityModelRenderer<>(c, Models.GMLRS, 0.25f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_CLUSTER_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_SMOKE_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_INCENDIARY_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.ATACMS.get(), c -> new EntityModelRenderer<>(c, Models.ATACMS, 0.4f, 1f));
        e.registerEntityRenderer(ModRegistry.BOMBLET.get(), c -> new EntityModelRenderer<>(c, Models.GRENADE, 0.12f, 1f));
        e.registerEntityRenderer(ModRegistry.SMOKE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.HELLFIRE.get(), c -> new EntityModelRenderer<>(c, Models.HELLFIRE, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.GRENADE.get(), c -> new EntityModelRenderer<>(c, Models.GRENADE, 0.12f, 1f));
        e.registerEntityRenderer(ModRegistry.INTERCEPTOR.get(), c -> new EntityModelRenderer<>(c, Models.PANTSIR_MISSILE, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.PATRIOT_INTERCEPTOR.get(), c -> new EntityModelRenderer<>(c, Models.PATRIOT_MISSILE, 0.3f, 1f));
        for (AircraftType type : AircraftType.values()) {
            e.registerEntityRenderer(ModRegistry.AIRCRAFT.get(type).get(),
                    c -> new EntityModelRenderer<>(c, Models.aircraft(type), (float) type.gear, 1f));
        }
        e.registerEntityRenderer(ModRegistry.OPERATOR_BODY.get(), OperatorBodyRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.WEAPON_BE.get(), c -> new WeaponRenderer());
    }

    /** With Curios installed, backpacks worn in the back slot are drawn on the player. */
    @SubscribeEvent
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
        if (com.steelrain.util.Gear.CURIOS) e.enqueueWork(CuriosClient::register);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent e) {
        e.register(ModRegistry.WEAPON_MENU.get(), WeaponScreen::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(EXIT_DRONE);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "fpv_hud"), FpvClient::renderHud);
    }

    private ClientSetup() {}
}
