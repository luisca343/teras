package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

/**
 * Resolves a {@link DungeonGeoEnemy}'s assets from its variant, which arrives on the entity's
 * synced data. One model file serves every variant; only the texture and scale differ.
 */
@OnlyIn(Dist.CLIENT)
public class DungeonGeoEnemyModel extends GeoModel<DungeonGeoEnemy> {

    @Override
    public ResourceLocation getModelResource(DungeonGeoEnemy enemy) {
        return ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, enemy.variant().model());
    }

    @Override
    public ResourceLocation getTextureResource(DungeonGeoEnemy enemy) {
        return ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, enemy.variant().texture());
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonGeoEnemy enemy) {
        return ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, enemy.variant().animation());
    }
}
