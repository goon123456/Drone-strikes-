package com.steelrain.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelrain.ModRegistry;
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
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(Models.FPV, Models::fpv);
        e.registerLayerDefinition(Models.SHAHED, Models::shahed);
        e.registerLayerDefinition(Models.REAPER, Models::reaper);
        e.registerLayerDefinition(Models.TOMAHAWK, Models::tomahawk);
        e.registerLayerDefinition(Models.ISKANDER, Models::iskander);
        e.registerLayerDefinition(Models.GMLRS, Models::gmlrs);
        e.registerLayerDefinition(Models.SHELL, Models::shell);
        e.registerLayerDefinition(Models.HELLFIRE, Models::hellfire);
        e.registerLayerDefinition(Models.INTERCEPTOR, Models::interceptor);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.FPV.get(), c -> new SimpleRenderer<>(c, Models.FPV, "fpv_drone", 1.2f, 0.25f));
        e.registerEntityRenderer(ModRegistry.SHAHED.get(), c -> new SimpleRenderer<>(c, Models.SHAHED, "shahed_136", 1.6f, 1f));
        e.registerEntityRenderer(ModRegistry.REAPER.get(), c -> new SimpleRenderer<>(c, Models.REAPER, "mq9_reaper", 2.5f, 1f));
        e.registerEntityRenderer(ModRegistry.TOMAHAWK.get(), c -> new SimpleRenderer<>(c, Models.TOMAHAWK, "tomahawk", 1.8f, 1f));
        e.registerEntityRenderer(ModRegistry.ISKANDER.get(), c -> new SimpleRenderer<>(c, Models.ISKANDER, "iskander", 2f, 1f));
        e.registerEntityRenderer(ModRegistry.GMLRS.get(), c -> new SimpleRenderer<>(c, Models.GMLRS, "gmlrs", 1.8f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL.get(), c -> new SimpleRenderer<>(c, Models.SHELL, "shell_155mm", 1.5f, 1f));
        e.registerEntityRenderer(ModRegistry.HELLFIRE.get(), c -> new SimpleRenderer<>(c, Models.HELLFIRE, "hellfire", 1.5f, 1f));
        e.registerEntityRenderer(ModRegistry.INTERCEPTOR.get(), c -> new SimpleRenderer<>(c, Models.INTERCEPTOR, "pantsir_missile", 1.8f, 1f));
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
