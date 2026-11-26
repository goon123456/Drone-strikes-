package com.steelrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelrain.ModRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Only loaded when Curios is installed: shows backpacks on the wearer's back. */
public final class CuriosClient {
    static void register() {
        CuriosRendererRegistry.register(ModRegistry.FIBER_BACKPACK.get(), () -> new Backpack(Models.FIBER_PACK));
        CuriosRendererRegistry.register(ModRegistry.JAMMER_BACKPACK.get(), () -> new Backpack(Models.JAMMER_PACK));
    }

    private record Backpack(BoxModel model) implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing,
                float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            pose.pushPose();
            // Player model space has Y pointing down and the back towards +Z.
            pose.translate(0, slotContext.entity().isCrouching() ? 0.55 : 0.35, slotContext.entity().isCrouching() ? 0.28 : 0.13);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture())), light);
            pose.popPose();
        }
    }

    private CuriosClient() {}
}
