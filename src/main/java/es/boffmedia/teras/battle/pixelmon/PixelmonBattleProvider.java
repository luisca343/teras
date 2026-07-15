package es.boffmedia.teras.battle.pixelmon;

import com.pixelmonmod.pixelmon.api.battles.BattleResults;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.export.PokemonConverterFactory;
import com.pixelmonmod.pixelmon.api.pokemon.export.exception.PokemonImportException;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.EVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.api.pokemon.stats.PermanentStats;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleSet;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.TeamSelectionRegistry;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.api.BattleProvider;
import es.boffmedia.teras.battle.config.BattleConfig;
import es.boffmedia.teras.battle.config.BattleMode;
import es.boffmedia.teras.battle.lifecycle.BattleOutcomeHandler;
import es.boffmedia.teras.battle.model.TeamMember;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** {@link BattleProvider} for Pixelmon 9.3.16. Only linked when the {@code pixelmon} mod is present. */
public class PixelmonBattleProvider implements BattleProvider {

    @Override
    public String engineId() {
        return "pixelmon";
    }

    @Override
    public void startConfigBattle(ServerPlayer player, BattleConfig config) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        if (party == null) {
            Teras.LOGGER.error("Cannot start battle '{}': player party unavailable", config.getNombreArchivo());
            return;
        }

        int teamLevel = config.calculateTeamLevel(party.getHighestLevel());
        List<Pokemon> team = capTeam(buildTeam(config.getTeamPaste(), teamLevel), config.getRivalTeamSize());
        if (team.isEmpty()) {
            Teras.LOGGER.error("Cannot start battle '{}': rival team is empty", config.getNombreArchivo());
            return;
        }

        if (config.healBeforeStart()) {
            party.heal();
        }

        BattleParticipant rival = config.esEntrenador()
                ? PixelmonTrainerFactory.buildTrainer(player, config, team, teamLevel)
                : new WildPixelmonParticipant(team.get(0));

        BattleType type = toBattleType(config.getBattleMode());
        List<Entity> spawned = spawnedEntities(rival);

