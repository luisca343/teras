package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types. The dungeon's animated enemy is the only one so far. */
public final class EntityInit {
    private EntityInit() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Teras.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DungeonGeoEnemy>> DUNGEON_ENEMY =
            ENTITY_TYPES.register("dungeon_enemy", () -> EntityType.Builder
                    .of(DungeonGeoEnemy::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    // Dungeons are instanced far from anyone's base, so the entity has to stay
                    // loaded and tracked at the range a room is fought across.
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .fireImmune()
                    .build("dungeon_enemy"));

    /**
     * What dungeon enemies throw. One type with a kind on it: an archer's bolt and a webber's shot
     * differ in what they do on impact, not in how they fly.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<es.boffmedia.teras.dungeon.entity.DungeonBolt>>
            DUNGEON_BOLT = ENTITY_TYPES.register("dungeon_bolt", () -> EntityType.Builder
                    .<es.boffmedia.teras.dungeon.entity.DungeonBolt>of(
                            es.boffmedia.teras.dungeon.entity.DungeonBolt::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("dungeon_bolt"));
}
