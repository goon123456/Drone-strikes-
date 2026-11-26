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
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping EXIT_DRONE = new KeyMapping("key.steelrain.exit_drone",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.steelrain");
    public static final KeyMapping SWITCH_SEAT = new KeyMapping("key.steelrain.switch_seat",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.steelrain");
    public static final KeyMapping AUTO_MODE = new KeyMapping("key.steelrain.auto_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.steelrain");
    public static final KeyMapping SHELL_TYPE = new KeyMapping("key.steelrain.shell_type",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.steelrain");

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.FPV.get(), c -> new EntityModelRenderer<>(c, Models.FPV, 0.14f, 0.25f));
        e.registerEntityRenderer(ModRegistry.FIBER_FPV.get(), c -> new EntityModelRenderer<>(c, Models.FIBER_FPV, 0.14f, 0.25f));
        e.registerEntityRenderer(ModRegistry.MAVIC_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.MAVIC, 0.12f, 0f));
        e.registerEntityRenderer(ModRegistry.SHAHED.get(), c -> new EntityModelRenderer<>(c, Models.SHAHED, 0.3f, 1f));
        e.registerEntityRenderer(ModRegistry.TOMAHAWK.get(), c -> new EntityModelRenderer<>(c, Models.TOMAHAWK, 0.4f, 1f));
        e.registerEntityRenderer(ModRegistry.ISKANDER.get(), c -> new EntityModelRenderer<>(c, Models.ISKANDER, 0.5f, 1f));
        e.registerEntityRenderer(ModRegistry.GMLRS.get(), c -> new EntityModelRenderer<>(c, Models.GMLRS, 0.25f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_CLUSTER_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_SMOKE_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.SHELL_INCENDIARY_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.SHELL, 0.2f, 1f));
        e.registerEntityRenderer(ModRegistry.MORTAR_SHELL_ENTITY.get(), c -> new EntityModelRenderer<>(c, Models.MORTAR_BOMB, 0.15f, 1f));
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
        e.registerEntityRenderer(ModRegistry.ZU23.get(), c -> new SrVehicleRenderer<>(c, "zu_23_2"));
        e.registerEntityRenderer(ModRegistry.GEPARD.get(), c -> new SrVehicleRenderer<>(c, "gepard"));
        e.registerEntityRenderer(ModRegistry.PANTSIR.get(), c -> new SrVehicleRenderer<>(c, "pantsir"));
        e.registerEntityRenderer(ModRegistry.PATRIOT.get(), c -> new SrVehicleRenderer<>(c, "patriot"));
        e.registerEntityRenderer(ModRegistry.CANNON_SHELL.get(), CannonShellRenderer::new);
        e.registerEntityRenderer(ModRegistry.HIMARS.get(), c -> new SrVehicleRenderer<>(c, "himars"));
        e.registerEntityRenderer(ModRegistry.M109.get(), c -> new SrVehicleRenderer<>(c, "m109"));
        e.registerEntityRenderer(ModRegistry.MORTAR.get(), c -> new SrVehicleRenderer<>(c, "mortar_120"));
        e.registerEntityRenderer(ModRegistry.TOMAHAWK_LAUNCHER.get(), c -> new SrVehicleRenderer<>(c, "tomahawk_launcher"));
        e.registerEntityRenderer(ModRegistry.ISKANDER_LAUNCHER.get(), c -> new SrVehicleRenderer<>(c, "iskander_launcher"));
        e.registerEntityRenderer(ModRegistry.JAMMER.get(), c -> new SrVehicleRenderer<>(c, "jammer"));
        e.registerEntityRenderer(ModRegistry.TRENCH_JAMMER.get(), c -> new SrVehicleRenderer<>(c, "trench_jammer"));
        e.registerEntityRenderer(ModRegistry.KRASUKHA.get(), c -> new SrVehicleRenderer<>(c, "krasukha"));
        e.registerEntityRenderer(ModRegistry.OPERATOR_BODY.get(), OperatorBodyRenderer::new);
        e.registerBlockEntityRenderer(ModRegistry.WEAPON_BE.get(), c -> new WeaponRenderer());
    }

    /** With Curios installed, backpacks worn in the back slot are drawn on the player. */
    @SubscribeEvent
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
        if (com.steelrain.util.Gear.CURIOS) e.enqueueWork(CuriosClient::register);
    }

    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) {
        e.register(ModRegistry.WEAPON_MENU.get(), WeaponScreen::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(EXIT_DRONE);
        e.register(SWITCH_SEAT);
        e.register(SHELL_TYPE);
        e.register(AUTO_MODE);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "fpv_hud"), FpvClient::renderHud);
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "gun_sight"), ArtilleryHud::render);
    }

    private ClientSetup() {}
}
