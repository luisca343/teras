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
import java.util.List;
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
@EventBusSubscriber(modid = Teras.MOD_ID)
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
    private static int desertionGraceSeconds;
    private static int buildTimeoutSeconds;
    private static String sealBlock;
    private static String sealRuneBlock;
    private static String sealRuneLitBlock;
    private static int descentSeconds;
    private static int sealDoorWidth;
    private static int sealDoorHeight;
    private static long clearReward;
    private static int deathPenaltyPct;
    private static String treasureLootTable;
    private static String secretLootTable;
    private static String superSecretLootTable;
    private static String devilLootTable;
    private static String ordenLootTable;
    private static String treasureArmaLootTable;
    private static String treasureVitalidadLootTable;
    private static int treasureProvisionCoins;
    private static int treasureProvisionCharges;
    private static int gangaChance;
    private static int gangaDiscountPct;
    private static String gambleLootTable;
    private static int shopPremiumStage;
    private static String bossLootTable;
    private static String crackBlock;
    private static String spikeBlock;
    private static int curseDoorTollHearts;
    private static int marketSlots;
    private static int marketReward;
    private static int purgePrice;
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

    /**
     * Cell budget per canonical floor, index 0 = floor 1, and the 0..jitter spread added to it.
     * A floor's size is a property of <i>which floor it is</i>, never of how long the run using it
     * happens to be — see {@code FloorDepth}.
     */
    private static List<Integer> celdas = List.of();
    private static int jitter;

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
    private static int debtInterestPct;
    private static int debtSettleDiscountPct;
    private static int debtFloorsToCollect;

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
            Map.entry("PHOENIX", "minecraft:item.totem.use"),
            Map.entry("SEAL_RESTORED", "minecraft:block.beacon.activate"),
            Map.entry("DESCENT_TICK", "minecraft:block.note_block.hat"));

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
            desertionGraceSeconds = yaml.integer("graciaAbandonoSegundos", desertionGraceSeconds);
            buildTimeoutSeconds = yaml.integer("timeoutConstruccionSegundos", buildTimeoutSeconds);
            sealBlock = yaml.string("bloqueSello", sealBlock);
            sealRuneBlock = yaml.string("bloqueRunaSello", sealRuneBlock);
            sealRuneLitBlock = yaml.string("bloqueRunaSelloEncendida", sealRuneLitBlock);
            descentSeconds = Math.max(3, yaml.integer("segundosDescenso", descentSeconds));
            sealDoorWidth = Math.max(1, yaml.integer("anchoPuertaSello", sealDoorWidth));
            sealDoorHeight = Math.max(1, yaml.integer("altoPuertaSello", sealDoorHeight));
            clearReward = yaml.longValue("recompensaSala", clearReward);
            deathPenaltyPct = yaml.integer("penalizacionMuertePct", deathPenaltyPct);
            treasureLootTable = yaml.string("lootTesoro", treasureLootTable);
            secretLootTable = yaml.string("lootSecreta", secretLootTable);
            superSecretLootTable = yaml.string("lootSupersecreta", superSecretLootTable);
            devilLootTable = yaml.string("lootTrato", devilLootTable);
            ordenLootTable = yaml.string("lootOrden", ordenLootTable);
            treasureArmaLootTable = yaml.string("lootTesoroArma", treasureArmaLootTable);
            treasureVitalidadLootTable = yaml.string("lootTesoroVitalidad",
                    treasureVitalidadLootTable);
            treasureProvisionCoins = yaml.integer("tesoroProvisionMonedas", treasureProvisionCoins);
            treasureProvisionCharges = yaml.integer("tesoroProvisionCargas",
                    treasureProvisionCharges);
            bossLootTable = yaml.string("lootJefe", bossLootTable);
            crackBlock = yaml.string("bloqueGrieta", crackBlock);
            spikeBlock = yaml.string("bloquePinchos", spikeBlock);
            curseDoorTollHearts = yaml.integer("peajePuertaCorazones", curseDoorTollHearts);
            marketSlots = yaml.integer("ofertasMaldicion", marketSlots);
            marketReward = yaml.integer("pagoAfliccion", marketReward);
            purgePrice = yaml.integer("precioPurga", purgePrice);
            maxParty = Math.max(1, yaml.integer("maxGrupo", maxParty));
            entranceRadius = Math.max(1, yaml.integer("radioEntrada", entranceRadius));
            backendPostEnabled = yaml.bool("enviarResultados", backendPostEnabled);

            YamlConfig gen = yaml.section("generacion");
            jitter = Math.max(0, gen.integer("jitter", jitter));
            List<Object> curve = gen.list("celdas");
            if (!curve.isEmpty()) {
                List<Integer> parsed = new java.util.ArrayList<>();
                for (Object cell : curve) {
                    try {
                        parsed.add(Integer.parseInt(String.valueOf(cell).trim()));
                    } catch (NumberFormatException e) {
                        parsed.clear();
                        break;
                    }
                }
                // All or nothing: half a curve would silently reshape whichever floors survived
                // parsing, and a floor quietly changing size is the bug this table was built to end.
                if (parsed.isEmpty() || parsed.stream().anyMatch(cells -> cells < 1)) {
                    Teras.LOGGER.error("Dungeons: 'generacion.celdas' is not a list of positive "
                            + "integers; keeping the built-in curve {}", celdas);
                } else {
                    celdas = List.copyOf(parsed);
                }
            }

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

            YamlConfig shop = yaml.section("tienda");
            YamlConfig shopWeightBlock = shop.section("pesos");
            YamlConfig shopPriceBlock = shop.section("precios");
            for (String kind : DEFAULT_SHOP_WEIGHTS.keySet()) {
                shopWeights.put(kind, shopWeightBlock.integer(kind, shopWeights.get(kind)));
                shopPrices.put(kind, shopPriceBlock.integer(kind, shopPrices.get(kind)));
            }
            gangaChance = shop.integer("gangaProbabilidad", gangaChance);
            gangaDiscountPct = shop.integer("gangaDescuentoPct", gangaDiscountPct);
            gambleLootTable = shop.string("lootCaja", gambleLootTable);
            shopPremiumStage = shop.integer("pisoPremium", shopPremiumStage);

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
            debtInterestPct = devil.integer("interesDeudaPct", debtInterestPct);
            debtSettleDiscountPct = devil.integer("descuentoSaldoPct", debtSettleDiscountPct);
            debtFloorsToCollect = devil.integer("pisosHastaCobrador", debtFloorsToCollect);

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
        celdas = es.boffmedia.teras.dungeon.gen.GenConfig.defaults().celdas();
        jitter = es.boffmedia.teras.dungeon.gen.GenConfig.defaults().jitter();
        roomSize = 21;
        roomHeight = 12;
        doorWidth = 3;
        doorHeight = 3;
        dimension = "teras:vacio";
        slotY = 64;
        slotSpacing = 4096;
        maxSlots = 64;
        // Long enough to survive a router blip or a client restart, short enough that a party that
        // is not coming back stops holding a slot and a floor.
        desertionGraceSeconds = 180;
        // Generous on purpose. A build that actually fails is reported the moment it does, so this
        // only ever catches a completion that never arrives at all — and the job queue is one
        // shared line, so a floor can legitimately sit behind a dozen other builds and discards.
        // Too tight a timeout here would fail healthy runs on a busy server.
        buildTimeoutSeconds = 300;
        sealBlock = "minecraft:iron_bars";
        // The seal glyph's rune inlay, dull while the boss lives and lit when the seal re-pins.
        // The lit rune gets an invisible light block stamped over it, so any block works here.
        sealRuneBlock = "minecraft:polished_basalt";
        sealRuneLitBlock = "minecraft:amethyst_block";
        // The straggler bell: the first member down the pit starts this countdown, and when it
        // ends the rest of the party descends with them.
        descentSeconds = 15;
        // The grand ceremonial door the boss's death carves between the 2×2 arena and the 2×2
        // sala del sello: one wide opening centered on the shared face, taller than a normal door.
        sealDoorWidth = 7;
        sealDoorHeight = 5;
        // Both zero by default: a run's income is coins now, converted in one lump when it is
        // completed. The ₽ knobs stay wired for servers that want to pay per room anyway.
        clearReward = 0;
        deathPenaltyPct = 0;
        treasureLootTable = "teras:dungeon/treasure";
        // A secret costs a wall charge the party bought, so it pays the treasure table: breaking in
        // has to be worth at least what getting in cost. The super secret is rarer and pays better.
        secretLootTable = "teras:dungeon/treasure";
        superSecretLootTable = "teras:dungeon/boss";
        devilLootTable = "teras:dungeon/devil";
        ordenLootTable = "teras:dungeon/orden";
        // The treasure choice room's three stands (PISOS §66). arma and vitalidad are loot tables
        // so their bundles are data; provisión is coins + a wall charge, handled in code because
        // both go to the shared purse rather than into a pocket.
        treasureArmaLootTable = "teras:dungeon/treasure_arma";
        treasureVitalidadLootTable = "teras:dungeon/treasure_vitalidad";
        treasureProvisionCoins = 40;
        treasureProvisionCharges = 1;
        // The shop's floor deal and its gamble (PISOS §66). The ganga shows only sometimes at base;
        // future economy items raise a run's discount level toward always (the Steam-Sale hook, held
        // on DungeonRun). The gamble draws its own steady table — no epic jackpot. Premium stock
        // (fénix/seguro) waits until the runs that can afford it.
        gangaChance = 40;
        gangaDiscountPct = 30;
        gambleLootTable = "teras:dungeon/gamble";
        shopPremiumStage = 3;
        bossLootTable = "teras:dungeon/boss";
        crackBlock = "teras:muro_agrietado";
        spikeBlock = "minecraft:pointed_dripstone";
        // One heart to cross, floored so it can never kill. Real under the health lockdown,
        // where the only healing left is a potion somebody paid for.
        curseDoorTollHearts = 1;
        marketSlots = 3;
        marketReward = 40;
        purgePrice = 30;
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
        debtInterestPct = 50;
        debtSettleDiscountPct = 20;
        debtFloorsToCollect = 2;

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
                # The difficulty curve: how many grid cells each floor of the canonical sequence is
                # worth, floor 1 first. A 2x2 room spends four cells, so this is not a room count.
                #
                # Size is a property of WHICH FLOOR this is, never of how long the run using it is.
                # A mazmorra declares where it opens with 'primerPiso' in mazmorras.json, so a
                # one-floor challenge with primerPiso 10 builds floor 10 — the same 30 cells the
                # full descent gives it — instead of a floor 1. Adding a tramo to a mazmorra
                # lengthens the run and changes nothing about the floors it already had.
                #
                # The list also declares how deep the sequence goes: 12 entries means 12 floors, and
                # a mazmorra whose window reaches past that is refused at load with an error naming
                # it. Entries 1-6 are the values the old Isaac formula produced; 7-12 are authored,
                # and are the reason this is a table at all — the formula flattened everything past
                # floor 5 to the same size.
                #
                # 'jitter' is added on top, 0..n, drawn per floor from the run seed.
                generacion:
                  celdas: [10, 13, 17, 20, 22, 22, 24, 26, 28, 30, 34, 40]
                  jitter: 2
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
                # Teardown safety net. A run whose party has been entirely offline for this long is
                # ended and its floor swept — otherwise a party that disconnects holds a slot, a
                # floor, its shop and its pedestals until the next restart. A run that has been
                # waiting on a build job for longer than the timeout is failed and cleared: it
                # would never become ACTIVE, and only ACTIVE runs can be ended.
                graciaAbandonoSegundos: 180
                timeoutConstruccionSegundos: 300
                # Run loop: what seals doors in combat, and the wall a secret room hides behind.
                bloqueSello: minecraft:iron_bars
                bloqueGrieta: teras:muro_agrietado
                # La sala del sello. The rune inlay of the seal glyph swaps dull -> lit when the
                # boss falls (an invisible light block is stamped over each lit rune, so any block
                # reads). segundosDescenso is the straggler bell: the first member down the pit
                # starts it, and at zero the rest of the party descends with them.
                bloqueRunaSello: minecraft:polished_basalt
                bloqueRunaSelloEncendida: minecraft:amethyst_block
                segundosDescenso: 15
                # The grand ceremonial door between the 2x2 boss arena and the 2x2 sala del sello:
                # one wide opening the boss's death carves, centered on their shared face.
                anchoPuertaSello: 7
                altoPuertaSello: 5
                # The curse room is a market, not a tax: its doorway is framed in these spikes
                # so the toll is visible from a room away, and crossing costs each player this
                # many hearts once per floor (floored so it can never kill). Inside, offers
                # trade an affliction for coins, and the purge pedestal buys one back.
                bloquePinchos: minecraft:pointed_dripstone
                peajePuertaCorazones: 1
                ofertasMaldicion: 3
                pagoAfliccion: 40
                precioPurga: 30
                # ₽ knobs, through the Teras bank. All zero by default: a run earns coins and they
                # are converted in one lump when it is completed (see monedas.cambioPesos). Raise
                # these only if you also want per-room bank payouts on top.
                recompensaSala: 0
                penalizacionMuertePct: 0
                lootTesoro: teras:dungeon/treasure
                lootSecreta: teras:dungeon/treasure
                lootSupersecreta: teras:dungeon/boss
                lootTrato: teras:dungeon/devil
                lootOrden: teras:dungeon/orden
                lootJefe: teras:dungeon/boss
                # The treasure room's three-stand choice (PISOS 66): each player picks one of arma
                # (a gear piece), vitalidad (potions) or provision (coins + a wall charge). arma and
                # vitalidad are tables; provision is these two numbers, scaled by floor.
                lootTesoroArma: teras:dungeon/treasure_arma
                lootTesoroVitalidad: teras:dungeon/treasure_vitalidad
                tesoroProvisionMonedas: 40
                tesoroProvisionCargas: 1
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
                  # The floor deal and the gamble (PISOS 66). gangaProbabilidad is the BASE chance a
                  # shop shows one discounted slot; future economy items raise a run's discount level
                  # toward always-on (Steam-Sale style). The gamble (caja_sorpresa) draws lootCaja,
                  # its own steady table. pisoPremium is the floor from which fenix/seguro stock in.
                  gangaProbabilidad: 40
                  gangaDescuentoPct: 30
                  lootCaja: teras:dungeon/gamble
                  pisoPremium: 3
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
                  # La deuda: what a loan costs over the cash price, what settling early takes off
                  # the face value, and how many floors of not paying bring a Cobrador. At two, the
                  # two-floor Expedicion never sees one - a ruling, not an oversight (PISOS 63e).
                  interesDeudaPct: 50
                  descuentoSaldoPct: 20
                  pisosHastaCobrador: 2
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
                  seal_restored: minecraft:block.beacon.activate
                  descent_tick: minecraft:block.note_block.hat
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

    /**
     * The generation config a floor is built with: the compiled-in defaults, with the server's own
     * curve laid over them. Every generate() call site goes through this, so the curve is read from
     * one place and the built-in table stays the fallback rather than a second source of truth.
     */
    public static es.boffmedia.teras.dungeon.gen.GenConfig genConfig() {
        return es.boffmedia.teras.dungeon.gen.GenConfig.defaults().withCurve(celdas, jitter);
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

    public static int desertionGraceSeconds() {
        return desertionGraceSeconds;
    }

    public static int buildTimeoutSeconds() {
        return buildTimeoutSeconds;
    }

    public static String sealBlock() {
        return sealBlock;
    }

    public static String sealRuneBlock() {
        return sealRuneBlock;
    }

    public static String sealRuneLitBlock() {
        return sealRuneLitBlock;
    }

    public static int descentSeconds() {
        return descentSeconds;
    }

    public static int sealDoorWidth() {
        return sealDoorWidth;
    }

    public static int sealDoorHeight() {
        return sealDoorHeight;
    }

    public static long clearReward() {
        return clearReward;
    }


    public static int deathPenaltyPct() {
        return deathPenaltyPct;
    }

    public static String treasureLootTable() {
        return treasureLootTable;
    }

    public static String secretLootTable() {
        return secretLootTable;
    }

    public static String superSecretLootTable() {
        return superSecretLootTable;
    }


    public static String bossLootTable() {
        return bossLootTable;
    }

    public static String devilLootTable() {
        return devilLootTable;
    }

    /**
     * La Orden's relic table. Its own rather than the devil's on purpose: her gift has to be
     * power-competitive with his — PISOS §63e, the reason a restoration-only Orden would have made
     * the moral fork a trap choice — while staying clear of the scarcity trio (§4.1), so it trades
     * in gear and healing and never in keys or petardos.
     */
    public static String ordenLootTable() {
        return ordenLootTable;
    }

    /** The fighter's stand: one gear piece, rarity leaning raro, and an occasional book (PISOS §66). */
    public static String treasureArmaLootTable() {
        return treasureArmaLootTable;
    }

    /** The survivor's stand: a greater potion and a smaller heal. */
    public static String treasureVitalidadLootTable() {
        return treasureVitalidadLootTable;
    }

    /** The merchant's stand: this many coins to the shared purse, before the stage scale. */
    public static int treasureProvisionCoins() {
        return treasureProvisionCoins;
    }

    /** …and this many wall charges with them. */
    public static int treasureProvisionCharges() {
        return treasureProvisionCharges;
    }

    /** Base chance (0–100) that a floor's shop features one discounted ganga slot. */
    public static int gangaChance() {
        return gangaChance;
    }

    /** How deep the ganga cuts, as a percentage off the sticker price. */
    public static int gangaDiscountPct() {
        return gangaDiscountPct;
    }

    /** The gamble slot's reward table — its own, so it can be steady (no epic jackpot). */
    public static String gambleLootTable() {
        return gambleLootTable;
    }

    /** The stage from which premium stock (fénix, seguro) enters the shop's planogram. */
    public static int shopPremiumStage() {
        return shopPremiumStage;
    }

    public static String crackBlock() {
        return crackBlock;
    }

    public static String spikeBlock() {
        return spikeBlock;
    }

    public static int curseDoorTollHearts() {
        return curseDoorTollHearts;
    }

    public static int marketSlots() {
        return marketSlots;
    }

    public static int marketReward() {
        return marketReward;
    }

    public static int purgePrice() {
        return purgePrice;
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

    /** What the loan costs over the cash price — the creditor's margin on <i>pedir prestado</i>. */
    public static int debtInterestPct() {
        return debtInterestPct;
    }

    /** Taken off the face value for settling early, which is the reason to seek him out again. */
    public static int debtSettleDiscountPct() {
        return debtSettleDiscountPct;
    }

    /**
     * Floors of unpaid debt before a Cobrador comes for it.
     *
     * <p>Two, which in <i>La Expedición</i> — the two-floor everyday format — means he never
     * arrives at all. That is a <b>ruling, not an oversight</b> (PISOS §63e): the debt dies with the
     * run rather than growing an exit-collection or a format-scaled collector, so the deuda is a
     * Descenso-shaped mechanic by choice. Do not "fix" it in a later pass.</p>
     */
    public static int debtFloorsToCollect() {
        return debtFloorsToCollect;
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
