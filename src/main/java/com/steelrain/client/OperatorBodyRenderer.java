package com.steelrain.client;

import com.steelrain.entity.OperatorBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** The stand-in body looks like the player who left it: their skin, armour and held items. */
public class OperatorBodyRenderer extends LivingEntityRenderer<OperatorBodyEntity, PlayerModel<OperatorBodyEntity>> {
    public OperatorBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(OperatorBodyEntity body) {
        UUID id = body.operator().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (id != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkin().texture();
        }
        return id != null ? DefaultPlayerSkin.get(id).texture() : DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected boolean shouldShowName(OperatorBodyEntity body) { return body.hasCustomName(); }
}
