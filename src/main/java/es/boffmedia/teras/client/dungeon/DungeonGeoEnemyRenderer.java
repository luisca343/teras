package es.boffmedia.teras.client.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Draws the animated dungeon enemy. Scale comes from the variant rather than the renderer, so one
 * registration covers a scout and a boss.
 */
@OnlyIn(Dist.CLIENT)
public class DungeonGeoEnemyRenderer extends GeoEntityRenderer<DungeonGeoEnemy> {

    public DungeonGeoEnemyRenderer(EntityRendererProvider.Context context) {
        super(context, new DungeonGeoEnemyModel());
        this.shadowRadius = 0.5f;
        // Registered unconditionally; the layer itself skips variants with no emissive sheet. One
        // registration covers the whole bestiary, the same way scale does.
        addRenderLayer(new DungeonGeoEnemyGlowLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, DungeonGeoEnemy enemy,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        // Only on the first pass. Every render layer re-enters this method through
        // GeoRenderer.reRender, which pushes the pose and calls preRender again with isReRender
        // true — so scaling unconditionally squares the variant's scale on every layer.
        //
        // That is what put the queen's eyes above and in front of her: at 2.2 her glow drew at
        // 4.84x, and because the scale is about the entity's feet, more than twice the size is also
        // a long way up and forward. It was invisible on the other two for the least helpful
        // reasons available — the tejedora is scale 1.0, where squaring changes nothing, and the
        // cria is 0.7, where the glow shrinks inside her own head.
        if (!isReRender) {
            float scale = enemy.variant().scale();
            poseStack.scale(scale, scale, scale);
            this.shadowRadius = 0.5f * scale;
        }
        super.preRender(poseStack, enemy, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public Color getRenderColor(DungeonGeoEnemy enemy, float partialTick, int packedLight) {
        if (!enemy.isEnraged()) {
            return super.getRenderColor(enemy, partialTick, packedLight);
        }
        // Red stays full while green and blue pulse down, so the model reddens and breathes rather
        // than going dark. ~1.5s period off the entity's own tick, so every enraged enemy is in step.
        float phase = (enemy.tickCount + partialTick) * 0.3f;
        float dip = 0.35f + 0.25f * (float) Math.sin(phase);
        return Color.ofRGB(1.0f, dip, dip);
    }
}
