package com.steelrain.gun;

import com.steelrain.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A firearm. Everything about one particular gun lives on its stack: the rounds left in the fitted magazine (or
 * that none is fitted), whether a round is chambered, where the selector and the sights are set, and what is
 * fitted to it. The trigger, reloading and the bullets themselves are handled by {@link GunLogic}.
 */
public class GunItem extends Item {
    public final GunType type;

    public GunItem(GunType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    // ------------------------------------------------------------------ state on the stack

    /** Rounds in the fitted magazine (or tube); -1 when no magazine is fitted. */
    public static int rounds(ItemStack s) { return s.getOrDefault(ModRegistry.ROUNDS.get(), s.getItem() instanceof GunItem g && !g.type.magazineFed() ? 0 : -1); }
    public static boolean hasMagazine(ItemStack s) { return rounds(s) >= 0; }
    public static boolean chambered(ItemStack s) { return s.getOrDefault(ModRegistry.CHAMBER.get(), false); }
    public static int modeIndex(ItemStack s) { return s.getOrDefault(ModRegistry.FIRE_MODE.get(), 0); }

    public static int mode(ItemStack s) {
        GunType t = ((GunItem) s.getItem()).type;
        return t.modes[Math.floorMod(modeIndex(s), t.modes.length)];
    }

    public static List<Attachment> parts(ItemStack s) {
        List<Attachment> out = new ArrayList<>();
        for (int i : s.getOrDefault(ModRegistry.PARTS.get(), List.<Integer>of())) if (i >= 0 && i < Attachment.VALUES.length) out.add(Attachment.VALUES[i]);
        return out;
    }

    @Nullable
    public static Attachment part(ItemStack s, Attachment.Slot slot) {
        for (Attachment a : parts(s)) if (a.slot == slot) return a;
        return null;
    }

    public static void setParts(ItemStack s, List<Attachment> parts) {
        s.set(ModRegistry.PARTS.get(), parts.stream().map(Enum::ordinal).toList());
    }

    public static boolean suppressed(ItemStack s) { return part(s, Attachment.Slot.MUZZLE) != null; }

    /** The ranges the sight in use can be set to: the optic's own if it has any, else the iron sights'. */
    public static int[] zeros(ItemStack s) {
        Attachment optic = part(s, Attachment.Slot.OPTIC);
        return optic != null && optic.zeros != null ? optic.zeros : ((GunItem) s.getItem()).type.zeros;
    }

    public static int zeroRange(ItemStack s) {
        int[] z = zeros(s);
        return z[Math.max(0, Math.min(z.length - 1, s.getOrDefault(ModRegistry.ZERO.get(), 0)))];
    }

    /** What is loaded: the gun's own cartridge, or for the launcher whichever rocket was pushed in. */
    public static Caliber caliber(ItemStack s) {
        Caliber[] loads = ((GunItem) s.getItem()).type.loads();
        return loads[Math.max(0, Math.min(loads.length - 1, s.getOrDefault(ModRegistry.LOAD.get(), 0)))];
    }

    /** Rounds ready to fire, counting the one in the chamber. */
    public static int loaded(ItemStack s) { return Math.max(0, rounds(s)) + (chambered(s) ? 1 : 0); }

    /** A gun as it comes from the creative tab: loaded, with a full magazine. */
    public ItemStack loadedStack() {
        ItemStack s = new ItemStack(this);
        s.set(ModRegistry.ROUNDS.get(), type.capacity);
        if (!type.firesFromFeed()) s.set(ModRegistry.CHAMBER.get(), true);
        s.set(ModRegistry.FIRE_MODE.get(), 1);
        return s;
    }

    // ------------------------------------------------------------------ item behaviour

    /** A gun is not a club: left click is the trigger, so it never digs or swings. */
    @Override public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) { return false; }
    @Override public boolean onEntitySwing(ItemStack stack, LivingEntity entity) { return true; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) { return true; }

    /** The stack changes with every shot; that must not make the gun dip as if it had just been drawn. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Caliber c = caliber(stack);
        tooltip.add(Component.translatable("tooltip.steelrain.gun.calibre", c.label).withStyle(ChatFormatting.GRAY));
        String load = type.magazineFed() && !hasMagazine(stack) ? "-" : rounds(stack) + (type.firesFromFeed() ? "" : chambered(stack) ? "+1" : "+0");
        tooltip.add(Component.translatable("tooltip.steelrain.gun.loaded", load, type.capacity).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.steelrain.gun.mode", Component.translatable("gun.steelrain.mode." + GunType.Mode.KEY[mode(stack)]))
                .withStyle(ChatFormatting.GRAY));
        for (Attachment a : parts(stack)) {
            tooltip.add(Component.literal("+ ").append(Component.translatable("item.steelrain." + a.id)).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (flag.hasShiftDown()) {
            double v0 = c.metresPerSecond * type.velocityFactor;
            tooltip.add(Component.translatable("tooltip.steelrain.gun.velocity", Math.round(v0)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.steelrain.gun.rate", type.rpm).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.steelrain.gun.accuracy", String.format("%.1f", type.moa)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.steelrain.gun.weight", String.format("%.1f", type.weightKg)).withStyle(ChatFormatting.DARK_GRAY));
            double[] far = c.at(300, type.velocityFactor);
            tooltip.add(Component.translatable("tooltip.steelrain.gun.at300", Math.round(far[0]), String.format("%.2f", far[1])).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.steelrain.gun.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
