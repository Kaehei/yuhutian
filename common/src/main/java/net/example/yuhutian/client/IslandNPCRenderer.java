package net.example.yuhutian.client;

import net.example.yuhutian.YuhutianMod;
import net.example.yuhutian.entity.IslandNPCEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 空岛 NPC 实体渲染器。
 * <p>
 * 使用标准的玩家模型（Steve 皮肤）进行渲染。
 * </p>
 */
public class IslandNPCRenderer extends HumanoidMobRenderer<IslandNPCEntity, HumanoidModel<IslandNPCEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public IslandNPCRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(IslandNPCEntity entity) {
        return TEXTURE;
    }
}
