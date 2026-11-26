package com.steelrain;

import com.steelrain.block.WeaponBlock;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.block.WeaponBlockItem;
import com.steelrain.block.WeaponMenu;
import com.steelrain.block.WeaponPartBlock;
import com.steelrain.block.WeaponPartBlockEntity;
import com.steelrain.weapon.WeaponType;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.OperatorBodyEntity;
import com.steelrain.weapon.AircraftType;
import com.steelrain.item.FpvControllerItem;
import com.steelrain.item.FpvDroneItem;
import com.steelrain.item.LaptopItem;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModRegistry {
    public static final String MODID = "steelrain";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    // ---- sounds Superb Warfare does not have: installed by tools/gen_sounds.py ----
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final Supplier<net.minecraft.sounds.SoundEvent> FPV_LOW = sound("fpv_low");
    public static final Supplier<net.minecraft.sounds.SoundEvent> FPV_MEDIUM = sound("fpv_medium");
    public static final Supplier<net.minecraft.sounds.SoundEvent> FPV_HIGH = sound("fpv_high");
    public static final Supplier<net.minecraft.sounds.SoundEvent> FPV_ACCELERATE = sound("fpv_accelerate");
    public static final Supplier<net.minecraft.sounds.SoundEvent> MAVIC_LOW = sound("mavic_low");
    public static final Supplier<net.minecraft.sounds.SoundEvent> MAVIC_MEDIUM = sound("mavic_medium");
    public static final Supplier<net.minecraft.sounds.SoundEvent> MAVIC_HIGH = sound("mavic_high");
    public static final Supplier<net.minecraft.sounds.SoundEvent> MAVIC_ACCELERATE = sound("mavic_accelerate");
    public static final Supplier<net.minecraft.sounds.SoundEvent> SHAHED_ENGINE = sound("shahed_engine");
    public static final Supplier<net.minecraft.sounds.SoundEvent> REAPER_ENGINE = sound("reaper_engine");
    public static final Supplier<net.minecraft.sounds.SoundEvent> TB2_ENGINE = sound("tb2_engine");
    public static final Supplier<net.minecraft.sounds.SoundEvent> ORLAN_ENGINE = sound("orlan_engine");
    public static final Supplier<net.minecraft.sounds.SoundEvent> GLOBAL_HAWK_JET = sound("global_hawk_jet");
    public static final Supplier<net.minecraft.sounds.SoundEvent> JAMMER_HUM = sound("jammer_hum");
    public static final Supplier<net.minecraft.sounds.SoundEvent> STATIC_LIGHT = sound("static_light");
    public static final Supplier<net.minecraft.sounds.SoundEvent> STATIC_MEDIUM = sound("static_medium");
    public static final Supplier<net.minecraft.sounds.SoundEvent> STATIC_HEAVY = sound("static_heavy");
    public static final Supplier<net.minecraft.sounds.SoundEvent> CABLE_SNAP = sound("cable_snap");

    private static Supplier<net.minecraft.sounds.SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    // ---- data components ----
    public static final Supplier<DataComponentType<List<BlockPos>>> LINKS = COMPONENTS.register("links",
            () -> DataComponentType.<List<BlockPos>>builder()
                    .persistent(BlockPos.CODEC.listOf())
                    .networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());
    /** Superb Warfare strike vehicles a laptop is linked to. */
    public static final Supplier<DataComponentType<List<UUID>>> VEHICLE_LINKS = COMPONENTS.register("vehicle_links",
            () -> DataComponentType.<List<UUID>>builder()
                    .persistent(UUIDUtil.CODEC.listOf())
                    .networkSynchronized(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());
    public static final Supplier<DataComponentType<UUID>> LINKED_DRONE = COMPONENTS.register("linked_drone",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    /** Whether a switchable item (jammer backpack) is on. */
    public static final Supplier<DataComponentType<Boolean>> ACTIVE = COMPONENTS.register("active",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());
    /** Blocks of fibre-optic cable left on a spool backpack. */
    public static final Supplier<DataComponentType<Integer>> CABLE = COMPONENTS.register("cable",
            () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Remaining charge (ticks) of a drone that was picked up. */
    public static final Supplier<DataComponentType<Integer>> BATTERY = COMPONENTS.register("battery",
            () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // ---- components ----
    public static final DeferredItem<Item> CIRCUIT_BOARD = simple("circuit_board", 64);
    public static final DeferredItem<Item> ELECTRIC_MOTOR = simple("electric_motor", 64);
    public static final DeferredItem<Item> DRONE_BATTERY = simple("drone_battery", 64);
    public static final DeferredItem<Item> PROPELLER = simple("propeller", 64);
    public static final DeferredItem<Item> ROCKET_ENGINE = simple("rocket_engine", 64);
    public static final DeferredItem<Item> WARHEAD = simple("warhead", 64);
    public static final DeferredItem<Item> GUIDANCE_UNIT = simple("guidance_unit", 64);

    // ---- munitions ----
    public static final DeferredItem<Item> SHAHED_136 = simple("shahed_136", 8);
    public static final DeferredItem<Item> MQ9_REAPER = simple("mq9_reaper", 1);
    public static final DeferredItem<Item> HELLFIRE_MISSILE = simple("hellfire_missile", 8);
    public static final DeferredItem<Item> TOMAHAWK_MISSILE = simple("tomahawk_missile", 4);
    public static final DeferredItem<Item> ISKANDER_MISSILE = simple("iskander_missile", 2);
    public static final DeferredItem<Item> GMLRS_ROCKET = simple("gmlrs_rocket", 12);
    public static final DeferredItem<Item> SHELL_155MM = simple("shell_155mm", 32);
    public static final DeferredItem<Item> PANTSIR_MISSILE = simple("pantsir_missile", 12);
    public static final DeferredItem<Item> PATRIOT_MISSILE = simple("patriot_missile", 4);
    public static final DeferredItem<Item> ORLAN_10 = simple("orlan_10", 1);
    public static final DeferredItem<Item> BAYRAKTAR_TB2 = simple("bayraktar_tb2", 1);
    public static final DeferredItem<Item> GLOBAL_HAWK = simple("global_hawk", 1);
    public static final DeferredItem<Item> DRONE_GRENADE = simple("drone_grenade", 16);
    public static final DeferredItem<Item> SHELL_CLUSTER = simple("shell_cluster", 32);
    public static final DeferredItem<Item> SHELL_SMOKE = simple("shell_smoke", 32);
    public static final DeferredItem<Item> SHELL_INCENDIARY = simple("shell_incendiary", 32);
    public static final DeferredItem<Item> MORTAR_SHELL = simple("mortar_shell", 32);
    public static final DeferredItem<Item> ATACMS_MISSILE = simple("atacms_missile", 1);
    public static final DeferredItem<Item> AMMO_30MM_AP = simple("ammo_30mm_ap", 16);
    public static final DeferredItem<Item> AMMO_35MM_AP = simple("ammo_35mm_ap", 16);
    public static final DeferredItem<Item> AMMO_23MM_AP = simple("ammo_23mm_ap", 16);
    public static final DeferredItem<Item> AMMO_30MM = simple("ammo_30mm", 16);
    public static final DeferredItem<Item> AMMO_35MM = simple("ammo_35mm", 16);
    public static final DeferredItem<Item> AMMO_23MM = simple("ammo_23mm", 16);
    public static final DeferredItem<Item> FIBER_SPOOL = simple("fiber_spool", 16);
    public static final DeferredItem<Item> JAMMER_DOME = simple("jammer_dome", 16);
    public static final DeferredItem<Item> FIBER_BACKPACK = ITEMS.register("fiber_backpack",
            () -> new com.steelrain.item.FiberBackpackItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> JAMMER_BACKPACK = ITEMS.register("jammer_backpack",
            () -> new com.steelrain.item.JammerBackpackItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ANTI_DRONE_GUN = ITEMS.register("anti_drone_gun",
            () -> new com.steelrain.item.AntiDroneGunItem(new Item.Properties().stacksTo(1)));

    // ---- vehicles and their upkeep ----
    public static final DeferredItem<Item> JERRYCAN = simple("jerrycan", 16);
    public static final DeferredItem<Item> DIESEL_JERRYCAN = simple("diesel_jerrycan", 4);
    public static final DeferredItem<Item> HIMARS_ITEM = ITEMS.register("himars",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.HIMARS.get()));
    public static final DeferredItem<Item> MORTAR_ITEM = ITEMS.register("mortar_120",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.MORTAR.get()));
    public static final DeferredItem<Item> TOMAHAWK_LAUNCHER_ITEM = ITEMS.register("tomahawk_launcher",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.TOMAHAWK_LAUNCHER.get()));
    public static final DeferredItem<Item> ISKANDER_LAUNCHER_ITEM = ITEMS.register("iskander_launcher",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.ISKANDER_LAUNCHER.get()));
    public static final DeferredItem<Item> ZU23_ITEM = ITEMS.register("zu_23_2",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.ZU23.get()));
    public static final DeferredItem<Item> GEPARD_ITEM = ITEMS.register("gepard",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.GEPARD.get()));
    public static final DeferredItem<Item> PANTSIR_ITEM = ITEMS.register("pantsir",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.PANTSIR.get()));
    public static final DeferredItem<Item> PATRIOT_ITEM = ITEMS.register("patriot",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.PATRIOT.get()));
    public static final DeferredItem<Item> JAMMER_ITEM = ITEMS.register("jammer",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.JAMMER.get()));
    public static final DeferredItem<Item> TRENCH_JAMMER_ITEM = ITEMS.register("trench_jammer",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.TRENCH_JAMMER.get()));
    public static final DeferredItem<Item> KRASUKHA_ITEM = ITEMS.register("krasukha",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.KRASUKHA.get()));
    public static final DeferredItem<Item> M109_ITEM = ITEMS.register("m109",
            () -> new com.steelrain.item.VehicleItem(new Item.Properties().stacksTo(1), () -> ModRegistry.M109.get()));

    // ---- tools ----
    public static final DeferredItem<Item> FIELD_LAPTOP = ITEMS.register("field_laptop",
            () -> new LaptopItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FPV_CONTROLLER = ITEMS.register("fpv_controller",
            () -> new FpvControllerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FPV_DRONE = ITEMS.register("fpv_drone",
            () -> new FpvDroneItem(new Item.Properties().stacksTo(16), 0));
    public static final DeferredItem<Item> FIBER_FPV_DRONE = ITEMS.register("fiber_fpv_drone",
            () -> new FpvDroneItem(new Item.Properties().stacksTo(16), 2));
    public static final DeferredItem<Item> MAVIC = ITEMS.register("mavic",
            () -> new FpvDroneItem(new Item.Properties().stacksTo(1), 1));

    // ---- weapon blocks ----
    public static final Map<WeaponType, DeferredBlock<WeaponBlock>> WEAPON_BLOCKS = new EnumMap<>(WeaponType.class);
    static {
        for (WeaponType type : WeaponType.values()) {
            DeferredBlock<WeaponBlock> block = BLOCKS.register(type.id, () -> new WeaponBlock(type,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4f, 12f)
                            .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .isSuffocating((st, l, p) -> false).isViewBlocking((st, l, p) -> false)));
            WEAPON_BLOCKS.put(type, block);
            ITEMS.register(type.id, () -> new WeaponBlockItem(block.get(), new Item.Properties().stacksTo(1)));
        }
    }

    /** Invisible filler that makes the rest of a full-size weapon solid and clickable. */
    public static final DeferredBlock<WeaponPartBlock> WEAPON_PART = BLOCKS.register("weapon_part",
            () -> new WeaponPartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4f, 12f)
                    .sound(SoundType.METAL).noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .isSuffocating((st, l, p) -> false).isViewBlocking((st, l, p) -> false)));
    public static final Supplier<BlockEntityType<WeaponPartBlockEntity>> WEAPON_PART_BE = BLOCK_ENTITIES.register("weapon_part",
            () -> BlockEntityType.Builder.of(WeaponPartBlockEntity::new, WEAPON_PART.get()).build(null));

    public static final Supplier<BlockEntityType<WeaponBlockEntity>> WEAPON_BE = BLOCK_ENTITIES.register("weapon",
            () -> BlockEntityType.Builder.of(WeaponBlockEntity::new,
                    WEAPON_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final Supplier<MenuType<WeaponMenu>> WEAPON_MENU = MENUS.register("weapon",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new WeaponMenu(id, inv, buf.readBlockPos())));

    // ---- entities ----
    private static final Map<String, MissileProfile> PROFILE_BY_NAME = new HashMap<>();
    private static final Map<String, DeferredHolder<EntityType<?>, EntityType<MissileEntity>>> TYPE_BY_NAME = new HashMap<>();

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHAHED = missile(MissileProfile.SHAHED, 1.2f, 0.6f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> TOMAHAWK = missile(MissileProfile.TOMAHAWK, 0.8f, 0.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> ISKANDER = missile(MissileProfile.ISKANDER, 1.0f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> GMLRS = missile(MissileProfile.GMLRS, 0.5f, 0.5f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHELL = missile(MissileProfile.SHELL, 0.4f, 0.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> HELLFIRE = missile(MissileProfile.HELLFIRE, 0.4f, 0.4f);

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> GRENADE = missile(MissileProfile.GRENADE, 0.25f, 0.25f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHELL_CLUSTER_ENTITY = missile(MissileProfile.SHELL_CLUSTER, 0.4f, 0.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHELL_SMOKE_ENTITY = missile(MissileProfile.SHELL_SMOKE, 0.4f, 0.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHELL_INCENDIARY_ENTITY = missile(MissileProfile.SHELL_INCENDIARY, 0.4f, 0.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> ATACMS = missile(MissileProfile.ATACMS, 0.8f, 0.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MORTAR_SHELL_ENTITY = missile(MissileProfile.MORTAR_SHELL, 0.3f, 0.3f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> BOMBLET = missile(MissileProfile.BOMBLET, 0.25f, 0.25f);
    /** Smoke screen left by a smoke shell. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.SmokeCloudEntity>> SMOKE = ENTITIES.register("smoke_cloud",
            () -> EntityType.Builder.<com.steelrain.entity.SmokeCloudEntity>of(com.steelrain.entity.SmokeCloudEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(4).build("smoke_cloud"));

    public static final Map<AircraftType, DeferredHolder<EntityType<?>, EntityType<AircraftEntity>>> AIRCRAFT = new EnumMap<>(AircraftType.class);
    static {
        for (AircraftType type : AircraftType.values()) {
            float w = (float) Math.max(1.0, Math.min(6.0, type.span * 0.3));
            float h = (float) Math.max(0.6, type.gear + 0.6);
            AIRCRAFT.put(type, ENTITIES.register(type.id, () -> EntityType.Builder.<AircraftEntity>of(AircraftEntity::new, MobCategory.MISC)
                    .sized(w, h).clientTrackingRange(24).updateInterval(1).build(type.id)));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.CannonShellEntity>> CANNON_SHELL = ENTITIES.register("cannon_shell",
            () -> EntityType.Builder.<com.steelrain.entity.CannonShellEntity>of(com.steelrain.entity.CannonShellEntity::new, MobCategory.MISC)
                    .sized(0.15f, 0.15f).clientTrackingRange(16).updateInterval(5).noSave().build("cannon_shell"));
    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorEntity>> INTERCEPTOR = ENTITIES.register("pantsir_missile",
            () -> EntityType.Builder.<InterceptorEntity>of(InterceptorEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(20).updateInterval(1).build("pantsir_missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorEntity>> PATRIOT_INTERCEPTOR = ENTITIES.register("patriot_missile",
            () -> EntityType.Builder.<InterceptorEntity>of(InterceptorEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(24).updateInterval(1).build("patriot_missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<FpvDroneEntity>> FPV = ENTITIES.register("fpv_drone",
            () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.25f).eyeHeight(0.12f).clientTrackingRange(24).updateInterval(1).build("fpv_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<FpvDroneEntity>> FIBER_FPV = ENTITIES.register("fiber_fpv_drone",
            () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.25f).eyeHeight(0.12f).clientTrackingRange(24).updateInterval(1).build("fiber_fpv_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<FpvDroneEntity>> MAVIC_ENTITY = ENTITIES.register("mavic",
            () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.45f, 0.2f).eyeHeight(0.05f).clientTrackingRange(24).updateInterval(1).build("mavic"));
    // ---- air defence ----
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Zu23>> ZU23 = ENTITIES.register("zu_23_2",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Zu23>of(com.steelrain.entity.vehicle.Zu23::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(2.4f, 1.9f).build("zu_23_2"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Gepard>> GEPARD = ENTITIES.register("gepard",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Gepard>of(com.steelrain.entity.vehicle.Gepard::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(3.3f, 3.3f).build("gepard"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Pantsir>> PANTSIR = ENTITIES.register("pantsir",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Pantsir>of(com.steelrain.entity.vehicle.Pantsir::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(3.0f, 3.6f).build("pantsir"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Patriot>> PATRIOT = ENTITIES.register("patriot",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Patriot>of(com.steelrain.entity.vehicle.Patriot::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(3.0f, 3.4f).build("patriot"));

    /** Steel Rain's own vehicles (moving off Superb Warfare one at a time). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Himars>> HIMARS = ENTITIES.register("himars",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Himars>of(com.steelrain.entity.vehicle.Himars::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(2.6f, 3.0f).build("himars"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Mortar>> MORTAR = ENTITIES.register("mortar_120",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Mortar>of(com.steelrain.entity.vehicle.Mortar::new, MobCategory.MISC)
                    .setTrackingRange(256).setUpdateInterval(1).sized(1.0f, 1.0f).build("mortar_120"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.TomahawkLauncher>> TOMAHAWK_LAUNCHER = ENTITIES.register("tomahawk_launcher",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.TomahawkLauncher>of(com.steelrain.entity.vehicle.TomahawkLauncher::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(2.6f, 3.2f).build("tomahawk_launcher"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.IskanderLauncher>> ISKANDER_LAUNCHER = ENTITIES.register("iskander_launcher",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.IskanderLauncher>of(com.steelrain.entity.vehicle.IskanderLauncher::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(3.0f, 3.3f).build("iskander_launcher"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.M109>> M109 = ENTITIES.register("m109",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.M109>of(com.steelrain.entity.vehicle.M109::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(3.1f, 3.0f).build("m109"));

    // ---- jammers ----
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Jammer>> JAMMER = ENTITIES.register("jammer",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Jammer>of(com.steelrain.entity.vehicle.Jammer::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(2.8f, 2.8f).build("jammer"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Jammer>> TRENCH_JAMMER = ENTITIES.register("trench_jammer",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Jammer>of(com.steelrain.entity.vehicle.Jammer::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(0.8f, 1.0f).build("trench_jammer"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.steelrain.entity.vehicle.Jammer>> KRASUKHA = ENTITIES.register("krasukha",
            () -> EntityType.Builder.<com.steelrain.entity.vehicle.Jammer>of(com.steelrain.entity.vehicle.Jammer::new, MobCategory.MISC)
                    .setTrackingRange(512).setUpdateInterval(1).fireImmune().sized(2.8f, 3.4f).build("krasukha"));

    /** Stand-in left where a player was standing while they watch a remote drone feed. */
    public static final DeferredHolder<EntityType<?>, EntityType<OperatorBodyEntity>> OPERATOR_BODY = ENTITIES.register("operator_body",
            () -> EntityType.Builder.<OperatorBodyEntity>of(OperatorBodyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).noSave().build("operator_body"));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.steelrain"))
            .icon(() -> new ItemStack(TOMAHAWK_MISSILE.get()))
            .displayItems((params, out) -> {
                ITEMS.getEntries().forEach(e -> out.accept(e.get()));
            })
            .build());

    private static DeferredItem<Item> simple(String name, int stack) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(stack)));
    }

    private static DeferredHolder<EntityType<?>, EntityType<MissileEntity>> missile(MissileProfile profile, float w, float h) {
        PROFILE_BY_NAME.put(profile.id(), profile);
        DeferredHolder<EntityType<?>, EntityType<MissileEntity>> holder = ENTITIES.register(profile.id(),
                () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                        .sized(w, h).clientTrackingRange(20).updateInterval(1).build(profile.id()));
        TYPE_BY_NAME.put(profile.id(), holder);
        return holder;
    }

    public static MissileProfile profileOf(EntityType<?> type) {
        MissileProfile p = PROFILE_BY_NAME.get(EntityType.getKey(type).getPath());
        return p != null ? p : MissileProfile.SHELL;
    }

    public static EntityType<MissileEntity> typeOf(MissileProfile profile) {
        return TYPE_BY_NAME.getOrDefault(profile.id(), SHELL).get();
    }

    /** What a loaded ammunition item turns into when it is fired. */
    public static MissileProfile profileOfAmmo(ItemStack stack, MissileProfile fallback) {
        if (stack.is(SHELL_CLUSTER.get())) return MissileProfile.SHELL_CLUSTER;
        if (stack.is(SHELL_SMOKE.get())) return MissileProfile.SHELL_SMOKE;
        if (stack.is(SHELL_INCENDIARY.get())) return MissileProfile.SHELL_INCENDIARY;
        if (stack.is(ATACMS_MISSILE.get())) return MissileProfile.ATACMS;
        return fallback;
    }

    public static boolean armourPiercing(ItemStack belt) {
        return belt.is(AMMO_30MM_AP.get()) || belt.is(AMMO_35MM_AP.get()) || belt.is(AMMO_23MM_AP.get());
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        COMPONENTS.register(bus);
    }

    private ModRegistry() {}
}
