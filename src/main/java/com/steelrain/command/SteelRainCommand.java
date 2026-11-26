package com.steelrain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.steelrain.ModRegistry;
import com.steelrain.block.WeaponBlock;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.entity.MissileEntity;
import com.steelrain.weapon.WeaponType;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Operator tools for testing and map making: /steelrain launch ... and /steelrain place ... */
public final class SteelRainCommand {
    private static final Map<String, MissileProfile> WEAPONS = new LinkedHashMap<>();
    static {
        for (MissileProfile p : new MissileProfile[]{MissileProfile.SHAHED, MissileProfile.TOMAHAWK, MissileProfile.ISKANDER,
                MissileProfile.GMLRS, MissileProfile.SHELL, MissileProfile.HELLFIRE, MissileProfile.SHELL_CLUSTER,
                MissileProfile.SHELL_SMOKE, MissileProfile.SHELL_INCENDIARY, MissileProfile.ATACMS}) {
            WEAPONS.put(p.id(), p);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("steelrain").requires(s -> s.hasPermission(2))
                .then(Commands.literal("launch")
                        .then(Commands.argument("weapon", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(WEAPONS.keySet(), b))
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                .executes(SteelRainCommand::launch)))))
                .then(Commands.literal("place")
                        .then(Commands.argument("system", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(WeaponType.values()).map(t -> t.id), b))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("facing", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        java.util.List.of("north", "south", "east", "west"), b))
                                                .executes(SteelRainCommand::place))))));
    }

    /** /steelrain place <system> <pos> <facing> - puts down a complete full-size weapon system. */
    private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "system");
        WeaponType type = java.util.Arrays.stream(WeaponType.values()).filter(t -> t.id.equals(id)).findFirst().orElse(null);
        Direction facing = Direction.byName(StringArgumentType.getString(ctx, "facing"));
        if (type == null || facing == null || facing.getAxis().isVertical()) {
            ctx.getSource().sendFailure(Component.translatable("message.steelrain.unknown_weapon"));
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos blocked = level.getBlockState(pos).canBeReplaced() ? WeaponBlock.obstruction(level, pos, facing, type) : pos;
        if (blocked != null) {
            ctx.getSource().sendFailure(Component.translatable("message.steelrain.no_space",
                    type.width, type.height, type.length, blocked.getX(), blocked.getY(), blocked.getZ()));
            return 0;
        }
        level.setBlock(pos, ModRegistry.WEAPON_BLOCKS.get(type).get().defaultBlockState().setValue(WeaponBlock.FACING, facing), 3);
        WeaponBlock.placeParts(level, pos, facing, type);
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && level.getBlockEntity(pos) instanceof WeaponBlockEntity be) {
            be.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(type.translationKey()), true);
        return 1;
    }

    private static int launch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MissileProfile profile = WEAPONS.get(StringArgumentType.getString(ctx, "weapon"));
        if (profile == null) {
            ctx.getSource().sendFailure(Component.translatable("message.steelrain.unknown_weapon"));
            return 0;
        }
        BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos target = BlockPosArgument.getBlockPos(ctx, "target");
        ServerPlayer player = ctx.getSource().getPlayer();
        MissileEntity.launch(ctx.getSource().getLevel(), profile, Vec3.atBottomCenterOf(from).add(0, 1, 0),
                target.getX() + 0.5, target.getZ() + 0.5,
                player != null ? player.getUUID() : null, player != null ? player.getGameProfile().getName() : null, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.steelrain.launched", target.getX(), target.getZ()), true);
        return 1;
    }

    private SteelRainCommand() {}
}
