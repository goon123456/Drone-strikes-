package com.steelrain.gun;

import com.steelrain.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

import java.util.List;

/**
 * A detachable magazine (or belt box) for one gun. It remembers how many rounds are in it, so a half-used magazine
 * taken off a gun goes back in the pack half full. Hold use to thumb loose rounds into it one at a time; sneak and
 * use to strip it.
 */
public class MagazineItem extends Item {
    public final GunType gun;

    public MagazineItem(GunType gun, Properties properties) {
        super(properties);
        this.gun = gun;
    }

    public static int rounds(ItemStack s) { return s.getOrDefault(ModRegistry.ROUNDS.get(), 0); }

    public ItemStack filled(int rounds) {
        ItemStack s = new ItemStack(this);
        s.set(ModRegistry.ROUNDS.get(), rounds);
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item ammo = ModRegistry.AMMO.get(gun.caliber).get();
        if (player.isShiftKeyDown()) {
            int n = rounds(stack);
            if (n <= 0) return InteractionResultHolder.pass(stack);
            if (!level.isClientSide) {
                stack.set(ModRegistry.ROUNDS.get(), 0);
                while (n > 0) {
                    ItemStack out = new ItemStack(ammo, Math.min(n, ammo.getDefaultMaxStackSize()));
                    n -= out.getCount();
                    if (!player.getInventory().add(out)) player.drop(out, false);
                }
                level.playSound(null, player.blockPosition(), GunLogic.sound("gun_mag_out"), SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (rounds(stack) >= gun.capacity || (!player.getAbilities().instabuild && !player.getInventory().contains(new ItemStack(ammo)))) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide || !(user instanceof Player player) || remaining % 3 != 0) return;
        int n = rounds(stack);
        if (n >= gun.capacity) { user.releaseUsingItem(); return; }
        if (!player.getAbilities().instabuild) {
            Item ammo = ModRegistry.AMMO.get(gun.caliber).get();
            int slot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(ammo)) { slot = i; break; }
            if (slot < 0) { user.releaseUsingItem(); return; }
            player.getInventory().getItem(slot).shrink(1);
        }
        stack.set(ModRegistry.ROUNDS.get(), n + 1);
        level.playSound(null, player.blockPosition(), GunLogic.sound("gun_round_in"), SoundSource.PLAYERS, 0.5f, 0.95f + level.random.nextFloat() * 0.1f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * rounds(stack) / gun.capacity); }
    @Override public int getBarColor(ItemStack stack) { return rounds(stack) * 4 < gun.capacity ? 0xE5534B : 0xC8B45A; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.rounds", rounds(stack), gun.capacity, gun.caliber.label).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.fits", Component.translatable("item.steelrain." + gun.id)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
