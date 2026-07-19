package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.DungeonMaterializer;
import es.boffmedia.teras.dungeon.encounter.EnemySpawner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps foreign creatures out of dungeon floors. A dungeon room's population is supposed to be
 * exactly the wave the run spawned; the first live test instead filled every room with wild
 * Pokémon, because Pixelmon's spawner treats the dungeon dimension like any other world.
 *
 * <p>Cancelling at {@link EntityJoinLevelEvent} rather than at a spawn-rules event is deliberate:
 * it is engine-agnostic. Pixelmon, vanilla natural spawning, spawners and anything else all have
 * to join the level, and none of them can join a floor. Only mobs are refused — item drops,
 * projectiles, XP orbs and players are untouched.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class InstanceGuard {
    private InstanceGuard() {}

    /** Vertical slack around a floor, so a mob spawning just above the roof is still refused. */
    private static final int Y_MARGIN = 16;

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (isAllowed(mob) || !isInsideFloor(event.getLevel(), mob)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Restores a dungeon wave that another mod refused. <b>Pixelmon's {@code MobSpawnReplacement}
     * listens on this same event and swaps vanilla monsters for Pokémon</b> — which is why the
     * first playtest saw a Pokémon appear in each room and the doors never close: every zombie the
     * run spawned was cancelled and replaced, the room's wave came out empty, and an empty room
     * clears itself. Our own enemies are not a spawn to be replaced, so this has the last word.
     *
     * <p>Runs at {@link EventPriority#LOWEST} with {@code receiveCanceled}, because the level reads
     * {@code isCanceled()} only after every listener has had its say.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityJoinLast(EntityJoinLevelEvent event) {
        if (!event.isCanceled() || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && mob.getTags().contains(EnemySpawner.DUNGEON_TAG)) {
            event.setCanceled(false);
        }
    }

    /**
     * What may stand in a dungeon besides the run's own wave. The exemptions matter as much as the
     * rule: a player's sent-out Pokémon is owned, and a scripted boss's summoned adds are
     * CustomNPCs entities — cancelling either would break the dungeon rather than clean it up.
     * What is left, and refused, is ambient natural spawning and replacement spawns.
     */
    private static boolean isAllowed(Mob mob) {
        return mob.getTags().contains(EnemySpawner.DUNGEON_TAG)
                || mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null
                || isCustomNpc(mob);
    }

    /** Checked by registry namespace so this class stays free of {@code noppes} imports. */
    private static boolean isCustomNpc(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id != null && id.getNamespace().equals("customnpcs");
    }

    /** True when {@code entity} is standing in any floor currently built in {@code level}. */
    public static boolean isInsideFloor(Level level, Entity entity) {
        Vec3 pos = entity.position();
        for (BuiltDungeon built : DungeonMaterializer.built()) {
            if (!built.dimension().equals(level.dimension())) {
                continue;
            }
            if (floorBounds(built).isInside(net.minecraft.core.BlockPos.containing(pos))) {
                return true;
            }
        }
        return false;
    }

    private static BoundingBox floorBounds(BuiltDungeon built) {
        int span = built.layout().grid().size() * built.roomSize();
        return new BoundingBox(
                built.origin().getX(), built.origin().getY() - Y_MARGIN, built.origin().getZ(),
                built.origin().getX() + span, built.origin().getY() + built.roomHeight() + Y_MARGIN,
                built.origin().getZ() + span);
    }
}
