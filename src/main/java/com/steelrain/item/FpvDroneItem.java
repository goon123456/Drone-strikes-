package com.steelrain.item;

import com.steelrain.ModRegistry;
import com.steelrain.entity.FpvDroneEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FpvDroneItem extends Item {
    /** 0 radio FPV, 1 Mavic, 2 fibre-optic FPV. */
    private final int kind;
    private final boolean mavic;

    public FpvDroneItem(Properties properties, int kind) {
        super(properties);
        this.kind = kind;
        this.mavic = kind == 1;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide) {
            Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace()));
            FpvDroneEntity drone = new FpvDroneEntity(kind == 1 ? ModRegistry.MAVIC_ENTITY.get() : kind == 2 ? ModRegistry.FIBER_FPV.get() : ModRegistry.FPV.get(), level);
            drone.setPos(at);
            Player player = ctx.getPlayer();
            if (player != null) {
                drone.setYRot(player.getYRot());
                drone.setOwner(player.getUUID(), player.getGameProfile().getName());
            }
            drone.setBattery(ctx.getItemInHand().getOrDefault(ModRegistry.BATTERY.get(), drone.fullBattery()));
            level.addFreshEntity(drone);
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(kind == 1 ? "tooltip.steelrain.mavic" : kind == 2 ? "tooltip.steelrain.fiber_fpv" : "tooltip.steelrain.fpv_drone"));
        Integer charge = stack.get(ModRegistry.BATTERY.get());
        if (charge != null) tooltip.add(Component.translatable("tooltip.steelrain.charge", charge / 20));
    }
}