        // A trainer battle has a spawned rival entity for the native preview screen; wild encounters and
        // preview-off battles start directly (the chat reveal is the fallback the screen can't do).
        if (config.hasPreview() && rival.getEntity() != null) {
            startWithNativePreview(player, config, rival, type, spawned);
            return;
        }
        if (config.hasPreview()) {
            sendRivalTeamPreview(player, config.getNombre(), team);
        }
        startBattle(player, config,
                new BattleParticipant[]{new PlayerParticipant(player)},
                new BattleParticipant[]{rival},
                type,
                spawned);
    }

    @Override
    public void startMultiBattle(ServerPlayer player, BattleConfig partner, BattleConfig rival1, BattleConfig rival2) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        if (party == null) {
            Teras.LOGGER.error("Cannot start multi battle: player party unavailable");
            return;
        }
        int playerLevel = party.getHighestLevel();

        EntityParticipant partnerNpc = buildTrainerFromConfig(player, partner, playerLevel);
        EntityParticipant rivalNpc1 = buildTrainerFromConfig(player, rival1, playerLevel);
        EntityParticipant rivalNpc2 = buildTrainerFromConfig(player, rival2, playerLevel);
        if (partnerNpc == null || rivalNpc1 == null || rivalNpc2 == null) {
            Teras.LOGGER.error("Cannot start multi battle: a participant's team was empty");
            return;
        }

        if (partner.healBeforeStart()) {
            party.heal();
        }

        // 2v2 doubles: [player, partner] vs [rival1, rival2]. Outcome/rewards use rival1.
        startBattle(player, rival1,
                new BattleParticipant[]{new PlayerParticipant(player), partnerNpc},
                new BattleParticipant[]{rivalNpc1, rivalNpc2},
                BattleType.DOUBLE,
                spawnedEntities(partnerNpc, rivalNpc1, rivalNpc2));
    }

    private EntityParticipant buildTrainerFromConfig(ServerPlayer player, BattleConfig config, int playerLevel) {
        int teamLevel = config.calculateTeamLevel(playerLevel);
        List<Pokemon> team = capTeam(buildTeam(config.getTeamPaste(), teamLevel), config.getRivalTeamSize());
        if (team.isEmpty()) {
            return null;
        }
        return PixelmonTrainerFactory.buildTrainer(player, config, team, teamLevel);
    }

    /** Trims a rival team to its configured size (right side of {@code tamanoEquipos}). */
    private static List<Pokemon> capTeam(List<Pokemon> team, int max) {
        if (max <= 0 || team.size() <= max) {
            return team;
        }
        return new ArrayList<>(team.subList(0, max));
    }

    private static List<Entity> spawnedEntities(BattleParticipant... participants) {
        List<Entity> entities = new ArrayList<>();
        for (BattleParticipant p : participants) {
            if (p instanceof EntityParticipant && p.getEntity() != null) {
                entities.add(p.getEntity());
            }
        }
        return entities;
    }

    private void startBattle(ServerPlayer player, BattleConfig outcomeConfig,
                             BattleParticipant[] teamOne, BattleParticipant[] teamTwo, BattleType type,
                             List<Entity> spawnedEntities) {
        String label = outcomeConfig.getNombreArchivo();
        try {
            BattleBuilder builder = BattleBuilder.builder()
                    .teamOne(teamOne)
                    .teamTwo(teamTwo)
                    .setBattleType(type)
                    .endHandler(buildEndHandler(player, outcomeConfig, spawnedEntities));
            applyClauses(builder, outcomeConfig);
            builder.start(player.level().registryAccess())
                    .whenComplete((bc, err) -> {
                        if (err != null) {
                            Teras.LOGGER.error("Battle '{}' failed to start", label, err);
                            discard(spawnedEntities); // end handler won't fire
                        }
                    });
        } catch (Exception e) {
            Teras.LOGGER.error("Error starting battle '{}'", label, e);
            discard(spawnedEntities);
        }
    }

    /**
     * Opens Pixelmon's native team-preview screen. Its internal build drops the battle type (defaults
     * to SINGLE), gimmick flags and our end handler, so we stash them keyed by the player for
     * {@code TeamSelectionMixin} to re-apply. Trainer/entity rivals only; wild uses the chat reveal.
     */
    private void startWithNativePreview(ServerPlayer player, BattleConfig config, BattleParticipant rival,
                                        BattleType type, List<Entity> spawnedEntities) {
        String label = config.getNombreArchivo();
        try {
            Holder<BattleRuleSet> rules = selectionRules(player, config);
            Consumer<BattleBuilder> customizer = builder -> {
                builder.setBattleType(type);
                applyClauses(builder, config);
                builder.endHandler(buildEndHandler(player, config, spawnedEntities));
            };
            TerasTeamPreview.stash(player.getUUID(), new TerasTeamPreview.PendingBattle(
                    customizer, config.allowsMega(), config.allowsDynamax()));

            TeamSelectionRegistry.builder()
                    .members(player, rival.getEntity())
                    .showOpponentTeam()
                    .closeable()
                    .rules(rules)
                    .cancelConsumer(ts -> {
                        TerasTeamPreview.discard(player.getUUID());
                        discard(spawnedEntities);
                    })
                    .start();
        } catch (Exception e) {
            Teras.LOGGER.error("Error opening team preview for '{}'", label, e);
            TerasTeamPreview.discard(player.getUUID());
            discard(spawnedEntities);
        }
    }

    /**
     * AG ruleset capped to the player's team size ({@code max = playerTeamSize},
     * {@code min = playerActiveCount}). Only caps the player's selection; the rival is pre-trimmed and
     * the mixin fields all of it. Player cap needs the screen, so it applies only with preview on.
     */
    private static Holder<BattleRuleSet> selectionRules(ServerPlayer player, BattleConfig config) {
        BattleRuleSet ag = BattleRuleSet.getAG(player.level().registryAccess()).value();
        int max = Math.max(1, Math.min(6, config.getPlayerTeamSize()));
        int min = Math.max(1, Math.min(config.getPlayerActiveCount(), max));
        BattleRuleSet capped = new BattleRuleSet(
                ag.name(), ag.description(), ag.icon(), min, max, ag.rules());
        return Holder.direct(capped);
    }

    /** Applies {@code normas} as battle-wide clause context keys. */
    private static void applyClauses(BattleBuilder builder, BattleConfig config) {
        List<String> clauses = config.getClauses();
        if (clauses.contains("bag")) {
            builder.set(ContextKeys.BAG_CLAUSE, true);
        }
        if (clauses.contains("sleep")) {
            builder.set(ContextKeys.SLEEP_CLAUSE, true);
        }
        if (clauses.contains("forfeit")) {
            builder.set(ContextKeys.CANNOT_FORFEIT, true);
        }
    }

    private BiConsumer<BattleEndEvent, BattleController> buildEndHandler(
            ServerPlayer player, BattleConfig config, List<Entity> spawnedEntities) {
        return (event, controller) -> {
            boolean won = event.getResult(player)
                    .map(r -> r == BattleResults.VICTORY).orElse(false);
            List<TeamMember> team1 = new ArrayList<>();
            List<TeamMember> team2 = new ArrayList<>();
            captureTeams(controller, player.getUUID(), team1, team2);
            BattleOutcomeHandler.onConfigBattleEnd(player, config, won, captureLog(controller), team1, team2);
            discard(spawnedEntities);
        };
    }

    /**
     * Fills {@code team1} with the player's own battle Pokémon and {@code team2} with every opposing
     * participant's, both as engine-neutral {@link TeamMember}s for the SmartRotom report. Ally
     * participants on the player's side (multi battles) are skipped so {@code team1} matches the
     * reported player name. Never throws.
     */
    private static void captureTeams(BattleController controller, UUID playerId,
                                     List<TeamMember> team1, List<TeamMember> team2) {
        try {
            if (controller == null || controller.participants == null) {
                return;
            }
            int playerTeam = -1;
            for (BattleParticipant p : controller.participants) {
                if (playerId.equals(p.getUniqueId())) {
                    playerTeam = p.team;
                    break;
                }
            }
            for (BattleParticipant p : controller.participants) {
                if (p.team == playerTeam) {
                    if (playerId.equals(p.getUniqueId())) {
                        addMembers(p, team1);
                    }
                } else {
                    addMembers(p, team2);
                }
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to capture Pixelmon teams: {}", e.toString());
        }
    }

    private static void addMembers(BattleParticipant participant, List<TeamMember> out) {
        if (participant.allPokemon == null) {
            return;
        }
        for (PixelmonWrapper wrapper : participant.allPokemon) {
            if (wrapper == null) {
                continue;
            }
            Pokemon pokemon = wrapper.getOriginalPokemon();
            if (pokemon != null) {
                out.add(toMember(pokemon));
            }
        }
    }

    /** Native stat order [HP, ATK, DEF, SPA, SPD, SPE], matching the DTO's arrays. */
    private static final BattleStatsType[] STAT_ORDER = {
            BattleStatsType.HP, BattleStatsType.ATTACK, BattleStatsType.DEFENSE,
            BattleStatsType.SPECIAL_ATTACK, BattleStatsType.SPECIAL_DEFENSE, BattleStatsType.SPEED};

    private static TeamMember toMember(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        String speciesName = species != null ? species.getName() : "";
        String form = pokemon.getFormName();
        return new TeamMember(
                pokemon.getDex(),
                pokemon.getNature() != null ? capitalize(pokemon.getNature().getSerializedName()) : "",
                speciesName,
                isBaseForm(form) ? "" : form,
                pokemon.getPalette() != null ? pokemon.getPalette().getName() : "none",
                pokemon.getNickname() != null ? pokemon.getNickname().getString() : speciesName,
                pokemon.getPokemonLevel(),
                heldItemName(pokemon.getHeldItem()),
                pokemon.getAbility() != null ? pokemon.getAbility().getName() : "",
                moveNames(pokemon.getMoveset()),
                statList(pokemon.getIVs()),
                statList(pokemon.getEVs()),
                permanentStats(pokemon.getStats()));
    }

    private static List<String> moveNames(Moveset moveset) {
        List<String> moves = new ArrayList<>();
        if (moveset != null && moveset.attacks != null) {
            for (Attack attack : moveset.attacks) {
                if (attack != null && attack.getActualMove() != null) {
                    moves.add(attack.getActualMove().getAttackName());
                }
            }
        }
        return moves;
    }

    private static List<Integer> statList(IVStore ivs) {
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(ivs != null ? ivs.getStat(stat) : 0);
        }
        return out;
    }

    private static List<Integer> statList(EVStore evs) {
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(evs != null ? evs.getStat(stat) : 0);
        }
        return out;
    }

    private static List<Integer> permanentStats(PermanentStats stats) {
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(stats != null ? stats.get(stat) : 0);
        }
        return out;
    }

    private static String heldItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Pixelmon reports a base/no form as {@code null}, {@code ""}, or {@code "base"/"normal"}. */
    private static boolean isBaseForm(String form) {
        if (form == null || form.isBlank()) {
            return true;
        }
        String f = form.toLowerCase(Locale.ROOT);
        return f.equals("base") || f.equals("normal");
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Pixelmon's human-readable action log as replay text (not Showdown protocol). Never throws. */
    private static String captureLog(BattleController controller) {
        try {
            if (controller == null || controller.battleLog == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (var action : controller.battleLog.getAllActions()) {
                if (action != null) {
                    action.appendLog(sb);
                    sb.append('\n');
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to capture Pixelmon battle log: {}", e.toString());
            return null;
        }
    }

    /** Chat reveal of the rival team (species + level) — the preview fallback for wild battles. */
    private static void sendRivalTeamPreview(ServerPlayer player, String rivalName, List<Pokemon> team) {
        String name = (rivalName == null || rivalName.isBlank()) ? "Rival" : rivalName;
        MessageHelper.enviarMensaje(player, "§6Equipo de §e" + name + "§6:");
        for (Pokemon p : team) {
            MessageHelper.enviarMensaje(player,
                    "§7• §f" + p.getDisplayName().getString() + " §8Nv." + p.getPokemonLevel());
        }
    }

    private static void discard(List<Entity> entities) {
        for (Entity e : entities) {
            if (e != null && !e.isRemoved()) {
                e.discard();
            }
        }
    }

    private static BattleType toBattleType(BattleMode mode) {
        switch (mode) {
            case DOUBLE:   return BattleType.DOUBLE;
            case TRIPLE:   return BattleType.TRIPLE;
            case ROTATION: return BattleType.ROTATION;
            case HORDE:    return BattleType.HORDE;
            case RAID:     return BattleType.RAID;
            default:       return BattleType.SINGLE;
        }
    }

    /** Parses a PokePaste into Pixelmon {@link Pokemon} at {@code teamLevel}; empty list on failure. */
    static List<Pokemon> buildTeam(String paste, int teamLevel) {
        if (paste == null || paste.isBlank()) {
            return List.of();
        }
        try {
            List<Pokemon> team = PokemonConverterFactory.importText(Arrays.asList(paste.split("\n")));
            if (team != null) {
                for (Pokemon p : team) {
                    p.setLevel(teamLevel);
                }
                return team;
            }
        } catch (PokemonImportException e) {
            Teras.LOGGER.error("Failed to import Pixelmon team from paste: {}", e.getMessage());
        }
        return List.of();
    }
}
