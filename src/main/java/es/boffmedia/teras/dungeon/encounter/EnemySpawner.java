package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.ability.Abilities;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import es.boffmedia.teras.dungeon.entity.GeoEnemyVariant;
import es.boffmedia.teras.init.EntityInit;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;
import es.boffmedia.teras.dungeon.run.CoinDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

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
    public static List<Entity> spawn(ServerLevel level, BuiltDungeon built, Room room, int roomIndex,
                                     int partySize) {
        return spawn(level, built, room, roomIndex, 1.0f, partySize, WaveModifier.NONE);
    }

    /**
     * What an affliction does to a wave, as one value the spawner can be handed.
     *
     * <p>A record rather than two loose doubles because the two halves only make sense together:
     * Enjambre is "more of them, each frailer", and shipping the count without the discount would
     * be a straight difficulty increase — which the affliction catalog's own rule forbids.</p>
     */
    public record WaveModifier(double count, double stats) {
        public static final WaveModifier NONE = new WaveModifier(1.0, 1.0);
    }

    public static List<Entity> spawn(ServerLevel level, BuiltDungeon built, Room room, int roomIndex,
                                     float sizeFactor, int partySize) {
        return spawn(level, built, room, roomIndex, sizeFactor, partySize, WaveModifier.NONE);
    }

    /**
     * @param sizeFactor multiplies the wave's enemy count — how a challenge room's later waves get
     *                   heavier. Bosses ignore it: a pool draws exactly one.
     * @param partySize  how many players the floor is being fought by; see {@code PartyScaling}.
     *                   Bosses take its health share but never its count — a boss room draws one
     *                   boss whatever the party.
     */
    public static List<Entity> spawn(ServerLevel level, BuiltDungeon built, Room room, int roomIndex,
                                     float sizeFactor, int partySize, WaveModifier modifier) {
        // A challenge's later waves spawn into a room that still holds the previous one's corpses
        // and any stragglers; purging is what keeps the ledger and the floor in agreement.
        purgeLeftovers(level, built, room);
        SeededRng rng = new SeededRng(DungeonSeeds.derive(built.layout().baseSeed(), 0x656E656DL + roomIndex));
        List<Entity> spawned = switch (room.type()) {
            case BOSS -> spawnFromPool(level, built, room, bossPool(built), "boss", rng,
                    CoinDrops.TIER_BOSS_TAG, partySize);
            case MINI_BOSS -> spawnFromPool(level, built, room, miniBossPool(built), "boss", rng,
                    CoinDrops.TIER_MINIBOSS_TAG, partySize);
            default -> spawnWave(level, built, room, rng, sizeFactor, partySize, modifier);
        };
        if (spawned.isEmpty()) {
            Teras.LOGGER.warn("Dungeons: {} spawned no enemies — the room clears itself on entry. "
                    + "Check the piso's enemigos table (or enemies.json) for stage {}.",
                    room, built.layout().floor());
        } else {
            Teras.LOGGER.debug("Dungeons: {} spawned {} enemies", room, spawned.size());
        }
        warnIfPeaceful(level, spawned);
        aggro(level, spawned);
        // Deliberately after the return list is fixed: atmosphere is spawned into the room but is
        // not part of it. A bat inside the kill ledger seals a room until the party has hunted down
        // every one, which is exactly the fight nobody wants to have.
        spawnAmbient(level, built, room, rng);
        return spawned;
    }

    /**
     * The boss pool this floor draws from: the plan's, which already resolved the piso's override
     * against its tramo's default. {@code enemies.json} is the fallback for a floor built without a
     * plan and for configs written before pisos owned their pools.
     *
     * <p>Until this read the plan, {@code jefes} and {@code minijefes} were config nothing
     * consumed — every floor drew from the global stage pool, so the piso override that keeps the
     * spider queen out of plain Cuevas had no effect at all.</p>
     */
    private static List<SpawnTables.SpawnEntry> bossPool(BuiltDungeon built) {
        return poolOr(built.plan() == null ? List.of() : built.plan().jefes(),
                SpawnTables.bossPool(built.layout().floor()));
    }

    private static List<SpawnTables.SpawnEntry> miniBossPool(BuiltDungeon built) {
        return poolOr(built.plan() == null ? List.of() : built.plan().minijefes(),
                SpawnTables.miniBossPool(built.layout().floor()));
    }

    private static List<SpawnTables.SpawnEntry> poolOr(List<String> ids,
                                                       List<SpawnTables.SpawnEntry> fallback) {
        if (ids == null || ids.isEmpty()) {
            return fallback;
        }
        List<SpawnTables.SpawnEntry> pool = new ArrayList<>(ids.size());
        for (String id : ids) {
            pool.add(entryOf(es.boffmedia.teras.dungeon.piso.SpawnRef.of(id, 1)));
        }
        return pool;
    }

    /**
     * A tagged enemy already standing in the room at spawn time is a leftover — a deserted fight
     * whose wave unloaded before it could be discarded, thawed back in when the player returned.
     * The fresh wave replaces it; without this the rematch stacks both wave instances, and the
     * leftovers fight outside the new kill ledger.
     */
    private static void purgeLeftovers(ServerLevel level, BuiltDungeon built, Room room) {
        for (GridPos cell : room.cells()) {
            BlockPos origin = built.cellOrigin(cell);
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + built.roomSize(), origin.getY() + built.roomHeight(),
                    origin.getZ() + built.roomSize());
            for (Entity leftover : level.getEntities((Entity) null, box,
                    e -> e.getTags().contains(DUNGEON_TAG))) {
                leftover.discard();
            }
        }
    }

    /**
     * Points the wave at the nearest player. A sealed room is not an ambush to be discovered: the
     * doors shut and the fight starts. It also means combat does not depend on getting a
     * CustomNPCs faction's player attitude right — an NPC handed a target fights whatever its
     * faction says.
     */
    private static void aggro(ServerLevel level, List<Entity> spawned) {
        for (Entity entity : spawned) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            Player nearest = level.getNearestPlayer(mob, 64);
            if (nearest instanceof ServerPlayer player) {
                mob.setTarget(player);
            }
        }
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

    /**
     * The room's fighting wave, drawn from the piso's own roster and scaled by its tramo's depth.
     *
     * <p>Shooters are placed apart from the rest. A room authors two kinds of position — floor
     * {@code spawn} markers and {@code spawn:ranged} ledges — and putting an archer on the floor
     * wastes the shelf the builder made for it while putting a melee enemy on a shelf gives the
     * party a free one. When a room has no perches the shooters simply join the floor, so a piso
     * with archers is never broken by a room without ledges.</p>
     */
    private static List<Entity> spawnWave(ServerLevel level, BuiltDungeon built, Room room,
                                          SeededRng rng, float sizeFactor, int partySize,
                                          WaveModifier modifier) {
        double dificultad = built.dificultad();
        es.boffmedia.teras.dungeon.piso.EnemyTable table = tableOf(built);
        List<es.boffmedia.teras.dungeon.piso.SpawnRef> roster = table.rosterAt(dificultad);
        if (roster.isEmpty()) {
            return spawnLegacyWave(level, built, room, rng, sizeFactor, partySize);
        }
        List<BlockPos> floorPositions = spawnPositions(built, room, "spawn");
        List<BlockPos> perches = markerPositions(built, room, "spawn:ranged");

        int count = rng.between(table.countMinAt(dificultad), table.countMaxAt(dificultad));
        if (room.shape().cellCount() > 1) {
            count = count * room.shape().cellCount() / 2 + 1;
        }
        // The party is the second axis, kept off damage entirely: joining a friend must never make
        // the floor hit harder. Count carries it, health moves a little, and PartyScaling caps the
        // product so a wave still fits the markers the room was authored with.
        count = Math.max(1, Math.round(count * sizeFactor
                * (float) es.boffmedia.teras.dungeon.piso.PartyScaling.count(partySize)
                * (float) modifier.count()));

        List<Entity> spawned = new ArrayList<>();
        int floorIndex = 0;
        int perchIndex = 0;
        for (int i = 0; i < count; i++) {
            es.boffmedia.teras.dungeon.piso.SpawnRef ref =
                    pickRef(roster, rng);
            boolean toPerch = shoots(ref) && !perches.isEmpty();
            BlockPos pos = toPerch
                    ? perches.get(perchIndex++ % perches.size())
                    : floorPositions.get(floorIndex++ % floorPositions.size());
            Entity enemy = spawnOne(level, entryOf(ref), built.clampInside(room, pos, 1));
            if (enemy == null) {
                continue;
            }
            EnemyScaling.apply(enemy,
                    ref.vida() * es.boffmedia.teras.dungeon.piso.Dificultad.health(dificultad)
                            * es.boffmedia.teras.dungeon.piso.PartyScaling.health(partySize)
                            * modifier.stats(),
                    ref.dano() * es.boffmedia.teras.dungeon.piso.Dificultad.damage(dificultad)
                            * modifier.stats(),
                    ref.escala());
            spawned.add(enemy);
        }
        return spawned;
    }

    /**
     * Whether this enemy wants a ledge. Read off the bestiary rather than declared per table entry:
     * "shoots" is a property of the enemy, and a table that restated it would eventually disagree
     * with the definition. Only first-party variants can answer, so a vanilla skeleton stays on the
     * floor — a perch it cannot be trusted to path down from is worse than no perch.
     */
    private static boolean shoots(es.boffmedia.teras.dungeon.piso.SpawnRef ref) {
        if (ref.kind() != null && !ref.kind().equalsIgnoreCase("geo")) {
            return false;
        }
        return GeoEnemyVariant.exists(ref.id()) && GeoEnemyVariant.of(ref.id()).shoots();
    }

    /** The piso's table, or the empty one when a floor was built without a plan. */
    private static es.boffmedia.teras.dungeon.piso.EnemyTable tableOf(BuiltDungeon built) {
        if (built.plan() == null || built.plan().piso() == null) {
            return es.boffmedia.teras.dungeon.piso.EnemyTable.EMPTY;
        }
        return built.plan().piso().enemigos();
    }

    private static es.boffmedia.teras.dungeon.piso.SpawnRef pickRef(
            List<es.boffmedia.teras.dungeon.piso.SpawnRef> roster, SeededRng rng) {
        int total = 0;
        for (es.boffmedia.teras.dungeon.piso.SpawnRef ref : roster) {
            total += ref.peso();
        }
        int roll = rng.between(1, Math.max(1, total));
        for (es.boffmedia.teras.dungeon.piso.SpawnRef ref : roster) {
            roll -= ref.peso();
            if (roll <= 0) {
                return ref;
            }
        }
        return roster.get(roster.size() - 1);
    }

    /**
     * One table line as something the spawn path understands. A ref with no {@code kind} names the
     * first-party bestiary, which is why a piso's table reads as a list of enemy names rather than
     * of registry ids.
     */
    private static SpawnTables.SpawnEntry entryOf(es.boffmedia.teras.dungeon.piso.SpawnRef ref) {
        String kind = ref.kind() == null ? "geo" : ref.kind().toLowerCase(java.util.Locale.ROOT);
        SpawnTables.Kind resolved = switch (kind) {
            case "entity" -> SpawnTables.Kind.ENTITY;
            case "cnpc" -> SpawnTables.Kind.CNPC;
            default -> SpawnTables.Kind.GEO;
        };
        // Renamed ids are healed here rather than downstream: the entry's id is what names the boss
        // bar, keys the ability table and reaches the entity, so an id fixed only at the renderer
        // would still fight without its abilities under its old name.
        String id = resolved == SpawnTables.Kind.GEO
                ? GeoEnemyVariant.current(ref.id()) : ref.id();
        if (resolved == SpawnTables.Kind.GEO && !GeoEnemyVariant.exists(id)) {
            // GeoEnemyVariant.of falls back rather than failing, so without this the wrong enemy
            // appears and the table looks like it worked.
            Teras.LOGGER.warn("Dungeons: '{}' is not a known enemy variant — spawning the fallback. "
                    + "Check the piso's enemigos table.", id);
        }
        return new SpawnTables.SpawnEntry(resolved, id, ref.tab(), ref.peso(), ref.elite());
    }

    /**
     * Atmosphere: spawned on room entry, outside the kill ledger, swept with the floor like
     * everything else this system places. They carry {@link #DUNGEON_TAG} so {@code InstanceGuard}
     * lets them stand and the floor sweep takes them — the tag is about ownership, not about being
     * an objective.
     */
    private static void spawnAmbient(ServerLevel level, BuiltDungeon built, Room room,
                                     SeededRng rng) {
        es.boffmedia.teras.dungeon.piso.EnemyTable table = tableOf(built);
        if (table.ambientales().isEmpty()) {
            return;
        }
        List<BlockPos> positions = markerPositions(built, room, "ambiente");
        if (positions.isEmpty()) {
            positions = spawnPositions(built, room, "spawn");
        }
        int index = 0;
        for (es.boffmedia.teras.dungeon.piso.EnemyTable.AmbientRef ref : table.ambientales()) {
            for (int i = 0; i < ref.cantidad(); i++) {
                BlockPos pos = positions.get(index++ % positions.size());
                Entity ambient = spawnOne(level,
                        entryOf(new es.boffmedia.teras.dungeon.piso.SpawnRef(
                                ref.kind() == null ? "entity" : ref.kind(), ref.id(), ref.tab(), 1,
                                false, 1.0, 1.0, 1.0)),
                        built.clampInside(room, pos, 1));
                if (ambient instanceof Mob mob) {
                    // Never handed a target: the moment atmosphere fights back it is an encounter
                    // the ledger does not know about, and the room can be cleared with it alive.
                    mob.setTarget(null);
                }
            }
        }
    }

    /**
     * The pre-piso path: global stage tables from {@code enemies.json}. Kept for a piso that
     * declares no roster of its own, so an existing server keeps fighting exactly what it did
     * before its pisos gained tables.
     */
    private static List<Entity> spawnLegacyWave(ServerLevel level, BuiltDungeon built, Room room,
                                                SeededRng rng, float sizeFactor, int partySize) {
        SpawnTables.StageTable table = SpawnTables.stageTable(built.layout().floor());
        List<BlockPos> positions = spawnPositions(built, room, "spawn");
        int count = rng.between(table.countMin(), table.countMax());
        if (room.shape().cellCount() > 1) {
            count = count * room.shape().cellCount() / 2 + 1;
        }
        count = Math.max(1, Math.round(count * sizeFactor * (float) es.boffmedia.teras.dungeon.piso.PartyScaling.count(partySize)));
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

    /**
     * {@code tierTag} is what makes a boss worth more than the adds it summons: the coin payout
     * reads it off the entity, so a wave spawned mid-fight by an ability pays the ordinary rate.
     */
    private static List<Entity> spawnFromPool(ServerLevel level, BuiltDungeon built, Room room,
                                              List<SpawnTables.SpawnEntry> pool, String markerKind,
                                              SeededRng rng, String tierTag, int partySize) {
        List<BlockPos> markers = markerPositions(built, room, markerKind);
        // A template without a boss marker puts its boss in the middle of the chamber. The
        // per-cell fallback would give the anchor cell — the corner quadrant of a 2x2 boss room.
        // Marker positions are clamped a block off the walls: an authored marker inside or against
        // a wall spawns a boss embedded in it.
        BlockPos pos = markers.isEmpty() ? built.roomCenter(room)
                : built.clampInside(room, markers.get(0), 1);
        if (markers.isEmpty()) {
            Teras.LOGGER.warn("Dungeons: {} has no '{}' marker — spawning at the room center. "
                    + "Add one to its template with the room editor.", room, markerKind);
        }
        SpawnTables.SpawnEntry drawn = SpawnTables.pickWeighted(pool, rng);
        Entity boss = spawnOne(level, drawn, pos);
        if (boss == null) {
            return List.of();
        }
        // Depth reaches the boss too, or a tramo turning its floors up leaves the one fight that
        // gates the descent untouched — and the boss becomes the easy part of a hard floor.
        // A boss room draws exactly one boss whatever the party, so the party's share has to land
        // on health or four players simply delete it four times as fast.
        EnemyScaling.apply(boss,
                es.boffmedia.teras.dungeon.piso.Dificultad.health(built.dificultad())
                        * es.boffmedia.teras.dungeon.piso.PartyScaling.health(partySize),
                es.boffmedia.teras.dungeon.piso.Dificultad.damage(built.dificultad()), 1.0);
        boss.addTag(tierTag);
        // Bosses are named unconditionally: the bar carries the name, and the nameplate is what a
        // player reads when the bar is not on screen yet.
        boss.setCustomName(EnemyNames.of(drawn.id()).copy()
                .withStyle(net.minecraft.ChatFormatting.RED));
        boss.setCustomNameVisible(true);
        return List.of(boss);
    }

    /** The authored enemy id an entity was spawned from, read back off its tag. */
    public static String authoredIdOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(Abilities.TAG_PREFIX)) {
                return tag.substring(Abilities.TAG_PREFIX.length());
            }
        }
        return "";
    }

    private static Entity spawnOne(ServerLevel level, SpawnTables.SpawnEntry entry, BlockPos pos) {
        Entity entity = switch (entry.kind()) {
            case ENTITY -> spawnEntity(level, entry.id(), pos);
            case CNPC -> spawnClone(level, entry, pos);
            case GEO -> spawnGeo(level, entry.id(), pos);
        };
        if (entity != null) {
            // Which authored enemy this is, for the ability layer. A tag rather than the NPC's
            // display name because installed clones are meant to be renamed in the CNPC editor.
            entity.addTag(Abilities.TAG_PREFIX + entry.id());
            if (entry.elite()) {
                // An elite is the one mob in a wave worth reacting to differently, and it looked
                // exactly like the chaff around it. A floating name is the cheapest way to say
                // "this one is not like the others" — a clone's own CNPC name is overwritten here
                // on purpose, because the wave decides what is elite, not the editor.
                entity.setCustomName(EnemyNames.of(entry.id()).copy()
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                entity.setCustomNameVisible(true);
            }
        }
        return entity;
    }

    /**
     * Spawns one enemy outside the wave flow — a boss's adds. Public because the ability layer
     * summons them mid-fight; the caller is responsible for getting the result into the room's kill
     * ledger (see {@code RunEngine.registerSummon}) or discarding it.
     */
    public static Entity spawnSummon(ServerLevel level, SpawnTables.SpawnEntry entry, BlockPos pos) {
        Entity add = spawnOne(level, entry, pos);
        if (add != null) {
            aggro(level, List.of(add));
        }
        return add;
    }

    /** The animated first-party enemy; {@code id} selects its {@link GeoEnemyVariant}. */
    private static Entity spawnGeo(ServerLevel level, String variantId, BlockPos pos) {
        DungeonGeoEnemy enemy = EntityInit.DUNGEON_ENEMY.get().create(level);
        if (enemy == null) {
            Teras.LOGGER.warn("Dungeons: could not create the animated enemy for variant '{}'", variantId);
            return null;
        }
        enemy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        enemy.applyVariant(variantId);
        enemy.addTag(DUNGEON_TAG);
        enemy.setPersistenceRequired();
        if (!level.addFreshEntity(enemy)) {
            Teras.LOGGER.warn("Dungeons: level refused the animated enemy at {}", pos);
            return null;
        }
        return enemy;
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

    /** Marker positions of {@code kind}, empty when the template shipped without any. */
    private static List<BlockPos> markerPositions(BuiltDungeon built, Room room, String kind) {
        List<BlockPos> positions = new ArrayList<>();
        for (TemplateMarkers.Marker marker : built.markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals(kind)) {
                positions.add(marker.pos());
            }
        }
        return positions;
    }

    /** Wave spawn points: the template's markers, or one per cell when it shipped without any. */
    private static List<BlockPos> spawnPositions(BuiltDungeon built, Room room, String kind) {
        List<BlockPos> positions = markerPositions(built, room, kind);
        if (positions.isEmpty()) {
            for (GridPos cell : room.cells()) {
                positions.add(built.cellOrigin(cell).offset(built.roomSize() / 2, 1, built.roomSize() / 2));
            }
        }
        return positions;
    }
}
