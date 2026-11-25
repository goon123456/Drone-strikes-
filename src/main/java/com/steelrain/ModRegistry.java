package com.steelrain;

import com.steelrain.block.WeaponBlock;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.block.WeaponMenu;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.entity.InterceptorEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.entity.ReaperEntity;
import com.steelrain.item.FpvControllerItem;
import com.steelrain.item.FpvDroneItem;
import com.steelrain.item.LaptopItem;
import com.steelrain.weapon.MissileProfile;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
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
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Fluids a launcher tank accepts. Filled by other mods' diesel/gasoline through tags. */
    public static final TagKey<Fluid> LAUNCHER_FUEL = TagKey.create(Registries.FLUID,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "launcher_fuel"));

    // ---- data components ----
    public static final Supplier<DataComponentType<List<BlockPos>>> LINKS = COMPONENTS.register("links",
            () -> DataComponentType.<List<BlockPos>>builder()
                    .persistent(BlockPos.CODEC.listOf())
                    .networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());
    public static final Supplier<DataComponentType<UUID>> LINKED_DRONE = COMPONENTS.register("linked_drone",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    // ---- components ----
    public static final DeferredItem<Item> CIRCUIT_BOARD = simple("circuit_board", 64);
    public static final DeferredItem<Item> ELECTRIC_MOTOR = simple("electric_motor", 64);
    public static final DeferredItem<Item> DRONE_BATTERY = simple("drone_battery", 64);
    public static final DeferredItem<Item> PROPELLER = simple("propeller", 64);
    public static final DeferredItem<Item> ROCKET_ENGINE = simple("rocket_engine", 64);
    public static final DeferredItem<Item> WARHEAD = simple("warhead", 64);
    public static final DeferredItem<Item> GUIDANCE_UNIT = simple("guidance_unit", 64);
    public static final DeferredItem<Item> FUEL_CANISTER = simple("fuel_canister", 16);

    // ---- munitions ----
    public static final DeferredItem<Item> SHAHED_136 = simple("shahed_136", 8);
    public static final DeferredItem<Item> MQ9_REAPER = simple("mq9_reaper", 1);
    public static final DeferredItem<Item> HELLFIRE_MISSILE = simple("hellfire_missile", 8);
    public static final DeferredItem<Item> TOMAHAWK_MISSILE = simple("tomahawk_missile", 4);
    public static final DeferredItem<Item> ISKANDER_MISSILE = simple("iskander_missile", 2);
    public static final DeferredItem<Item> GMLRS_ROCKET = simple("gmlrs_rocket", 12);
    public static final DeferredItem<Item> SHELL_155MM = simple("shell_155mm", 32);
    public static final DeferredItem<Item> PANTSIR_MISSILE = simple("pantsir_missile", 12);

    // ---- tools ----
    public static final DeferredItem<Item> FIELD_LAPTOP = ITEMS.register("field_laptop",
            () -> new LaptopItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FPV_CONTROLLER = ITEMS.register("fpv_controller",
            () -> new FpvControllerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FPV_DRONE = ITEMS.register("fpv_drone",
            () -> new FpvDroneItem(new Item.Properties().stacksTo(16)));

    // ---- weapon blocks ----
    public static final Map<WeaponType, DeferredBlock<WeaponBlock>> WEAPON_BLOCKS = new EnumMap<>(WeaponType.class);
    static {
        for (WeaponType type : WeaponType.values()) {
            DeferredBlock<WeaponBlock> block = BLOCKS.register(type.id, () -> new WeaponBlock(type,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4f, 12f)
                            .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
            WEAPON_BLOCKS.put(type, block);
            ITEMS.register(type.id, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static final Supplier<BlockEntityType<WeaponBlockEntity>> WEAPON_BE = BLOCK_ENTITIES.register("weapon",
            () -> BlockEntityType.Builder.of(WeaponBlockEntity::new,
                    WEAPON_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final Supplier<MenuType<WeaponMenu>> WEAPON_MENU = MENUS.register("weapon",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new WeaponMenu(id, inv, buf.readBlockPos())));

    // ---- entities ----
    private static final Map<String, MissileProfile> PROFILE_BY_NAME = new HashMap<>();

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHAHED = missile(MissileProfile.SHAHED, 1.2f, 0.6f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> TOMAHAWK = missile(MissileProfile.TOMAHAWK, 0.8f, 0.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> ISKANDER = missile(MissileProfile.ISKANDER, 1.0f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> GMLRS = missile(MissileProfile.GMLRS, 0.5f, 0.5f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> SHELL = missile(MissileProfile.SHELL, 0.4f, 0.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> HELLFIRE = missile(MissileProfile.HELLFIRE, 0.4f, 0.4f);

    public static final DeferredHolder<EntityType<?>, EntityType<ReaperEntity>> REAPER = ENTITIES.register("mq9_reaper",
            () -> EntityType.Builder.<ReaperEntity>of(ReaperEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f).clientTrackingRange(20).updateInterval(1).build("mq9_reaper"));
    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorEntity>> INTERCEPTOR = ENTITIES.register("pantsir_missile",
            () -> EntityType.Builder.<InterceptorEntity>of(InterceptorEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(20).updateInterval(1).build("pantsir_missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<FpvDroneEntity>> FPV = ENTITIES.register("fpv_drone",
            () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.35f).eyeHeight(0.2f).clientTrackingRange(20).updateInterval(1).build("fpv_drone"));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.steelrain"))
            .icon(() -> new ItemStack(TOMAHAWK_MISSILE.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> out.accept(e.get())))
            .build());

    private static DeferredItem<Item> simple(String name, int stack) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(stack)));
    }

    private static DeferredHolder<EntityType<?>, EntityType<MissileEntity>> missile(MissileProfile profile, float w, float h) {
        PROFILE_BY_NAME.put(profile.id(), profile);
        return ENTITIES.register(profile.id(), () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                .sized(w, h).clientTrackingRange(20).updateInterval(1).build(profile.id()));
    }

    public static MissileProfile profileOf(EntityType<?> type) {
        MissileProfile p = PROFILE_BY_NAME.get(EntityType.getKey(type).getPath());
        return p != null ? p : MissileProfile.SHELL;
    }

    public static EntityType<MissileEntity> typeOf(MissileProfile profile) {
        if (profile == MissileProfile.SHAHED) return SHAHED.get();
        if (profile == MissileProfile.TOMAHAWK) return TOMAHAWK.get();
        if (profile == MissileProfile.ISKANDER) return ISKANDER.get();
        if (profile == MissileProfile.GMLRS) return GMLRS.get();
        if (profile == MissileProfile.HELLFIRE) return HELLFIRE.get();
        return SHELL.get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        COMPONENTS.register(bus);
    }

    private ModRegistry() {}
}
