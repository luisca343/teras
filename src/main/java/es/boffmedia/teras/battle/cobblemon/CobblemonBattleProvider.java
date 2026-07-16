package es.boffmedia.teras.battle.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.scheduling.SchedulingFunctionsKt;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.cobblemon.mod.common.pokemon.Species;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.api.BattleProvider;
import es.boffmedia.teras.battle.config.BattleConfig;
import es.boffmedia.teras.battle.config.BattleMode;
import es.boffmedia.teras.battle.config.ShowdownSet;
import es.boffmedia.teras.battle.config.ShowdownTeamParser;
import es.boffmedia.teras.battle.lifecycle.BattleOutcomeHandler;
import es.boffmedia.teras.battle.model.TeamMember;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link BattleProvider} for Cobblemon. Only linked when the {@code cobblemon} mod is present. Trainer
 * battles pit an NPC-backed {@link NPCBattleActor} (see {@link CobblemonTrainerFactory}) against the
 * player's {@link PlayerBattleActor}. Teams come from {@link ShowdownTeamParser} mapped onto
 * {@link PokemonProperties} (species, level, gender, shiny, ability, nature, tera, moves, EVs/IVs,
 * held item).
 */
public class CobblemonBattleProvider implements BattleProvider {

    /** Battles we started, keyed by Cobblemon battle id, awaiting their victory event. */
    private static final ConcurrentHashMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final AtomicBoolean SUBSCRIBED = new AtomicBoolean(false);

    /** @param npcs the trainer entities spawned for this battle, to discard once it ends */
    private record Pending(ServerPlayer player, BattleConfig config, List<NPCEntity> npcs) {}

    public CobblemonBattleProvider() {
        // Cobblemon has no per-battle end callback, so correlate the global battle events back via PENDING.
        if (SUBSCRIBED.compareAndSet(false, true)) {
            try {
                CobblemonEvents.BATTLE_VICTORY.subscribe(CobblemonBattleProvider::onVictory);
                CobblemonEvents.BATTLE_FLED.subscribe(CobblemonBattleProvider::onFled);
            } catch (Exception e) {
                Teras.LOGGER.error("Failed to subscribe to Cobblemon battle events", e);
            }
        }
    }

    @Override
    public String engineId() {
        return "cobblemon";
    }

    @Override
    public void startConfigBattle(ServerPlayer player, BattleConfig config) {
        if (config.healBeforeStart()) {
            Cobblemon.INSTANCE.getStorage().getParty(player).heal();
        }
        List<BattlePokemon> fullPlayerTeam = playerBattleTeam(player);
        if (fullPlayerTeam.isEmpty()) {
            Teras.LOGGER.error("Cannot start Cobblemon battle '{}': player has no Pokémon",
                    config.getNombreArchivo());
            return;
        }

        int teamLevel = config.calculateTeamLevel(highestLevel(fullPlayerTeam));
        List<BattlePokemon> playerTeam = capTeam(fullPlayerTeam, config.getPlayerTeamSize());
        List<BattlePokemon> rivalTeam = capTeam(
                buildTrainerTeam(config.getTeamPaste(), teamLevel), config.getRivalTeamSize());
        if (rivalTeam.isEmpty()) {
            Teras.LOGGER.error("Cannot start Cobblemon battle '{}': rival team is empty",
                    config.getNombreArchivo());
            return;
        }

        NPCBattleActor rival = CobblemonTrainerFactory.buildTrainer(player, trainerName(config), rivalTeam,
                teamLevel, CobblemonTrainerFactory.spotNear(player, RIVAL_DISTANCE, 0), player.position());
        if (rival == null) {
            return;
        }
        BattleSide playerSide = new BattleSide(new PlayerBattleActor(player.getUUID(), playerTeam));
        BattleSide rivalSide = new BattleSide(rival);

        startBattle(player, config, battleFormat(config, toFormat(config.getBattleMode())),
                playerSide, rivalSide, List.of(rival.getEntity()));
    }

