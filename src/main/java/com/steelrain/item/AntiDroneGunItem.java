package com.steelrain.item;

import com.steelrain.ModRegistry;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.util.Teams;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Hand-held directional jammer: hold use while pointing at a drone to cut its radio link. */
public class AntiDroneGunItem extends Item {
    public static final int FULL = 20 * 120;
    private static final double RANGE = 80;
    private static final double CONE_COS = Math.cos(Math.toRadians(7));

    public AntiDroneGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrDefault(ModRegistry.BATTERY.get(), FULL) <= 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.steelrain.flat_battery"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(user instanceof Player player)) return;
        int charge = stack.getOrDefault(ModRegistry.BATTERY.get(), FULL);
        if (charge <= 0) {
            user.releaseUsingItem();
            return;
        }
        stack.set(ModRegistry.BATTERY.get(), charge - 1);
        if (remaining % 8 == 0) {
            sl.playSound(null, user.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6f, 2f);
        }
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        for (FlyingWeaponEntity e : sl.getEntitiesOfClass(FlyingWeaponEntity.class, new AABB(eye, eye).inflate(RANGE))) {
            Vec3 to = e.position().subtract(eye);
            double d = to.length();
            if (d > RANGE || d < 0.5 || to.dot(look) / d < CONE_COS) continue;
            if (Teams.friendly(sl, player.getUUID(), player.getGameProfile().getName(), e.ownerId(), e.ownerName())) continue;
            e.directJam(sl.getGameTime() + 12);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain.charge", stack.getOrDefault(ModRegistry.BATTERY.get(), FULL) / 20));
        tooltip.add(Component.translatable("tooltip.steelrain.anti_drone_gun"));
    }
}
