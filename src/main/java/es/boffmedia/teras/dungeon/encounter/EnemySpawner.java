package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a room's spawn markers plus the stage's {@link SpawnTables} into standing enemies.
 * Composition is drawn from a floor-derived seed (a seed-run fights the same waves); which
 * individual attacks land is the game's business, not the generator's.
 *
 * <p>Mobs spawn persistent (a sealed room must never win itself by despawn timer) and boss/mini
 * boss rooms draw one pick from their pool at the {@code boss} marker. Every failure — unknown
 * entity id, missing clone, missing CustomNPCs — degrades the wave and logs; a 0-enemy wave is
 * the {@code RunCore}'s instant-clear case, never a sealed dead end.</p>
 */
public final class EnemySpawner {
    private EnemySpawner() {}

    /**
     * Scoreboard tag marking a mob as this system's. It is applied <b>before</b> the entity joins
     * the level, because that is when {@code InstanceGuard} has to tell our wave apart from a
     * replacement spawn — see the Pixelmon note there.
     */
    public static final String DUNGEON_TAG = "teras_dungeon_enemy";

    /** Spawns the room's encounter; the returned entities are the room's kill ledger. */
    public static List<Entity> spawn(ServerLevel level, BuiltDungeon built, Room room, int roomIndex) {
        SeededRng rng = new SeededRng(DungeonSeeds.derive(built.layout().baseSeed(), 0x656E656DL + roomIndex));
        List<Entity> spawned = switch (room.type()) {
            case BOSS -> spawnFromPool(level, built, room,
                    SpawnTables.bossPool(built.layout().stage()), "boss", rng);
            case MINI_BOSS -> spawnFromPool(level, built, room,
                    SpawnTables.miniBossPool(built.layout().stage()), "boss", rng);
            default -> spawnWave(level, built, room, rng);
        };
        if (spawned.isEmpty()) {
            Teras.LOGGER.warn("Dungeons: {} spawned no enemies — the room clears itself on entry. "
                    + "Check enemies.json ids for stage {}.", room, built.layout().stage());
        } else {
            Teras.LOGGER.debug("Dungeons: {} spawned {} enemies", room, spawned.size());
        }
        warnIfPeaceful(level, spawned);
        return spawned;
    }

    /**
     * Vanilla monsters are removed on their first tick when the difficulty is PEACEFUL, so a room
     * seals, loses its whole wave to the despawn rule and re-opens about a second later — which
     * reads in-game as "the doors never closed". CustomNPCs enemies are unaffected, so this is a
     * warning about the vanilla fallback table, not a hard requirement.
     */
    private static void warnIfPeaceful(ServerLevel level, List<Entity> spawned) {
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            return;
        }
        boolean anyMonster = spawned.stream().anyMatch(e -> e instanceof Monster);
        if (anyMonster) {
            Teras.LOGGER.warn("Dungeons: difficulty is PEACEFUL — vanilla monsters will vanish "
                    + "immediately and rooms will clear themselves. Raise the difficulty or use "
                    + "CustomNPCs enemies in enemies.json.");
        }
    }

    private static List<Entity> spawnWave(ServerLevel level, BuiltDungeon built, Room room, SeededRng rng) {
        SpawnTables.StageTable table = SpawnTables.stageTable(built.layout().stage());
        List<BlockPos> positions = spawnPositions(built, room, "spawn");
        int count = rng.between(table.countMin(), table.countMax());
        if (room.shape().cellCount() > 1) {
            count = count * room.shape().cellCount() / 2 + 1;
        }
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos pos = positions.get(i % positions.size());
            Entity enemy = spawnOne(level, SpawnTables.pickWeighted(table.wave(), rng), pos);
            if (enemy != null) {
                spawned.add(enemy);
            }
        }
        return spawned;
    }

    private static List<Entity> spawnFromPool(ServerLevel level, BuiltDungeon built, Room room,
                                              List<SpawnTables.SpawnEntry> pool, String markerKind,
                                              SeededRng rng) {
        List<BlockPos> positions = spawnPositions(built, room, markerKind);
        Entity boss = spawnOne(level, SpawnTables.pickWeighted(pool, rng), positions.get(0));
        return boss == null ? List.of() : List.of(boss);
    }

    private static Entity spawnOne(ServerLevel level, SpawnTables.SpawnEntry entry, BlockPos pos) {
        return switch (entry.kind()) {
            case ENTITY -> spawnEntity(level, entry.id(), pos);
            case CNPC -> spawnClone(level, entry, pos);
        };
    }

    private static Entity spawnEntity(ServerLevel level, String id, BlockPos pos) {
        EntityType<?> type = EntityType.byString(id).orElse(null);
        if (type == null) {
            Teras.LOGGER.warn("Dungeons: unknown entity id '{}' in enemies.json", id);
            return null;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            Teras.LOGGER.warn("Dungeons: entity type '{}' refused to instantiate", id);
            return null;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        entity.addTag(DUNGEON_TAG);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            // Without this a wave can be despawned out from under a sealed room, which then opens
            // itself with nothing killed. It does not survive PEACEFUL — see warnIfPeaceful.
            mob.setPersistenceRequired();
        }
        // The return value is the whole point: an entity that never joined must not enter the kill
        // ledger, or its room stays sealed over an enemy that does not exist.
        if (!level.addFreshEntity(entity)) {
            Teras.LOGGER.warn("Dungeons: level refused entity '{}' at {}", id, pos);
            return null;
        }
        return entity;
    }

    private static Entity spawnClone(ServerLevel level, SpawnTables.SpawnEntry entry, BlockPos pos) {
        if (!net.neoforged.fml.ModList.get().isLoaded("customnpcs")) {
            Teras.LOGGER.warn("Dungeons: enemies.json references CNPC clone '{}' but CustomNPCs is absent",
                    entry.id());
            return null;
        }
        Entity clone = CnpcBridge.spawnClone(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                entry.tab(), entry.id());
        if (clone != null) {
            // Tagged after the fact: the CustomNPCs API adds the entity itself, so we never see it
            // before it joins. NPC entities pass the guard on their namespace either way; the tag
            // is what covers a clone of a plain vanilla mob.
            clone.addTag(DUNGEON_TAG);
        }
        return clone;
    }

    /** Marker positions of {@code kind}; cell centers when the template shipped without markers. */
    private static List<BlockPos> spawnPositions(BuiltDungeon built, Room room, String kind) {
        List<BlockPos> positions = new ArrayList<>();
        for (TemplateMarkers.Marker marker : built.markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals(kind)) {
                positions.add(marker.pos());
            }
        }
        if (positions.isEmpty()) {
            for (GridPos cell : room.cells()) {
                positions.add(built.cellOrigin(cell).offset(built.roomSize() / 2, 1, built.roomSize() / 2));
            }
        }
        return positions;
    }
}
