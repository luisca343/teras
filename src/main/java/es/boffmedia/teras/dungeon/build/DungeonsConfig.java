package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.YamlConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dungeon build tunables, in {@code config/teras/dungeons/config.yml} — same discipline as
 * {@link es.boffmedia.teras.karts.KartsConfig}: defaults reset before every load so an integrated
 * server never inherits the previous world's numbers.
 *
 * <p>{@code roomSize} is the <b>only</b> size convention: grid pitch, template footprint per cell
 * and door math all derive from it. The legacy paster mixed a ×21 grid pitch with
 * {@code roomSize−1} rotation offsets and was patched by trial (see DUNGEONS.md §1).</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonsConfig {
    private DungeonsConfig() {}

    private static int roomSize;
    private static int roomHeight;
    private static int doorWidth;
    private static int doorHeight;
    private static String theme;
    private static String dimension;
    private static int slotY;
    private static int slotSpacing;
    private static int maxSlots;
    private static String sealBlock;
    private static long clearReward;
    private static long curseToll;
    private static int deathPenaltyPct;
    private static String treasureLootTable;
    private static int maxParty;
    private static int entranceRadius;
    private static final Map<String, String> sounds = new LinkedHashMap<>();
    private static float soundVolume;

    /**
     * Cue name to vanilla sound id. Overridable in {@code config.yml} under {@code sonidos:} — the
     * migration path to first-party audio: point an entry at a {@code teras:} id once the assets
     * exist and nothing here has to change.
     */
    private static final Map<String, String> DEFAULT_SOUNDS = Map.ofEntries(
            Map.entry("ROOM_SEALED", "minecraft:block.iron_door.close"),
            Map.entry("ROOM_SEALED_BODY", "minecraft:block.anvil.land"),
            Map.entry("BOSS_SEALED", "minecraft:block.iron_door.close"),
            Map.entry("BOSS_SEALED_BODY", "minecraft:entity.wither.spawn"),
            Map.entry("ROOM_OPENED", "minecraft:block.iron_door.open"),
            Map.entry("ROOM_OPENED_BODY", "minecraft:block.note_block.bell"),
            Map.entry("BOSS_DEFEATED", "minecraft:ui.toast.challenge_complete"),
            Map.entry("TRAPDOOR_OPEN", "minecraft:block.end_portal.spawn"),
            Map.entry("SECRET_OPENED", "minecraft:block.vault.open_shutter"),
            Map.entry("ENEMY_ENRAGED", "minecraft:entity.ravager.roar"));

    static {
        resetToDefaults();
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
        RoomTemplates.load();
        es.boffmedia.teras.dungeon.encounter.SpawnTables.load();
        es.boffmedia.teras.world.VoidZones.logZoneMap();
    }

    public static void load() {
        resetToDefaults();
        Path dir = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons");
        Path path = dir.resolve("config.yml");
        try {
            if (!Files.exists(path)) {
                YamlConfig.write(path, renderTemplate());
                Teras.LOGGER.info("Dungeons: created default {}", path);
                return;
            }
            YamlConfig yaml = YamlConfig.read(path);
            roomSize = yaml.integer("tamanoSala", roomSize);
            roomHeight = yaml.integer("alturaSala", roomHeight);
            doorWidth = yaml.integer("anchoPuerta", doorWidth);
            doorHeight = yaml.integer("altoPuerta", doorHeight);
            theme = yaml.string("tema", theme);
            dimension = yaml.string("dimension", dimension);
            slotY = yaml.integer("slotY", slotY);
            slotSpacing = yaml.integer("separacionSlots", slotSpacing);
            maxSlots = yaml.integer("maxSlots", maxSlots);
            sealBlock = yaml.string("bloqueSello", sealBlock);
            clearReward = yaml.longValue("recompensaSala", clearReward);
            curseToll = yaml.longValue("peajeMaldicion", curseToll);
            deathPenaltyPct = yaml.integer("penalizacionMuertePct", deathPenaltyPct);
            treasureLootTable = yaml.string("lootTesoro", treasureLootTable);
            maxParty = Math.max(1, yaml.integer("maxGrupo", maxParty));
            entranceRadius = Math.max(1, yaml.integer("radioEntrada", entranceRadius));
            soundVolume = (float) yaml.integer("volumenSonidos", Math.round(soundVolume * 100)) / 100f;
            YamlConfig soundBlock = yaml.section("sonidos");
            for (String cue : DEFAULT_SOUNDS.keySet()) {
                sounds.put(cue, soundBlock.string(cue.toLowerCase(java.util.Locale.ROOT), sounds.get(cue)));
            }
            Teras.LOGGER.info("Dungeons: config loaded from {}", path);
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: failed to load config, using defaults: {}", e.toString());
        }
    }

    private static void resetToDefaults() {
        roomSize = 21;
        roomHeight = 8;
        doorWidth = 3;
        doorHeight = 3;
        theme = "base";
        dimension = "teras:vacio";
        slotY = 64;
        slotSpacing = 4096;
        maxSlots = 64;
        sealBlock = "minecraft:iron_bars";
        clearReward = 100;
        curseToll = 200;
        deathPenaltyPct = 5;
        treasureLootTable = "teras:dungeon/treasure";
        maxParty = 4;
        entranceRadius = 16;
        soundVolume = 0.8f;
        sounds.clear();
        sounds.putAll(DEFAULT_SOUNDS);
    }

    private static String renderTemplate() {
        return """
                # Teras dungeons — build settings.
                # tamanoSala is the cell pitch AND the per-cell template footprint; a template for a
                # 2x1 room must be exactly twice as wide. alturaSala is the template height.
                tamanoSala: 21
                alturaSala: 8
                anchoPuerta: 3
                altoPuerta: 3
                # Room template pools live in rooms.json next to this file.
                tema: base
                # Instanced runs build in this dimension, on a slot lattice at slotY. The void is
                # shared: which coordinates belong to what is the VoidZones map (runs keep the
                # x>=0,z>=0 quadrant; room-editor pads the strip just north of it; the rest free).
                dimension: teras:vacio
                slotY: 64
                separacionSlots: 4096
                maxSlots: 64
                # Run loop: what seals doors in combat, and the money knobs (through the Teras bank).
                bloqueSello: minecraft:iron_bars
                recompensaSala: 100
                peajeMaldicion: 200
                penalizacionMuertePct: 5
                lootTesoro: teras:dungeon/treasure
                # Party play: group size cap, and how close to a marked entrance NPC a player must
                # stand for 'entrar' to work (also the gather radius for their party members).
                maxGrupo: 4
                radioEntrada: 16
                # Cues, as vanilla sound ids. The door cues play once per doorway of the room; the
                # _body ones play once from the middle of it. Point any of these at a teras: id once
                # you ship your own audio — nothing else has to change.
                volumenSonidos: 80
                sonidos:
                  room_sealed: minecraft:block.iron_door.close
                  room_sealed_body: minecraft:block.anvil.land
                  boss_sealed: minecraft:block.iron_door.close
                  boss_sealed_body: minecraft:entity.wither.spawn
                  room_opened: minecraft:block.iron_door.open
                  room_opened_body: minecraft:block.note_block.bell
                  boss_defeated: minecraft:ui.toast.challenge_complete
                  trapdoor_open: minecraft:block.end_portal.spawn
                  secret_opened: minecraft:block.vault.open_shutter
                  enemy_enraged: minecraft:entity.ravager.roar
                # Enemy waves live in enemies.json next to this file.
                """;
    }

    public static int roomSize() {
        return roomSize;
    }

    public static int roomHeight() {
        return roomHeight;
    }

    public static int doorWidth() {
        return doorWidth;
    }

    public static int doorHeight() {
        return doorHeight;
    }

    public static String theme() {
        return theme;
    }

    public static String dimension() {
        return dimension;
    }

    public static int slotY() {
        return slotY;
    }

    public static int slotSpacing() {
        return slotSpacing;
    }

    public static int maxSlots() {
        return maxSlots;
    }

    public static String sealBlock() {
        return sealBlock;
    }

    public static long clearReward() {
        return clearReward;
    }

    public static long curseToll() {
        return curseToll;
    }

    public static int deathPenaltyPct() {
        return deathPenaltyPct;
    }

    public static String treasureLootTable() {
        return treasureLootTable;
    }

    public static int maxParty() {
        return maxParty;
    }

    public static int entranceRadius() {
        return entranceRadius;
    }

    /** Vanilla (or first-party) sound id for a cue; never null — unknown cues fall back silently. */
    public static String sound(String cue) {
        return sounds.getOrDefault(cue, DEFAULT_SOUNDS.get(cue));
    }

    public static float soundVolume() {
        return soundVolume;
    }
}
