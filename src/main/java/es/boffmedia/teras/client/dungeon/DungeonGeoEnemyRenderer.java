package es.boffmedia.teras.client.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the animated dungeon enemy. Scale comes from the variant rather than the renderer, so one
 * registration covers a scout and a boss.
 */
@OnlyIn(Dist.CLIENT)
public class DungeonGeoEnemyRenderer extends GeoEntityRenderer<DungeonGeoEnemy> {

    public DungeonGeoEnemyRenderer(EntityRendererProvider.Context context) {
        super(context, new DungeonGeoEnemyModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public void preRender(PoseStack poseStack, DungeonGeoEnemy enemy,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        float scale = enemy.variant().scale();
        poseStack.scale(scale, scale, scale);
        this.shadowRadius = 0.5f * scale;
        super.preRender(poseStack, enemy, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
