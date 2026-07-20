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
    private static String dimension;
    private static int slotY;
    private static int slotSpacing;
    private static int maxSlots;
    private static String sealBlock;
    private static long clearReward;
    private static long curseToll;
    private static int deathPenaltyPct;
    private static String treasureLootTable;
    private static String curseLootTable;
    private static String devilLootTable;
    private static String bossLootTable;
    private static String crackBlock;
    private static int maxParty;
    private static int entranceRadius;
    private static final Map<String, String> sounds = new LinkedHashMap<>();
    private static float soundVolume;
    /**
     * How likely each curse is per floor, before the piso's own {@code maldiciones} filter it. A
     * curse every eligible piso at that depth refuses simply cannot occur there — the intended
     * consequence, not a gap.
     */
    private static final Map<es.boffmedia.teras.dungeon.model.Curse, Double> CURSE_CHANCES =
            new LinkedHashMap<>();

    // Coins: what enemies pay, how it is picked up, and what it is worth on the way out.
    private static int coinsNormalMin;
    private static int coinsNormalMax;
    private static int coinsMiniBossMin;
    private static int coinsMiniBossMax;
    private static int coinsBossMin;
    private static int coinsBossMax;
    private static int coinStageScalingPct;
    private static double coinPickupRadius;
    private static int coinDeathPenaltyPct;
    private static int coinToPesos;
    private static int curseCoinToll;

    // Shop: per-kind weight and price. Prices scale per floor like income does.
    private static final Map<String, Integer> shopWeights = new LinkedHashMap<>();
    private static final Map<String, Integer> shopPrices = new LinkedHashMap<>();

    // Challenge room waves.
    private static int challengeWaves;
    private static int challengeExtraWaveStage;
    private static int challengeWaveGrowthPct;
    private static int challengeReward;

    // Sacrifice room.
    private static float sacrificeDamage;
    private static int sacrificeBaseChancePct;
    private static int sacrificeStepChancePct;
    private static int sacrificeCoinsMin;
    private static int sacrificeCoinsMax;

    // Arcade room.
    private static int arcadePrice;
    private static int arcadeBreakChancePct;

    // Devil deal room.
    private static int devilCoinPrice;
    private static int devilHeartPrice;

    // Backend reporting.
    private static boolean backendPostEnabled;

    /** Shop stock kinds, as they are keyed in the config's {@code tienda:} block. */
    private static final Map<String, Integer> DEFAULT_SHOP_WEIGHTS = new LinkedHashMap<>();
    private static final Map<String, Integer> DEFAULT_SHOP_PRICES = new LinkedHashMap<>();

    static {
        DEFAULT_SHOP_WEIGHTS.put("pocion", 20);
        DEFAULT_SHOP_WEIGHTS.put("pocion_mayor", 10);
        DEFAULT_SHOP_WEIGHTS.put("mapa", 12);
        DEFAULT_SHOP_WEIGHTS.put("brujula", 12);
        DEFAULT_SHOP_WEIGHTS.put("rompemuros", 14);
        DEFAULT_SHOP_WEIGHTS.put("bendicion_fuerza", 8);
        DEFAULT_SHOP_WEIGHTS.put("bendicion_resistencia", 8);
        DEFAULT_SHOP_WEIGHTS.put("bendicion_velocidad", 8);
        DEFAULT_SHOP_WEIGHTS.put("fenix", 5);
        DEFAULT_SHOP_WEIGHTS.put("seguro", 8);
        DEFAULT_SHOP_WEIGHTS.put("caja_sorpresa", 10);

        DEFAULT_SHOP_PRICES.put("pocion", 10);
        DEFAULT_SHOP_PRICES.put("pocion_mayor", 25);
        DEFAULT_SHOP_PRICES.put("mapa", 15);
        DEFAULT_SHOP_PRICES.put("brujula", 15);
        DEFAULT_SHOP_PRICES.put("rompemuros", 20);
        DEFAULT_SHOP_PRICES.put("bendicion_fuerza", 12);
        DEFAULT_SHOP_PRICES.put("bendicion_resistencia", 12);
        DEFAULT_SHOP_PRICES.put("bendicion_velocidad", 12);
        DEFAULT_SHOP_PRICES.put("fenix", 40);
        DEFAULT_SHOP_PRICES.put("seguro", 8);
        DEFAULT_SHOP_PRICES.put("caja_sorpresa", 12);
    }

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
            Map.entry("ENEMY_ENRAGED", "minecraft:entity.ravager.roar"),
            Map.entry("COIN_PICKUP", "minecraft:entity.experience_orb.pickup"),
            Map.entry("PURCHASE", "minecraft:entity.player.levelup"),
            Map.entry("PURCHASE_DENIED", "minecraft:block.note_block.bass"),
            Map.entry("CHALLENGE_STARTED", "minecraft:event.raid.horn"),
            Map.entry("WAVE_CLEARED", "minecraft:block.note_block.chime"),
            Map.entry("SACRIFICE", "minecraft:entity.player.hurt"),
            Map.entry("SACRIFICE_REWARD", "minecraft:block.amethyst_block.chime"),
            Map.entry("ARCADE_PLAY", "minecraft:block.lever.click"),
            Map.entry("ARCADE_WIN", "minecraft:entity.player.levelup"),
            Map.entry("ARCADE_BREAK", "minecraft:entity.item.break"),
            Map.entry("DEVIL_OPENED", "minecraft:entity.wither.ambient"),
            Map.entry("DEVIL_DEAL", "minecraft:entity.evoker.cast_spell"),
            Map.entry("PHOENIX", "minecraft:item.totem.use"));

    static {
        resetToDefaults();
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
        es.boffmedia.teras.dungeon.piso.PisoCatalog.load();
        // Needs the server: a piso passes its structural checks naming templates that resolve to
        // nothing on disk, and with no fallback between pisos that is fatal to any floor it fills.
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                event.getServer().getStructureManager());
        es.boffmedia.teras.dungeon.encounter.SpawnTables.load();
        es.boffmedia.teras.dungeon.gear.GearConfig.load();
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
            dimension = yaml.string("dimension", dimension);
            slotY = yaml.integer("slotY", slotY);
            slotSpacing = yaml.integer("separacionSlots", slotSpacing);
            maxSlots = yaml.integer("maxSlots", maxSlots);
            sealBlock = yaml.string("bloqueSello", sealBlock);
            clearReward = yaml.longValue("recompensaSala", clearReward);
            curseToll = yaml.longValue("peajeMaldicion", curseToll);
            deathPenaltyPct = yaml.integer("penalizacionMuertePct", deathPenaltyPct);
            treasureLootTable = yaml.string("lootTesoro", treasureLootTable);
            curseLootTable = yaml.string("lootMaldicion", curseLootTable);
            devilLootTable = yaml.string("lootTrato", devilLootTable);
            bossLootTable = yaml.string("lootJefe", bossLootTable);
            crackBlock = yaml.string("bloqueGrieta", crackBlock);
            maxParty = Math.max(1, yaml.integer("maxGrupo", maxParty));
            entranceRadius = Math.max(1, yaml.integer("radioEntrada", entranceRadius));
            backendPostEnabled = yaml.bool("enviarResultados", backendPostEnabled);

            YamlConfig curseBlock = yaml.section("maldiciones");
            for (es.boffmedia.teras.dungeon.model.Curse curse
                    : es.boffmedia.teras.dungeon.model.Curse.values()) {
                String key = curse.name().toLowerCase(java.util.Locale.ROOT);
                int pct = curseBlock.integer(key,
                        (int) Math.round(CURSE_CHANCES.getOrDefault(curse, 0.0) * 100));
                CURSE_CHANCES.put(curse, Math.max(0, Math.min(100, pct)) / 100.0);
            }

            YamlConfig coins = yaml.section("monedas");
            coinsNormalMin = coins.integer("normalMin", coinsNormalMin);
            coinsNormalMax = coins.integer("normalMax", coinsNormalMax);
            coinsMiniBossMin = coins.integer("miniJefeMin", coinsMiniBossMin);
            coinsMiniBossMax = coins.integer("miniJefeMax", coinsMiniBossMax);
            coinsBossMin = coins.integer("jefeMin", coinsBossMin);
            coinsBossMax = coins.integer("jefeMax", coinsBossMax);
            coinStageScalingPct = coins.integer("escaladoPorPisoPct", coinStageScalingPct);
            coinPickupRadius = Math.max(0.5, coins.integer("radioRecogida",
                    (int) Math.round(coinPickupRadius)));
            coinDeathPenaltyPct = coins.integer("muertePct", coinDeathPenaltyPct);
            coinToPesos = coins.integer("cambioPesos", coinToPesos);
            curseCoinToll = coins.integer("peajeMaldicion", curseCoinToll);

            YamlConfig shop = yaml.section("tienda");
            YamlConfig shopWeightBlock = shop.section("pesos");
            YamlConfig shopPriceBlock = shop.section("precios");
            for (String kind : DEFAULT_SHOP_WEIGHTS.keySet()) {
                shopWeights.put(kind, shopWeightBlock.integer(kind, shopWeights.get(kind)));
                shopPrices.put(kind, shopPriceBlock.integer(kind, shopPrices.get(kind)));
            }

            YamlConfig challenge = yaml.section("desafio");
            challengeWaves = Math.max(1, challenge.integer("oleadasBase", challengeWaves));
            challengeExtraWaveStage = challenge.integer("oleadaExtraDesdeEtapa", challengeExtraWaveStage);
            challengeWaveGrowthPct = challenge.integer("crecimientoOleadaPct", challengeWaveGrowthPct);
            challengeReward = challenge.integer("recompensaMonedas", challengeReward);

            YamlConfig sacrifice = yaml.section("sacrificio");
            sacrificeDamage = sacrifice.integer("dano", Math.round(sacrificeDamage));
            sacrificeBaseChancePct = sacrifice.integer("probBasePct", sacrificeBaseChancePct);
            sacrificeStepChancePct = sacrifice.integer("probPorPasoPct", sacrificeStepChancePct);
            sacrificeCoinsMin = sacrifice.integer("monedasMin", sacrificeCoinsMin);
            sacrificeCoinsMax = sacrifice.integer("monedasMax", sacrificeCoinsMax);

            YamlConfig arcade = yaml.section("arcada");
            arcadePrice = arcade.integer("precio", arcadePrice);
            arcadeBreakChancePct = arcade.integer("probRoturaPct", arcadeBreakChancePct);

            YamlConfig devil = yaml.section("trato");
            devilCoinPrice = devil.integer("precioMonedas", devilCoinPrice);
            devilHeartPrice = devil.integer("precioCorazones", devilHeartPrice);

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
        roomHeight = 12;
        doorWidth = 3;
        doorHeight = 3;
        dimension = "teras:vacio";
        slotY = 64;
        slotSpacing = 4096;
        maxSlots = 64;
        sealBlock = "minecraft:iron_bars";
        // Both zero by default: a run's income is coins now, converted in one lump when it is
        // completed. The ₽ knobs stay wired for servers that want to pay per room anyway.
        clearReward = 0;
        curseToll = 0;
        deathPenaltyPct = 0;
        treasureLootTable = "teras:dungeon/treasure";
        curseLootTable = "teras:dungeon/curse";
        devilLootTable = "teras:dungeon/devil";
        bossLootTable = "teras:dungeon/boss";
        crackBlock = "teras:muro_agrietado";
        maxParty = 4;
        entranceRadius = 16;
        backendPostEnabled = true;

        coinsNormalMin = 1;
        coinsNormalMax = 3;
        coinsMiniBossMin = 8;
        coinsMiniBossMax = 15;
        coinsBossMin = 20;
        coinsBossMax = 40;
        coinStageScalingPct = 15;
        coinPickupRadius = 2;
        coinDeathPenaltyPct = 20;
        coinToPesos = 10;
        curseCoinToll = 15;

        shopWeights.clear();
        shopWeights.putAll(DEFAULT_SHOP_WEIGHTS);
        shopPrices.clear();
        shopPrices.putAll(DEFAULT_SHOP_PRICES);

        challengeWaves = 2;
        challengeExtraWaveStage = 5;
        challengeWaveGrowthPct = 30;
        challengeReward = 30;

        sacrificeDamage = 4.0f;
        sacrificeBaseChancePct = 10;
        sacrificeStepChancePct = 10;
        sacrificeCoinsMin = 20;
        sacrificeCoinsMax = 40;

        arcadePrice = 5;
        arcadeBreakChancePct = 8;

        devilCoinPrice = 60;
        devilHeartPrice = 2;

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
                alturaSala: 12
                anchoPuerta: 3
                altoPuerta: 3
                # Per-floor curse chances, as percentages. A piso only receives the curses its own
                # 'maldiciones' accepts, so one every eligible piso refuses never occurs at that depth.
                maldiciones:
                  labyrinth: 10
                  lost: 10
                # Places live in pisos/*.json and the dungeons that use them in mazmorras.json.
                # There is no global theme: a piso owns its own rooms outright.
                # Instanced runs build in this dimension, on a slot lattice at slotY. The void is
                # shared: which coordinates belong to what is the VoidZones map (runs keep the
                # x>=0,z>=0 quadrant; room-editor pads the strip just north of it; the rest free).
                dimension: teras:vacio
                slotY: 64
                separacionSlots: 4096
                maxSlots: 64
                # Run loop: what seals doors in combat, and the wall a secret room hides behind.
                bloqueSello: minecraft:iron_bars
                bloqueGrieta: teras:muro_agrietado
                # ₽ knobs, through the Teras bank. All zero by default: a run earns coins and they
                # are converted in one lump when it is completed (see monedas.cambioPesos). Raise
                # these only if you also want per-room bank payouts on top.
                recompensaSala: 0
                peajeMaldicion: 0
                penalizacionMuertePct: 0
                lootTesoro: teras:dungeon/treasure
                lootMaldicion: teras:dungeon/curse
                lootTrato: teras:dungeon/devil
                lootJefe: teras:dungeon/boss
                # Party play: group size cap, and how close to a marked entrance NPC a player must
                # stand for 'entrar' to work (also the gather radius for their party members).
                maxGrupo: 4
                radioEntrada: 16
                # POST completed/abandoned runs to SmartRotom (leaderboards). Needs config.yml's
                # apiToken set, or the route 401s — see docs/SMARTROTOM_ENDPOINTS_HANDOFF.md.
                enviarResultados: true
                # --- Coins ------------------------------------------------------------------
                # The in-run currency. Enemies drop it and drop nothing else (their vanilla loot and
                # XP are cancelled); the shop, the arcade, devil deals and the curse toll all price
                # in it. Coins are never money: they cannot be picked up into an inventory and only
                # become ₽ when the party completes the run, at cambioPesos each, split equally.
                monedas:
                  normalMin: 1
                  normalMax: 3
                  miniJefeMin: 8
                  miniJefeMax: 15
                  jefeMin: 20
                  jefeMax: 40
                  # Compounded per floor. Shop prices scale by the same number, so descending does
                  # not make the shop cheap.
                  escaladoPorPisoPct: 15
                  radioRecogida: 2
                  # Slice of the shared purse lost on any member's death.
                  muertePct: 20
                  cambioPesos: 10
                  peajeMaldicion: 15
                # --- Shop -------------------------------------------------------------------
                # One pedestal per shopslot marker. The first slot is always a wall-breaker charge
                # so secret rooms are never locked out; the rest roll from these weights, without
                # repeats. Prices are in coins, scaled per floor.
                tienda:
                  pesos:
                    pocion: 20
                    pocion_mayor: 10
                    mapa: 12
                    brujula: 12
                    rompemuros: 14
                    bendicion_fuerza: 8
                    bendicion_resistencia: 8
                    bendicion_velocidad: 8
                    fenix: 5
                    seguro: 8
                    caja_sorpresa: 10
                  precios:
                    pocion: 10
                    pocion_mayor: 25
                    mapa: 15
                    brujula: 15
                    rompemuros: 20
                    bendicion_fuerza: 12
                    bendicion_resistencia: 12
                    bendicion_velocidad: 12
                    fenix: 40
                    seguro: 8
                    caja_sorpresa: 12
                # --- Challenge room ---------------------------------------------------------
                # Stepping on the plate seals the doors and runs the waves; surviving pays out.
                desafio:
                  oleadasBase: 2
                  oleadaExtraDesdeEtapa: 5
                  crecimientoOleadaPct: 30
                  recompensaMonedas: 30
                # --- Sacrifice room ---------------------------------------------------------
                # Each step on the spikes costs health (which nothing but a dungeon potion gives
                # back) and improves the odds of the payout.
                sacrificio:
                  dano: 4
                  probBasePct: 10
                  probPorPasoPct: 10
                  monedasMin: 20
                  monedasMax: 40
                # --- Arcade room ------------------------------------------------------------
                arcada:
                  precio: 5
                  probRoturaPct: 8
                # --- Devil deal -------------------------------------------------------------
                # The room behind the barred door, which opens when the floor's boss falls. Pay in
                # coins, or sneak-click to pay in maximum hearts for the rest of the run.
                trato:
                  precioMonedas: 60
                  precioCorazones: 2
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
                  coin_pickup: minecraft:entity.experience_orb.pickup
                  purchase: minecraft:entity.player.levelup
                  purchase_denied: minecraft:block.note_block.bass
                  challenge_started: minecraft:event.raid.horn
                  wave_cleared: minecraft:block.note_block.chime
                  sacrifice: minecraft:entity.player.hurt
                  sacrifice_reward: minecraft:block.amethyst_block.chime
                  arcade_play: minecraft:block.lever.click
                  arcade_win: minecraft:entity.player.levelup
                  arcade_break: minecraft:entity.item.break
                  devil_opened: minecraft:entity.wither.ambient
                  devil_deal: minecraft:entity.evoker.cast_spell
                  phoenix: minecraft:item.totem.use
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

    public static Map<es.boffmedia.teras.dungeon.model.Curse, Double> curseChances() {
        return Map.copyOf(CURSE_CHANCES);
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

    public static String curseLootTable() {
        return curseLootTable;
    }

    public static String bossLootTable() {
        return bossLootTable;
    }

    public static String devilLootTable() {
        return devilLootTable;
    }

    public static String crackBlock() {
        return crackBlock;
    }

    public static boolean backendPostEnabled() {
        return backendPostEnabled;
    }

    public static int coinsNormalMin() {
        return coinsNormalMin;
    }

    public static int coinsNormalMax() {
        return coinsNormalMax;
    }

    public static int coinsMiniBossMin() {
        return coinsMiniBossMin;
    }

    public static int coinsMiniBossMax() {
        return coinsMiniBossMax;
    }

    public static int coinsBossMin() {
        return coinsBossMin;
    }

    public static int coinsBossMax() {
        return coinsBossMax;
    }

    public static int coinStageScalingPct() {
        return coinStageScalingPct;
    }

    public static double coinPickupRadius() {
        return coinPickupRadius;
    }

    public static int coinDeathPenaltyPct() {
        return coinDeathPenaltyPct;
    }

    public static int coinToPesos() {
        return coinToPesos;
    }

    public static int curseCoinToll() {
        return curseCoinToll;
    }

    /** Roll weight of a shop stock kind; 0 keeps it out of the pool entirely. */
    public static int shopWeight(String kind) {
        return shopWeights.getOrDefault(kind, 0);
    }

    /** Base price in coins, before per-floor scaling. */
    public static int shopPrice(String kind) {
        return shopPrices.getOrDefault(kind, DEFAULT_SHOP_PRICES.getOrDefault(kind, 10));
    }

    public static int challengeWaves() {
        return challengeWaves;
    }

    public static int challengeExtraWaveStage() {
        return challengeExtraWaveStage;
    }

    public static int challengeWaveGrowthPct() {
        return challengeWaveGrowthPct;
    }

    public static int challengeReward() {
        return challengeReward;
    }

    public static float sacrificeDamage() {
        return sacrificeDamage;
    }

    public static int sacrificeBaseChancePct() {
        return sacrificeBaseChancePct;
    }

    public static int sacrificeStepChancePct() {
        return sacrificeStepChancePct;
    }

    public static int sacrificeCoinsMin() {
        return sacrificeCoinsMin;
    }

    public static int sacrificeCoinsMax() {
        return sacrificeCoinsMax;
    }

    public static int arcadePrice() {
        return arcadePrice;
    }

    public static int arcadeBreakChancePct() {
        return arcadeBreakChancePct;
    }

    public static int devilCoinPrice() {
        return devilCoinPrice;
    }

    public static int devilHeartPrice() {
        return devilHeartPrice;
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
