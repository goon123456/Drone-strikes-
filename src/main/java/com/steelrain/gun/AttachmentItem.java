package com.steelrain.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A sight, suppressor or grip, fitted to a gun from the gun's fitting screen. */
public class AttachmentItem extends Item {
    public final Attachment attachment;

    public AttachmentItem(Attachment attachment, Properties properties) {
        super(properties);
        this.attachment = attachment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.steelrain." + attachment.id).withStyle(ChatFormatting.GRAY));
        StringBuilder fits = new StringBuilder();
        for (GunType t : GunType.VALUES) if (t.fits.contains(attachment)) fits.append(fits.isEmpty() ? "" : ", ").append(Component.translatable("item.steelrain." + t.id).getString());
        tooltip.add(Component.translatable("tooltip.steelrain.magazine.fits", fits.toString()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.steelrain.attachment.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
