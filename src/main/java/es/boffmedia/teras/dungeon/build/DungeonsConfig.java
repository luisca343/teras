package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DoorStyle;
import es.boffmedia.teras.dungeon.model.RoomType;
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
 *
 * <h2>One file, one domain per record</h2>
 *
 * <p>This class is the <b>facade</b>: it owns the YAML file, the shipped template and the load
 * order, and every {@code DungeonsConfig.thing()} call site in the mod still reads exactly as it
 * did. What it no longer owns is eighty loose static fields covering fifteen unrelated domains —
 * each block of the file is a record ({@link CoinConfig}, {@link ShopConfig}, {@link LootConfig},
 * …) that knows its own defaults and how to read itself, so adding a knob touches one small file
 * instead of a reset method, a load method and a field list three hundred lines apart.</p>
 *
 * <p>The YAML format is untouched by the split. A config on disk reads identically.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonsConfig {
    private DungeonsConfig() {}

    private static GenerationConfig generation;
    private static CoinConfig coins;
    private static ShopConfig shop;
    private static LootConfig loot;
    private static CombatConfig combat;
    private static ChallengeConfig challenge;
    private static SacrificeConfig sacrifice;
    private static ArcadeConfig arcade;
    private static DevilConfig devil;
    private static ChestConfig chests;
    private static ParkourConfig parkour;
    private static RoomsConfig rooms;
    private static RunConfig run;
    private static DoorConfig doors;
    private static SoundConfig sounds;

    /**
     * How likely each curse is per floor, before the piso's own {@code maldiciones} filter it. A
     * curse every eligible piso at that depth refuses simply cannot occur there — the intended
     * consequence, not a gap.
     */
    private static final Map<es.boffmedia.teras.dungeon.model.Curse, Double> CURSE_CHANCES =
            new LinkedHashMap<>();

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
                validate();
                return;
            }
            YamlConfig yaml = YamlConfig.read(path);
            // config.yml was the one dungeon config never stamped, so every default added to the
            // template since a server's first boot has been invisible on it — silently, which is the
            // exact failure ConfigVersion exists to announce. It is unstamped rather than stale when
            // the key is missing, and both read as "older than current".
            ConfigVersion.warnIfStale("config.yml", yaml.integer(ConfigVersion.KEY, 0),
                    "Copia los bloques que falten de la plantilla, o borra el archivo para "
                            + "regenerarlo (perderás tus ediciones).");

            generation = GenerationConfig.read(yaml, generation);
            coins = CoinConfig.read(yaml, coins);
            shop = ShopConfig.read(yaml, shop);
            loot = LootConfig.read(yaml, loot);
            combat = CombatConfig.read(yaml, combat);
            challenge = ChallengeConfig.read(yaml, challenge);
            sacrifice = SacrificeConfig.read(yaml, sacrifice);
            arcade = ArcadeConfig.read(yaml, arcade);
            devil = DevilConfig.read(yaml, devil);
            chests = ChestConfig.read(yaml, chests);
            parkour = ParkourConfig.read(yaml, parkour);
            rooms = RoomsConfig.read(yaml, rooms);
            run = RunConfig.read(yaml, run);
            doors = DoorConfig.read(yaml, doors);
            sounds = SoundConfig.read(yaml, sounds);
            readCurseChances(yaml);

            Teras.LOGGER.info("Dungeons: config loaded from {}", path);
            // Said out loud because every symptom of it being off is "the game behaves as it always
            // did", which nobody reads as a setting.
            Teras.LOGGER.info("Dungeons: rebuilt combat is {} ({}). Diagnose in game with"
                            + " /teras dungeon combate",
                    combat.enabled() ? "ON" : "OFF",
                    yaml.section("combate").has("activado")
                            ? "set in config.yml"
                            : "no 'combate' block in config.yml — using the shipped default");
            validate();
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: failed to load config, using defaults: {}", e.toString());
        }
    }

    private static void readCurseChances(YamlConfig yaml) {
        YamlConfig curseBlock = yaml.section("maldiciones");
        for (es.boffmedia.teras.dungeon.model.Curse curse
                : es.boffmedia.teras.dungeon.model.Curse.values()) {
            String key = curse.name().toLowerCase(java.util.Locale.ROOT);
            int pct = curseBlock.integer(key,
                    (int) Math.round(CURSE_CHANCES.getOrDefault(curse, 0.0) * 100));
            CURSE_CHANCES.put(curse, Math.max(0, Math.min(100, pct)) / 100.0);
        }
    }

    /**
     * Says out loud what a hand-edited file got wrong.
     *
     * <p>Warnings rather than refusals, and deliberately so: this config is edited live by an
     * operator, and a value that is merely nonsensical (a max below its min, a reward table left
     * blank) is a floor that plays oddly, not a server that must not boot. What it must never do is
     * fail <i>silently</i> — a coin range inverted by a typo pays nothing at all, and there is
     * nothing in the game that reads as "you swapped two numbers in config.yml".</p>
     */
    static void validate() {
        range("monedas.normal", coins.normalMin(), coins.normalMax());
        range("monedas.miniJefe", coins.miniBossMin(), coins.miniBossMax());
        range("monedas.jefe", coins.bossMin(), coins.bossMax());
        range("sacrificio.monedas", sacrifice.coinsMin(), sacrifice.coinsMax());
        positive("maxSlots", generation.maxSlots());
        positive("tamanoSala", generation.roomSize());
        positive("alturaSala", generation.roomHeight());
        positive("anchoPuerta", generation.doorWidth());
        positive("altoPuerta", generation.doorHeight());
        positive("monedas.cambioPesos", coins.toPesos());
        positive("maxGrupo", run.maxParty());
        for (Map.Entry<String, String> table : loot.tables().entrySet()) {
            if (table.getValue() == null || table.getValue().isBlank()) {
                Teras.LOGGER.warn("Dungeons: '{}' is blank — the room it pays for will drop nothing",
                        table.getKey());
            }
        }
        if (generation.celdas().isEmpty()) {
            Teras.LOGGER.warn("Dungeons: 'generacion.celdas' is empty — no floor has a cell budget");
        }
    }

    private static void range(String key, int min, int max) {
        if (max < min) {
            Teras.LOGGER.warn("Dungeons: '{}Max' ({}) is below '{}Min' ({}) — the range is empty and "
                    + "pays nothing", key, max, key, min);
        }
    }

    private static void positive(String key, int value) {
        if (value <= 0) {
            Teras.LOGGER.warn("Dungeons: '{}' is {} — it must be at least 1", key, value);
        }
    }

    private static void resetToDefaults() {
        generation = GenerationConfig.defaults();
        coins = CoinConfig.defaults();
        shop = ShopConfig.defaults();
        loot = LootConfig.defaults();
        combat = CombatConfig.defaults();
        challenge = ChallengeConfig.defaults();
        sacrifice = SacrificeConfig.defaults();
        arcade = ArcadeConfig.defaults();
        devil = DevilConfig.defaults();
        chests = ChestConfig.defaults();
        parkour = ParkourConfig.defaults();
        rooms = RoomsConfig.defaults();
        run = RunConfig.defaults();
        doors = DoorConfig.defaults();
        sounds = SoundConfig.defaults();
        CURSE_CHANCES.clear();
    }

    private static String renderTemplate() {
        // The stamp is written, never read back into anything: it exists so that a file created
        // today can be told apart from one created before a default changed.
        return "version: " + ConfigVersion.CURRENT + "\n" + """
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
                  # El plomo takes the party's parkour for the floor (see 'parcool' below). Rarer
                  # than the other two: it is the one that changes how you MOVE, not how far.
                  plomo: 6
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
                # Days a return point for a player who was offline when their run ended is kept
                # before it is swept. 0 keeps them forever.
                diasParaExpirarRetornos: 30
                # Run loop: what seals doors in combat, and the wall a secret room hides behind.
                bloqueSello: teras:reja
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
                # Doors. The opening itself is anchoPuerta x altoPuerta and is NOT touched here —
                # widening it would move the reserved apron in every room ever authored. What this
                # section dresses is the wall around it: a ring in the wall plane (free, the audit
                # exempts the wall) and, with relieve, two jambs and a lintel standing one block
                # proud of it in the two columns beside the opening, which are outside the walkable
                # band. A door costs no floor space and still reads as a door.
                #
                # A doorway wears the frame of the MORE SPECIAL of the two rooms it joins, on both
                # faces — so it is a sign from the corridor and a place-marker from inside. Secret
                # and super-secret are never dressed: a frame on a secret gives it away.
                #
                # 'normal' is the fallback for a piso that declares no 'puertas' of its own; the
                # per-piso block in pisos/<id>.json is what makes an ordinary door look like ITS
                # floor. 'especiales' is dungeon-wide on purpose: treasure has to read as treasure
                # on a piso that did not exist when the player learned what it looks like.
                #
                #   marco   frame: the ring's sides and top, the jambs, the lintel beam
                #   acento  the keystone and the ring's corners
                #   luz     lamp on each jamb top; "" for an unlit door
                #   umbral  the threshold course under the opening
                #   porton  panel of a gate held shut all floor (trato, Orden); "" for none
                #   marca   that gate's centre block
                #   alta    a taller frame with a stepped lintel — the boss, and the sello
                #
                # ticksCierre is how long a sealing gate takes to fall, top course first.
                puertas:
                  relieve: true
                  ticksCierre: 6
                  normal:
                    marco: minecraft:polished_andesite
                    acento: minecraft:chiseled_tuff
                    luz: minecraft:shroomlight
                    umbral: minecraft:andesite
                  especiales:
                    # Copper: the rooms about money.
                    treasure:
                      marco: minecraft:waxed_cut_copper
                      acento: minecraft:gold_block
                      luz: minecraft:waxed_copper_bulb[lit=true,powered=false]
                      umbral: minecraft:waxed_cut_copper
                    shop:
                      marco: minecraft:stripped_dark_oak_wood
                      acento: minecraft:waxed_cut_copper
                      luz: minecraft:lantern
                      umbral: minecraft:waxed_cut_copper
                    arcade:
                      marco: minecraft:waxed_exposed_cut_copper
                      acento: minecraft:amethyst_block
                      luz: minecraft:verdant_froglight
                      umbral: minecraft:waxed_exposed_cut_copper
                    # Blackstone: the danger you walk into on purpose.
                    challenge:
                      marco: minecraft:polished_blackstone_bricks
                      acento: minecraft:chiseled_polished_blackstone
                      luz: minecraft:soul_lantern
                      umbral: minecraft:polished_blackstone
                    sacrifice:
                      marco: minecraft:red_nether_bricks
                      acento: minecraft:nether_wart_block
                      luz: ""
                      umbral: minecraft:red_nether_bricks
                    curse:
                      marco: minecraft:blackstone
                      acento: minecraft:dripstone_block
                      luz: ""
                      umbral: minecraft:blackstone
                    mini_boss:
                      marco: minecraft:polished_blackstone_bricks
                      acento: minecraft:polished_blackstone
                      luz: minecraft:soul_lantern
                      umbral: minecraft:polished_blackstone
                    boss:
                      marco: minecraft:polished_blackstone_bricks
                      acento: minecraft:crying_obsidian
                      luz: minecraft:soul_lantern
                      umbral: minecraft:polished_blackstone
                      alta: true
                    # The three promises, one of a kind each.
                    devil_deal:
                      marco: minecraft:polished_blackstone
                      acento: minecraft:crying_obsidian
                      luz: ""
                      umbral: minecraft:polished_blackstone
                      porton: minecraft:polished_blackstone
                      marca: teras:marca_pacto
                    orden:
                      marco: minecraft:smooth_quartz
                      acento: minecraft:gold_block
                      luz: ""
                      umbral: minecraft:smooth_quartz
                      porton: minecraft:smooth_quartz
                      marca: teras:marca_orden
                    exit:
                      marco: minecraft:polished_basalt
                      acento: minecraft:amethyst_block
                      luz: minecraft:amethyst_block
                      umbral: minecraft:polished_basalt
                      alta: true
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
                # Contenedores de corazón que cuesta una muerte. Es la única condición de derrota
                # de la mazmorra: quien se queda sin contenedores queda fuera de la expedición
                # (espectador, mirando por los ojos de un compañero), y si no queda nadie en pie la
                # partida se pierde — salís sin bolsa y sin conversión a ₽. El equipo extraído se
                # conserva siempre. 0 lo desactiva y devuelve la mazmorra a no poder perderse.
                contenedoresPorMuerte: 2
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
                # --- El combate reconstruido (ROGUELIKE §4) ---------------------------------
                # El daño no lo calcula Minecraft: lo calcula Teras, para jugadores y enemigos por
                # igual, y con él vienen la esquiva (tecla V), el encadenado de golpes ligeros
                # (clic izquierdo) y el aplomo, que rompe la guardia y deja al enemigo abierto.
                # El clic derecho NO es un ataque: sigue siendo el escudo, los artilugios, los
                # cofres, las tiendas y los PNJ.
                # Los precios del calabozo — las púas del sacrificio, el peaje de la maldición, las
                # trampas de los cofres, la caída — NO pasan por aquí: siguen doliendo lo mismo por
                # muy bien equipado que vayas, que es justo lo que es un precio.
                # Ponlo en false para devolver el combate a Minecraft sin tocar nada más.
                combate:
                  activado: true
                # --- El plomo (ParCool) -----------------------------------------------------
                # The 'plomo' curse takes the party's parkour away for a floor. ParCool is NOT a
                # Teras dependency: this drives its own limitation commands as the server, so a
                # server without it plays the floor unmodified and logs nothing louder than debug.
                # The action names below belong to ParCool and are the one part that can rot -
                # check them with /parcool limitation get global and fix them HERE, not in code.
                parcool:
                  habilitado: true
                  comandosQuitar:
                    - parcool limitation set individual of %player% boolean WallJump false
                    - parcool limitation set individual of %player% boolean HorizontalWallRun false
                    - parcool limitation set individual of %player% boolean CatLeap false
                    - parcool limitation set individual of %player% boolean ClingToCliff false
                    - parcool limitation set individual of %player% boolean Dive false
                    - parcool limitation set individual of %player% boolean PoleClimb false
                  comandosDevolver:
                    - parcool limitation set individual of %player% boolean WallJump true
                    - parcool limitation set individual of %player% boolean HorizontalWallRun true
                    - parcool limitation set individual of %player% boolean CatLeap true
                    - parcool limitation set individual of %player% boolean ClingToCliff true
                    - parcool limitation set individual of %player% boolean Dive true
                    - parcool limitation set individual of %player% boolean PoleClimb true
                # --- Chests -----------------------------------------------------------------
                # A reward that may stand in ANY room, priced in something other than coins
                # (PISOS 69). Kinds are authored on the marker: cofre:libre / sellado / puas /
                # trampa / proeza. Every price is charged at the CLICK, never at the approach:
                # ParCool is in the pack, so a ledge costs stamina, not access. proeza is the
                # exception that proves it - no price at all, placed where only parkour reaches.
                cofres:
                  loot: teras:dungeon/treasure
                  lootProeza: teras:dungeon/boss
                  danoPuas: 4
                  danoTrampa: 6
                  probTrampaPct: 35
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

    // --- the domain records, for anything that wants a whole block at once ---------------------

    public static GenerationConfig generation() {
        return generation;
    }

    public static CoinConfig coins() {
        return coins;
    }

    public static ShopConfig shop() {
        return shop;
    }

    public static LootConfig loot() {
        return loot;
    }

    public static CombatConfig combat() {
        return combat;
    }

    // --- and the flat accessors every call site in the mod already uses ------------------------

    public static int roomSize() {
        return generation.roomSize();
    }

    public static int roomHeight() {
        return generation.roomHeight();
    }

    public static int doorWidth() {
        return generation.doorWidth();
    }

    public static int doorHeight() {
        return generation.doorHeight();
    }

    /**
     * The generation config a floor is built with: the compiled-in defaults, with the server's own
     * curve laid over them. Every generate() call site goes through this, so the curve is read from
     * one place and the built-in table stays the fallback rather than a second source of truth.
     */
    public static es.boffmedia.teras.dungeon.gen.GenConfig genConfig() {
        return es.boffmedia.teras.dungeon.gen.GenConfig.defaults()
                .withCurve(generation.celdas(), generation.jitter());
    }

    public static Map<es.boffmedia.teras.dungeon.model.Curse, Double> curseChances() {
        return Map.copyOf(CURSE_CHANCES);
    }

    public static String dimension() {
        return generation.dimension();
    }

    public static int slotY() {
        return generation.slotY();
    }

    public static int slotSpacing() {
        return generation.slotSpacing();
    }

    public static int maxSlots() {
        return generation.maxSlots();
    }

    public static int desertionGraceSeconds() {
        return run.desertionGraceSeconds();
    }

    public static int buildTimeoutSeconds() {
        return run.buildTimeoutSeconds();
    }

    /** Days a pending return point is kept before the boot/login sweep drops it; 0 never expires. */
    public static int returnExpiryDays() {
        return run.returnExpiryDays();
    }

    public static String sealBlock() {
        return rooms.sealBlock();
    }

    public static String sealRuneBlock() {
        return rooms.sealRuneBlock();
    }

    public static String sealRuneLitBlock() {
        return rooms.sealRuneLitBlock();
    }

    public static int descentSeconds() {
        return generation.descentSeconds();
    }

    public static int sealDoorWidth() {
        return rooms.sealDoorWidth();
    }

    public static int sealDoorHeight() {
        return rooms.sealDoorHeight();
    }

    public static long clearReward() {
        return coins.clearReward();
    }

    public static int deathPenaltyPct() {
        return coins.bankDeathPenaltyPct();
    }

    public static String treasureLootTable() {
        return loot.treasure();
    }

    public static String secretLootTable() {
        return loot.secret();
    }

    public static String superSecretLootTable() {
        return loot.superSecret();
    }

    public static String bossLootTable() {
        return loot.boss();
    }

    public static String devilLootTable() {
        return loot.devil();
    }

    /**
     * La Orden's relic table. Its own rather than the devil's on purpose: her gift has to be
     * power-competitive with his — PISOS §63e, the reason a restoration-only Orden would have made
     * the moral fork a trap choice — while staying clear of the scarcity trio (§4.1), so it trades
     * in gear and healing and never in keys or petardos.
     */
    public static String ordenLootTable() {
        return loot.orden();
    }

    /** The fighter's stand: one gear piece, rarity leaning raro, and an occasional book (PISOS §66). */
    public static String treasureArmaLootTable() {
        return loot.treasureArma();
    }

    /** The survivor's stand: a greater potion and a smaller heal. */
    public static String treasureVitalidadLootTable() {
        return loot.treasureVitalidad();
    }

    /** The merchant's stand: this many coins to the shared purse, before the stage scale. */
    public static int treasureProvisionCoins() {
        return loot.provisionCoins();
    }

    /** …and this many wall charges with them. */
    public static int treasureProvisionCharges() {
        return loot.provisionCharges();
    }

    /**
     * Whether Teras computes damage instead of Minecraft, and whether the esquiva answers its key.
     *
     * <p>Off returns every <b>fight</b> to vanilla: the damage pipeline, the light chain, the heavy, the
     * dodge, poise and the stat panel all stand down. It is deliberately not a master switch for the
     * dungeon — the healing lockdown, the tolls, and the Pokémon ban ({@code DungeonPokemonGuard}) are
     * loadout and economy rules rather than combat rules, and they stay on. Said explicitly because this
     * javadoc used to promise that off "leaves every shipped fight exactly as it was" while one guard in
     * the combat package quietly ignored the flag.</p>
     */
    public static boolean combatEnabled() {
        return combat.enabled();
    }

    /** Whether el plomo tries to drive ParCool at all. Off makes the curse a no-op, not an error. */
    public static boolean parkourLimitsEnabled() {
        return parkour.enabled();
    }

    /** The commands that take the moveset away, with {@code %player%} still in them. */
    public static List<String> parkourLimitCommands() {
        return List.copyOf(parkour.limitCommands());
    }

    /** …and the ones that give it back. */
    public static List<String> parkourRestoreCommands() {
        return List.copyOf(parkour.restoreCommands());
    }

    /** What a chest pays. Every kind but proeza draws this one. */
    public static String chestLootTable() {
        return loot.chest();
    }

    /** What a proeza chest pays — the one placed where only parkour reaches. */
    public static String chestProezaLootTable() {
        return loot.chestProeza();
    }

    /** Health a spiked chest takes from each claimant, before the floor's scale. */
    public static float chestSpikeDamage() {
        return chests.spikeDamage();
    }

    /** Health a doubtful chest takes when the gamble goes wrong. */
    public static float chestTrapDamage() {
        return chests.trapDamage();
    }

    /** Chance (0–100) that a doubtful chest bites instead of paying double. */
    public static int chestTrapChancePct() {
        return chests.trapChancePct();
    }

    /** Base chance (0–100) that a floor's shop features one discounted ganga slot. */
    public static int gangaChance() {
        return shop.gangaChance();
    }

    /** How deep the ganga cuts, as a percentage off the sticker price. */
    public static int gangaDiscountPct() {
        return shop.gangaDiscountPct();
    }

    /** The gamble slot's reward table — its own, so it can be steady (no epic jackpot). */
    public static String gambleLootTable() {
        return shop.gambleLootTable();
    }

    /** The stage from which premium stock (fénix, seguro) enters the shop's planogram. */
    public static int shopPremiumStage() {
        return shop.premiumStage();
    }

    public static String crackBlock() {
        return rooms.crackBlock();
    }

    public static String spikeBlock() {
        return rooms.spikeBlock();
    }

    public static int curseDoorTollHearts() {
        return rooms.curseDoorTollHearts();
    }

    /** Whether door frames stand proud of the wall, or are inlaid flat into it. */
    public static boolean doorRelief() {
        return doors.relief();
    }

    /** How long a sealing gate takes to fall, top course first. */
    public static int sealCloseTicks() {
        return doors.sealCloseTicks();
    }

    /** The ordinary frame, for a piso that declares no {@code puertas} of its own. */
    public static DoorStyle doorStyle() {
        return doors.normal();
    }

    /** The dungeon-wide frame for a door into {@code type}, or null when it wears its piso's. */
    public static DoorStyle doorStyle(RoomType type) {
        return doors.specials().get(type);
    }

    public static int marketSlots() {
        return shop.marketSlots();
    }

    public static int marketReward() {
        return shop.marketReward();
    }

    public static int purgePrice() {
        return shop.purgePrice();
    }

    public static boolean backendPostEnabled() {
        return run.backendPostEnabled();
    }

    public static int coinsNormalMin() {
        return coins.normalMin();
    }

    public static int coinsNormalMax() {
        return coins.normalMax();
    }

    public static int coinsMiniBossMin() {
        return coins.miniBossMin();
    }

    public static int coinsMiniBossMax() {
        return coins.miniBossMax();
    }

    public static int coinsBossMin() {
        return coins.bossMin();
    }

    public static int coinsBossMax() {
        return coins.bossMax();
    }

    public static int coinStageScalingPct() {
        return coins.stageScalingPct();
    }

    public static double coinPickupRadius() {
        return coins.pickupRadius();
    }

    public static int coinDeathPenaltyPct() {
        return coins.deathPenaltyPct();
    }

    public static int coinToPesos() {
        return coins.toPesos();
    }

    /** Roll weight of a shop stock kind; 0 keeps it out of the pool entirely. */
    public static int shopWeight(String kind) {
        return shop.weight(kind);
    }

    /** Base price in coins, before per-floor scaling. */
    public static int shopPrice(String kind) {
        return shop.price(kind);
    }

    public static int challengeWaves() {
        return challenge.waves();
    }

    public static int challengeExtraWaveStage() {
        return challenge.extraWaveStage();
    }

    public static int challengeWaveGrowthPct() {
        return challenge.waveGrowthPct();
    }

    public static int challengeReward() {
        return challenge.reward();
    }

    public static float sacrificeDamage() {
        return sacrifice.damage();
    }

    public static int sacrificeBaseChancePct() {
        return sacrifice.baseChancePct();
    }

    public static int sacrificeStepChancePct() {
        return sacrifice.stepChancePct();
    }

    public static int sacrificeCoinsMin() {
        return sacrifice.coinsMin();
    }

    public static int sacrificeCoinsMax() {
        return sacrifice.coinsMax();
    }

    public static int arcadePrice() {
        return arcade.price();
    }

    public static int arcadeBreakChancePct() {
        return arcade.breakChancePct();
    }

    public static int devilCoinPrice() {
        return devil.coinPrice();
    }

    public static int devilHeartPrice() {
        return devil.heartPrice();
    }

    /** What the loan costs over the cash price — the creditor's margin on <i>pedir prestado</i>. */
    public static int debtInterestPct() {
        return devil.debtInterestPct();
    }

    /** Taken off the face value for settling early, which is the reason to seek him out again. */
    public static int debtSettleDiscountPct() {
        return devil.debtSettleDiscountPct();
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
        return devil.floorsToCollect();
    }

    public static int maxParty() {
        return run.maxParty();
    }

    public static int entranceRadius() {
        return run.entranceRadius();
    }

    public static int containersLostPerDeath() {
        return run.containersLostPerDeath();
    }

    /** Vanilla (or first-party) sound id for a cue; never null — unknown cues fall back silently. */
    public static String sound(String cue) {
        return sounds.cue(cue);
    }

    public static float soundVolume() {
        return sounds.volume();
    }
}
