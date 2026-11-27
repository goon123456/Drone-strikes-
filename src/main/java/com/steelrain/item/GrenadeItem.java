package com.steelrain.item;

import com.steelrain.entity.GrenadeEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A hand grenade: throw it with use. Fragmentation grenades burst; smoke grenades lay a screen. */
public class GrenadeItem extends Item {
    private final boolean smoke;

    public GrenadeItem(Properties properties, boolean smoke) {
        super(properties);
        this.smoke = smoke;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.7f);
        if (!level.isClientSide) {
            GrenadeEntity grenade = new GrenadeEntity(level, player, smoke);
            grenade.setPos(player.getEyePosition().add(player.getLookAngle().scale(0.4)));
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.1f, 0.6f);
            level.addFreshEntity(grenade);
        }
        player.getCooldowns().addCooldown(this, 20);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