    @Override
    public void startMultiBattle(ServerPlayer player, BattleConfig partner, BattleConfig rival1, BattleConfig rival2) {
        if (partner.healBeforeStart()) {
            Cobblemon.INSTANCE.getStorage().getParty(player).heal();
        }
        List<BattlePokemon> fullPlayerTeam = playerBattleTeam(player);
        if (fullPlayerTeam.isEmpty()) {
            Teras.LOGGER.error("Cannot start Cobblemon multi battle: player has no Pokémon");
            return;
        }
        int level = highestLevel(fullPlayerTeam);

        List<BattlePokemon> playerTeam = capTeam(fullPlayerTeam, rival1.getPlayerTeamSize());
        List<BattlePokemon> partnerTeam = capTeam(
                buildTrainerTeam(partner.getTeamPaste(), partner.calculateTeamLevel(level)), partner.getRivalTeamSize());
        List<BattlePokemon> rival1Team = capTeam(
                buildTrainerTeam(rival1.getTeamPaste(), rival1.calculateTeamLevel(level)), rival1.getRivalTeamSize());
        List<BattlePokemon> rival2Team = capTeam(
                buildTrainerTeam(rival2.getTeamPaste(), rival2.calculateTeamLevel(level)), rival2.getRivalTeamSize());
        if (partnerTeam.isEmpty() || rival1Team.isEmpty() || rival2Team.isEmpty()) {
            Teras.LOGGER.error("Cannot start Cobblemon multi battle: a participant's team was empty");
            return;
        }

        // The partner lines up beside the player and faces the rivals with them; the rivals face back.
        Vec3 rivalArea = CobblemonTrainerFactory.spotNear(player, RIVAL_DISTANCE, 0);
        NPCBattleActor partnerActor = CobblemonTrainerFactory.buildTrainer(player, trainerName(partner),
                partnerTeam, partner.calculateTeamLevel(level),
                CobblemonTrainerFactory.spotNear(player, 0, -ALLY_SPACING), rivalArea);
        NPCBattleActor rival1Actor = CobblemonTrainerFactory.buildTrainer(player, trainerName(rival1),
                rival1Team, rival1.calculateTeamLevel(level),
                CobblemonTrainerFactory.spotNear(player, RIVAL_DISTANCE, ALLY_SPACING / 2), player.position());
        NPCBattleActor rival2Actor = CobblemonTrainerFactory.buildTrainer(player, trainerName(rival2),
                rival2Team, rival2.calculateTeamLevel(level),
                CobblemonTrainerFactory.spotNear(player, RIVAL_DISTANCE, -ALLY_SPACING / 2), player.position());
        List<NPCEntity> npcs = spawnedEntities(partnerActor, rival1Actor, rival2Actor);
        if (partnerActor == null || rival1Actor == null || rival2Actor == null) {
            discard(npcs); // a half-spawned line-up would leave orphan NPCs standing
            return;
        }

        BattleSide playerSide = new BattleSide(
                new PlayerBattleActor(player.getUUID(), playerTeam), partnerActor);
        BattleSide rivalSide = new BattleSide(rival1Actor, rival2Actor);

        // Outcome/rewards use rival1.
        startBattle(player, rival1,
                battleFormat(rival1, BattleFormat.Companion.getGEN_9_MULTI()), playerSide, rivalSide, npcs);
    }

    /* ---- Helpers ---- */

    /** Blocks between the player and the trainers they face. */
    private static final double RIVAL_DISTANCE = 6.0;
    /** Blocks between two trainers standing on the same side. */
    private static final double ALLY_SPACING = 3.0;

