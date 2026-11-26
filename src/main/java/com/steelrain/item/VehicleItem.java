package com.steelrain.item;

import com.steelrain.entity.vehicle.SrVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/**
 * A vehicle, ready to put down: right-click the ground and it appears there facing away from you, with a full
 * battery and a quarter of a tank. The owner packs it back up by hitting it while sneaking.
 */
public class VehicleItem extends Item {
    private final Supplier<? extends EntityType<? extends SrVehicle>> type;

    public VehicleItem(Properties props, Supplier<? extends EntityType<? extends SrVehicle>> type) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        SrVehicle vehicle = type.get().create(level);
        if (vehicle == null) return InteractionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0;
        vehicle.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        // keep out of walls: the hull is much longer than its square hit box
        AABB hull = vehicle.getBoundingBox().inflate(0.0, -0.05, 0.0).move(0, 0.05, 0);
        if (!level.noCollision(vehicle, hull)) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.steelrain.no_room"), true);
            return InteractionResult.FAIL;
        }
        vehicle.setOwner(ctx.getPlayer() != null ? ctx.getPlayer().getUUID() : null);
        var packed = ctx.getItemInHand().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (packed != null) {
            vehicle.unpackState(packed.copyTag());             // as it was when it was packed up
        } else {
            vehicle.setEnergy(vehicle.batteryFe());            // new from the factory: charged, a quarter tank
            vehicle.setFuel(vehicle.tankLitres() / 4);
        }
        level.addFreshEntity(vehicle);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.steelrain.vehicle.tip"));
        var packed = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (packed != null) {
            var t = packed.copyTag();
            lines.add(Component.translatable("item.steelrain.vehicle.packed", Math.round(t.getFloat("Health")), Math.round(t.getFloat("Fuel")))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
