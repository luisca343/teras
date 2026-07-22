package es.boffmedia.teras.client.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a variant's emissive sheet over its base one, at full brightness.
 *
 * <p>{@link RenderType#eyes} ignores block light, which is the whole point: Infestadas' enemies are
 * {@code CLIMBER}s, so they are on walls and ceilings, above the player's eyeline, in rooms lit at
 * whatever a cave is lit at. Before this they were a dark silhouette against dark stone and the
 * first thing a player knew about one was the damage. The sheets are almost entirely transparent —
 * eight eyes and, on the queen, her spinneret.</p>
 *
 * <p>It also carries the only telegraph the queen's ceiling web has. {@code ATTACK_TICKS} and the
 * action behind it are synched precisely because the client needs them to pick a clip, so the same
 * pair is already available here, and brightening while the strand is being spat costs nothing and
 * needs no particle. A variant with no {@code glowTexture} never reaches this layer at all.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DungeonGeoEnemyGlowLayer extends GeoRenderLayer<DungeonGeoEnemy> {

    public DungeonGeoEnemyGlowLayer(GeoRenderer<DungeonGeoEnemy> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, DungeonGeoEnemy enemy, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (!enemy.variant().glows()) {
            return;
        }
        ResourceLocation glow = ResourceLocation.fromNamespaceAndPath(
                Teras.MOD_ID, enemy.variant().glowTexture());
        RenderType eyes = RenderType.eyes(glow);
        // Full white while an action is running, dimmed to a steady ember otherwise — the queen's
        // eyes flare as she spits a strand, the weaver's before it shoots. A tint rather than a
        // second sheet: swapping textures mid-action would cost a lookup per frame to say something
        // one multiplier already says.
        int lit = enemy.isActing() ? 0xFFFFFFFF : 0xFFA6A6A6;
        getRenderer().reRender(getDefaultBakedModel(enemy), poseStack, bufferSource, enemy, eyes,
                bufferSource.getBuffer(eyes), partialTick, packedLight,
                OverlayTexture.NO_OVERLAY, lit);
    }
}
