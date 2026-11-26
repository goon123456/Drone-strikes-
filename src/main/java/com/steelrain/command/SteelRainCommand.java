package com.steelrain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.steelrain.entity.MissileEntity;
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
                .then(Commands.literal("fire")
                        .then(Commands.argument("target", net.minecraft.commands.arguments.coordinates.ColumnPosArgument.columnPos())
                                .executes(c -> fire(c, 1))
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 12))
                                        .executes(c -> fire(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count")))))));
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

    /** Orders the nearest launcher within 64 blocks to fire at a target, as the Field Laptop would. */
    private static int fire(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var target = net.minecraft.commands.arguments.coordinates.ColumnPosArgument.getColumnPos(ctx, "target");
        Vec3 at = ctx.getSource().getPosition();
        var launchers = ctx.getSource().getLevel().getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(at, at).inflate(64), e -> e instanceof com.steelrain.weapon.Launcher);
        launchers.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        if (launchers.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.steelrain.no_launcher"));
            return 0;
        }
        Component refused = ((com.steelrain.weapon.Launcher) launchers.get(0)).tryLaunch(player, target.x(), target.z(), count);
        if (refused != null) {
            ctx.getSource().sendFailure(refused);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.steelrain.launched", target.x(), target.z()), true);
        return 1;
    }

    private SteelRainCommand() {}
}
