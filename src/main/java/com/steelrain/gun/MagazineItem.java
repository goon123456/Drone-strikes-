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
        Item ammo = ModRegistry.ammo(gun.caliber, stack.getOrDefault(ModRegistry.LOAD.get(), 0));
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
        if (rounds(stack) >= gun.capacity || (!player.getAbilities().instabuild && feedSlot(player, stack) < 0)) {
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
            int slot = feedSlot(player, stack);
            if (slot < 0) { user.releaseUsingItem(); return; }
            // an empty magazine takes on the kind of the first round pressed into it
            if (n == 0) stack.set(ModRegistry.LOAD.get(), ModRegistry.kindOf(gun.caliber, player.getInventory().getItem(slot)));
            player.getInventory().getItem(slot).shrink(1);
        }
        stack.set(ModRegistry.ROUNDS.get(), n + 1);
        level.playSound(null, player.blockPosition(), GunLogic.sound("gun_round_in"), SoundSource.PLAYERS, 0.5f, 0.95f + level.random.nextFloat() * 0.1f);
    }

    /** The first slot holding rounds this magazine can take: of its own kind once it has any in it, any kind if empty. */
    private int feedSlot(Player player, ItemStack stack) {
        int kind = rounds(stack) > 0 ? stack.getOrDefault(ModRegistry.LOAD.get(), 0) : -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            int k = ModRegistry.kindOf(gun.caliber, player.getInventory().getItem(i));
            if (k >= 0 && (kind < 0 || k == kind)) return i;
        }
        return -1;
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
        int kind = stack.getOrDefault(ModRegistry.LOAD.get(), 0);
        if (kind != 0 && rounds(stack) > 0) tooltip.add(Component.translatable("gun.steelrain.kind." + GunItem.KIND_KEY[Math.min(2, kind)]).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.fits", Component.translatable("item.steelrain." + gun.id)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