    private void startBattle(ServerPlayer player, BattleConfig outcomeConfig, BattleFormat format,
                             BattleSide side1, BattleSide side2, List<NPCEntity> npcs) {
        try {
            BattleStartResult result = BattleRegistry.startBattle(format, side1, side2, false);
            if (result instanceof SuccessfulBattleStart success) {
                PENDING.put(success.getBattle().getBattleId(), new Pending(player, outcomeConfig, npcs));
            } else {
                Teras.LOGGER.error("Cobblemon battle '{}' did not start: {}",
                        outcomeConfig.getNombreArchivo(), result);
                discard(npcs);
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Error starting Cobblemon battle '{}'", outcomeConfig.getNombreArchivo(), e);
            discard(npcs);
        }
    }

    private static List<NPCEntity> spawnedEntities(NPCBattleActor... actors) {
        List<NPCEntity> npcs = new ArrayList<>(actors.length);
        for (NPCBattleActor actor : actors) {
            if (actor != null) {
                npcs.add(actor.getEntity());
            }
        }
        return npcs;
    }

    /** Removes the battle's trainer NPCs from the world. Never throws. */
    private static void discard(List<NPCEntity> npcs) {
        for (NPCEntity npc : npcs) {
            try {
                npc.discard();
            } catch (Exception e) {
                Teras.LOGGER.warn("Failed to discard Cobblemon trainer NPC: {}", e.toString());
            }
        }
    }

    /** Seconds the trainers linger after the battle, so their win/lose animation and their Pokémon's
     *  recall aren't cut short by the entity vanishing. */
    private static final float DISCARD_DELAY_SECONDS = 3f;

    private static void discardAfterBattle(List<NPCEntity> npcs) {
        try {
            SchedulingFunctionsKt.afterOnServer(DISCARD_DELAY_SECONDS, () -> {
                discard(npcs);
                return Unit.INSTANCE;
            });
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to schedule Cobblemon trainer NPC cleanup: {}", e.toString());
            discard(npcs);
        }
    }

    /** BATTLE_FLED handler: no outcome to award, but the trainer NPCs still have to go. */
    private static void onFled(BattleFledEvent event) {
        Pending pending = PENDING.remove(event.getBattle().getBattleId());
        if (pending != null) {
            discardAfterBattle(pending.npcs());
        }
    }

    /** BATTLE_VICTORY handler: correlate the finished battle to its config and award the outcome. */
    private static void onVictory(BattleVictoryEvent event) {
        PokemonBattle battle = event.getBattle();
        Pending pending = PENDING.remove(battle.getBattleId());
        if (pending == null) {
            return; // not one of ours
        }
        discardAfterBattle(pending.npcs());
        boolean won = false;
        for (BattleActor actor : event.getWinners()) {
            if (actor.getUuid().equals(pending.player().getUUID())) {
                won = true;
                break;
            }
        }
        ServerPlayer player = livePlayer(pending.player());
        if (player != null) {
            List<TeamMember> team1 = new ArrayList<>();
            List<TeamMember> team2 = new ArrayList<>();
            captureTeams(battle, player.getUUID(), team1, team2);
            BattleOutcomeHandler.onConfigBattleEnd(player, pending.config(), won, captureLog(battle), team1, team2);
        }
    }

    /**
     * Fills {@code team1} with the player's own team and {@code team2} with every actor on the
     * opposing side(s). Allies on the player's side (multi battles) are skipped. Never throws.
     */
    private static void captureTeams(PokemonBattle battle, UUID playerId,
                                     List<TeamMember> team1, List<TeamMember> team2) {
        try {
            for (BattleSide side : battle.getSides()) {
                BattleActor[] actors = side.getActors();
                boolean playerSide = false;
                for (BattleActor actor : actors) {
                    if (playerId.equals(actor.getUuid())) {
                        playerSide = true;
                        break;
                    }
                }
                for (BattleActor actor : actors) {
                    if (playerSide) {
                        if (playerId.equals(actor.getUuid())) {
                            addMembers(actor, team1);
                        }
                    } else {
                        addMembers(actor, team2);
                    }
                }
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to capture Cobblemon teams: {}", e.toString());
        }
    }

    private static void addMembers(BattleActor actor, List<TeamMember> out) {
        for (BattlePokemon bp : actor.getPokemonList()) {
            if (bp == null) {
                continue;
            }
            Pokemon pokemon = bp.getOriginalPokemon();
            if (pokemon != null) {
                out.add(toMember(pokemon));
            }
        }
    }

    /** {@link TeamMember} stat order. */
    private static final Stats[] STAT_ORDER = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

    private static TeamMember toMember(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        String speciesName = species != null ? species.getName() : "";
        FormData form = pokemon.getForm();
        return new TeamMember(
                species != null ? species.getNationalPokedexNumber() : 0,
                pokemon.getNature() != null ? capitalize(pokemon.getNature().getName().getPath()) : "",
                speciesName,
                isBaseForm(form) ? "" : form.getName(),
                pokemon.getShiny() ? "shiny" : "none",
                pokemon.getNickname() != null ? pokemon.getNickname().getString() : speciesName,
                pokemon.getLevel(),
                heldItemName(pokemon.getHeldItem$common()),
                pokemon.getAbility() != null ? pokemon.getAbility().getName() : "",
                moveNames(pokemon.getMoveSet()),
                statList(pokemon.getIvs()),
                statList(pokemon.getEvs()),
                computedStats(pokemon));
    }

    private static List<String> moveNames(MoveSet moveSet) {
        List<String> moves = new ArrayList<>();
        if (moveSet != null) {
            for (Move move : moveSet.getMoves()) {
                if (move != null) {
                    moves.add(move.getName());
                }
            }
        }
        return moves;
    }

    private static List<Integer> statList(PokemonStats stats) {
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (Stat stat : STAT_ORDER) {
            out.add(stats != null ? stats.getOrDefault(stat) : 0);
        }
        return out;
    }

    private static List<Integer> computedStats(Pokemon pokemon) {
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (Stat stat : STAT_ORDER) {
            out.add(pokemon.getStat(stat));
        }
        return out;
    }

    private static String heldItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Cobblemon names the default form {@code "Normal"}. */
    private static boolean isBaseForm(FormData form) {
        if (form == null || form.getName() == null || form.getName().isBlank()) {
            return true;
        }
        return form.getName().equalsIgnoreCase("normal");
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The battle's native Showdown protocol messages as the replay. Never throws. */
    private static String captureLog(PokemonBattle battle) {
        try {
            List<String> messages = battle.getShowdownMessages();
            return messages == null || messages.isEmpty() ? null : String.join("\n", messages);
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to capture Cobblemon battle log: {}", e.toString());
            return null;
        }
    }

    /** Re-resolves a live server player from the stored reference (guards against reconnects). */
    private static ServerPlayer livePlayer(ServerPlayer stored) {
        if (stored.getServer() == null) {
            return null;
        }
        return stored.getServer().getPlayerList().getPlayer(stored.getUUID());
    }

    /** The player's current party as Showdown battle Pokémon. */
    private static List<BattlePokemon> playerBattleTeam(ServerPlayer player) {
        List<BattlePokemon> team = new ArrayList<>();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (Pokemon pokemon : party) {
            if (pokemon != null) {
                team.add(BattlePokemon.Companion.playerOwned(pokemon));
            }
        }
        return team;
    }

    /** Trims a team to {@code max} Pokémon (a side of {@code tamanoEquipos}); {@code max<=0} or a
     *  smaller team is returned unchanged. */
    private static List<BattlePokemon> capTeam(List<BattlePokemon> team, int max) {
        if (max <= 0 || team.size() <= max) {
            return team;
        }
        return new ArrayList<>(team.subList(0, max));
    }

    /** Adds the config's {@code normas} as Showdown clauses on a copy of the base format (the
     *  {@code GEN_9_*} presets are shared singletons — never mutate them). */
    private static BattleFormat battleFormat(BattleConfig config, BattleFormat base) {
        Set<String> clauses = showdownClauses(config);
        if (clauses.isEmpty()) {
            return base;
        }
        Set<String> rules = new HashSet<>(base.getRuleSet());
        rules.addAll(clauses);
        return base.copy(base.getMod(), base.getBattleType(), rules, base.getGen(), base.getAdjustLevel());
    }

    /** Maps Teras clause names to Pokémon Showdown rule identifiers; unmapped names are ignored. */
    private static Set<String> showdownClauses(BattleConfig config) {
        Set<String> rules = new HashSet<>();
        for (String norma : config.getClauses()) {
            switch (norma) {
                case "sleep", "sleepclause" -> rules.add("Sleep Clause Mod");
                case "species", "speciesclause" -> rules.add("Species Clause");
                case "item", "itemclause" -> rules.add("Item Clause");
                case "evasion", "evasionclause" -> rules.add("Evasion Clause");
                case "ohko", "ohkoclause" -> rules.add("OHKO Clause");
                case "endless", "endlessbattle", "endlessbattleclause" -> rules.add("Endless Battle Clause");
                default -> { /* bag/forfeit and unknowns have no Showdown-rule equivalent */ }
            }
        }
        return rules;
    }

    /** Parses a PokePaste into Cobblemon battle Pokémon levelled to {@code teamLevel}. */
    private static List<BattlePokemon> buildTrainerTeam(String paste, int teamLevel) {
        List<BattlePokemon> team = new ArrayList<>();
        for (ShowdownSet set : ShowdownTeamParser.parse(paste)) {
            Pokemon pokemon = toCobblemonPokemon(set, teamLevel);
            if (pokemon != null) {
                team.add(BattlePokemon.Companion.safeCopyOf(pokemon));
            }
        }
        return team;
    }

    /** Maps a parsed Showdown set onto {@link PokemonProperties} and creates the Cobblemon Pokémon. */
    private static Pokemon toCobblemonPokemon(ShowdownSet set, int teamLevel) {
        try {
            PokemonProperties props = new PokemonProperties();
            props.setSpecies(showdownId(set.species));
            props.setLevel(teamLevel);
            if (set.shiny) {
                props.setShiny(Boolean.TRUE);
            }
            if ("M".equals(set.gender)) {
                props.setGender(Gender.MALE);
            } else if ("F".equals(set.gender)) {
                props.setGender(Gender.FEMALE);
            }
            if (set.ability != null) {
                props.setAbility(showdownId(set.ability));
            }
            if (set.nature != null) {
                props.setNature(showdownId(set.nature));
            }
            if (set.teraType != null) {
                props.setTeraType(showdownId(set.teraType));
            }
            if (!set.moves.isEmpty()) {
                List<String> moves = new ArrayList<>();
                for (String m : set.moves) {
                    moves.add(showdownId(m));
                }
                props.setMoves(moves);
            }
            applyStats(props, set);

            Pokemon pokemon = props.create();
            if (set.item != null && !set.item.isBlank()) {
                applyHeldItem(pokemon, set.item);
            }
            return pokemon;
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to build Cobblemon Pokémon '{}': {}", set.species, e.toString());
            return null;
        }
    }

    /** Showdown stat labels (as the parser stores them) → Cobblemon {@link Stats}. */
    private static final Map<String, Stats> STAT_BY_KEY = Map.of(
            "HP", Stats.HP,
            "ATK", Stats.ATTACK,
            "DEF", Stats.DEFENCE,
            "SPA", Stats.SPECIAL_ATTACK,
            "SPD", Stats.SPECIAL_DEFENCE,
            "SPE", Stats.SPEED);

    /**
     * Applies the parsed IVs/EVs onto {@code props}. Unspecified IVs default to 31 (the Showdown
     * convention); unspecified EVs stay 0.
     */
    private static void applyStats(PokemonProperties props, ShowdownSet set) {
        IVs ivs = new IVs();
        for (Stats stat : STAT_BY_KEY.values()) {
            ivs.set(stat, 31);
        }
        set.ivs.forEach((key, value) -> {
            Stats stat = STAT_BY_KEY.get(key);
            if (stat != null) {
                ivs.set(stat, value);
            }
        });
        props.setIvs(ivs);

        if (!set.evs.isEmpty()) {
            EVs evs = EVs.createEmpty();
            set.evs.forEach((key, value) -> {
                Stats stat = STAT_BY_KEY.get(key);
                if (stat != null) {
                    evs.set(stat, value);
                }
            });
            props.setEvs(evs);
        }
    }

    /** Sets the held item from a Showdown name ("Choice Scarf"), trying {@code cobblemon} then
     *  {@code minecraft} (or an explicit {@code ns:path}); unresolvable items are skipped. */
    private static void applyHeldItem(Pokemon pokemon, String itemName) {
        String norm = itemName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_").replaceAll("[^a-z0-9_:]", "");
        if (norm.isEmpty()) {
            return;
        }
        Item item;
        if (norm.contains(":")) {
            item = itemOrNull(ResourceLocation.tryParse(norm));
        } else {
            item = itemOrNull(ResourceLocation.fromNamespaceAndPath("cobblemon", norm));
            if (item == null) {
                item = itemOrNull(ResourceLocation.fromNamespaceAndPath("minecraft", norm));
            }
        }
        if (item != null) {
            pokemon.setHeldItem$common(new ItemStack(item));
        } else {
            Teras.LOGGER.warn("Cobblemon held item '{}' not resolvable; skipping", itemName);
        }
    }

    private static Item itemOrNull(ResourceLocation id) {
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** Highest level among the player's battle team, for the rival-level calculation. */
    private static int highestLevel(List<BattlePokemon> team) {
        int max = 1;
        for (BattlePokemon bp : team) {
            int lvl = bp.getOriginalPokemon().getLevel();
            if (lvl > max) {
                max = lvl;
            }
        }
        return max;
    }

    private static BattleFormat toFormat(BattleMode mode) {
        switch (mode) {
            case DOUBLE:
            case HORDE:
                return BattleFormat.Companion.getGEN_9_DOUBLES();
            case TRIPLE:
                return BattleFormat.Companion.getGEN_9_TRIPLES();
            default:
                return BattleFormat.Companion.getGEN_9_SINGLES();
        }
    }

    private static String trainerName(BattleConfig config) {
        return config.getNombre() == null || config.getNombre().isBlank() ? "Entrenador" : config.getNombre();
    }

    /** Normalises a display name to Cobblemon's Showdown id convention (lowercase, alphanumerics only). */
    private static String showdownId(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
