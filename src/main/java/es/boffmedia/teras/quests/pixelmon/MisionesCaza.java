package es.boffmedia.teras.quests.pixelmon;

import com.pixelmonmod.pixelmon.api.battles.BattleResults;
import com.pixelmonmod.pixelmon.api.events.BeatWildPixelmonEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.raids.EndRaidEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.Teras;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestCategory;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.List;
import java.util.Map;

/**
 * Hunt quests: advances CustomNPCs quest objectives when a player defeats or catches Pokémon.
 * Port of the 1.16.5 {@code MisionesCaza}. Needs both Pixelmon and CustomNPCs — registered only when
 * {@code QuestBridge.isHuntAvailable()} (see {@code QuestRegistrar}).
 *
 * <p>Objectives are matched by their own text: everything before the first {@code ':'} is the
 * condition, which is either a species name ({@code "Pikachu: 0/5"}) or a type
 * ({@code "tipo FUEGO: 0/10"}). This text-parsing contract is preserved from 1.16.5 — the quests on
 * the live server are authored against it.</p>
 *
 * <h2>Ported from a version that never ran</h2>
 * The 1.16.5 class annotated {@code @Mod.EventBusSubscriber} — which registers the <i>class</i>, so
 * only static handlers are picked up — but declared every handler as an <b>instance</b> method. None
 * of them were ever subscribed. The handlers here are static and explicitly registered, so this is the
 * first version that actually fires; treat its behaviour as new rather than as a regression risk.
 *
 * <h2>Pixelmon 9.3.16 API changes</h2>
 * <ul>
 *   <li>{@code BeatTrainerEvent} no longer exists — trainer defeat is rebuilt on
 *       {@link BattleEndEvent} (see {@link #onBattleEnd}).</li>
 *   <li>The {@code Element} enum became the datapack-registered {@code Type}, reached as
 *       {@code Holder<Type>} and compared against a {@code ResourceKey}.</li>
 *   <li>{@code CaptureEvent.getPokemon()} returns a {@code Pokemon} rather than a {@code PixelmonEntity},
 *       and {@code RaidData} exposes {@code getPokemon()} instead of {@code getSpecies()}/{@code getForm()}.</li>
 * </ul>
 */
public final class MisionesCaza {
    private MisionesCaza() {}

    private static final String CATEGORY_DEFEAT = "DERROTA";
    private static final String CATEGORY_CAPTURE = "CAPTURA";

    /** The condition prefix marking a type objective rather than a species one. */
    private static final String TYPE_CONDITION = "tipo";

    /** Spanish type names as authored in quest objectives, mapped to Pixelmon's type registry keys. */
    private static final Map<String, ResourceKey<Type>> TYPES_BY_SPANISH_NAME = Map.ofEntries(
            Map.entry("NORMAL", Type.NORMAL),
            Map.entry("FUEGO", Type.FIRE),
            Map.entry("AGUA", Type.WATER),
            Map.entry("PLANTA", Type.GRASS),
            Map.entry("ELECTRICO", Type.ELECTRIC),
            Map.entry("HIELO", Type.ICE),
            Map.entry("LUCHA", Type.FIGHTING),
            Map.entry("VENENO", Type.POISON),
            Map.entry("TIERRA", Type.GROUND),
            Map.entry("VOLADOR", Type.FLYING),
            Map.entry("PSIQUICO", Type.PSYCHIC),
            Map.entry("BICHO", Type.BUG),
            Map.entry("ROCA", Type.ROCK),
            Map.entry("FANTASMA", Type.GHOST),
            Map.entry("DRAGON", Type.DRAGON),
            Map.entry("SINIESTRO", Type.DARK),
            Map.entry("ACERO", Type.STEEL),
            Map.entry("HADA", Type.FAIRY));

    // ---- Pixelmon event handlers ----

    @SubscribeEvent
    public static void onBeatWild(BeatWildPixelmonEvent event) {
        for (PixelmonWrapper pokemon : event.wpp.allPokemon) {
            advance(CATEGORY_DEFEAT, pokemon.getForm(), event.player);
        }
    }

    @SubscribeEvent
    public static void onCapture(CaptureEvent.SuccessfulCapture event) {
        // SuccessfulRaidCapture extends SuccessfulCapture, so this fires for raid catches too; the
        // dedicated raid handler below already counts those. 1.16.5 had both handlers and no guard,
        // so a raid catch would have been counted twice had any of it been wired up.
        if (event instanceof CaptureEvent.SuccessfulRaidCapture) return;
        advance(CATEGORY_CAPTURE, event.getPokemon().getForm(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onRaidCapture(CaptureEvent.SuccessfulRaidCapture event) {
        Pokemon pokemon = event.getRaidPokemon();
        if (pokemon == null) return;
        advance(CATEGORY_CAPTURE, pokemon.getForm(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onRaidEnd(EndRaidEvent event) {
        if (!event.didRaidersWin() || event.getRaid() == null) return;
        Pokemon pokemon = event.getRaid().getPokemon();
        if (pokemon == null || event.getRaidParticipant() == null) return;
        ServerPlayer player = event.getRaidParticipant().getWrapper().getPlayerOwner();
        advance(CATEGORY_DEFEAT, pokemon.getForm(), player);
    }

    /**
     * Trainer defeat, replacing the removed {@code BeatTrainerEvent}: for every player who won, credit
     * each Pokémon on the losing side.
     *
     * <p>Only {@link EntityParticipant} losers count — that's Pixelmon's trainer/NPC participant. Wild
     * and raid participants are excluded because {@link #onBeatWild} and {@link #onRaidEnd} already
     * cover them, and this event fires for those battles too.</p>
     */
    @SubscribeEvent
    public static void onBattleEnd(BattleEndEvent event) {
        Map<BattleParticipant, BattleResults> results = event.getResults();
        if (results == null) return;

        List<BattleParticipant> defeatedTrainers = results.entrySet().stream()
                .filter(e -> e.getValue() == BattleResults.DEFEAT)
                .map(Map.Entry::getKey)
                .filter(p -> p instanceof EntityParticipant)
                .toList();
        if (defeatedTrainers.isEmpty()) return;

        for (Map.Entry<BattleParticipant, BattleResults> entry : results.entrySet()) {
            if (entry.getValue() != BattleResults.VICTORY) continue;
            if (!(entry.getKey().getEntity() instanceof ServerPlayer player)) continue;
            for (BattleParticipant trainer : defeatedTrainers) {
                for (PixelmonWrapper pokemon : trainer.allPokemon) {
                    advance(CATEGORY_DEFEAT, pokemon.getForm(), player);
                }
            }
        }
    }

    // ---- Quest advancement ----

    /**
     * Advances every objective of {@code player}'s active quests in {@code categoryName} that this
     * Pokémon satisfies.
     */
    private static void advance(String categoryName, Stats form, ServerPlayer player) {
        if (player == null || form == null) return;
        try {
            IQuestCategory category = findCategory(categoryName);
            PlayerWrapper<?> wrapper = new PlayerWrapper<>(player);
            if (category == null) {
                // 1.16.5 told the player; keep it — it means the server's quests are misconfigured.
                wrapper.message("No existe la categoría de misiones " + categoryName);
                return;
            }
            String species = form.getParentSpecies() != null ? form.getParentSpecies().getName() : null;
            if (species == null) return;

            for (IQuest quest : wrapper.getActiveQuests()) {
                if (quest.getCategory() == null
                        || !quest.getCategory().getName().equalsIgnoreCase(categoryName)) continue;
                for (IQuestObjective objective : quest.getObjectives(wrapper)) {
                    if (!matches(objective, form, species)) continue;
                    if (tryAdvance(objective)) {
                        player.sendSystemMessage(
                                Component.literal("Has avanzado en la misión " + quest.getName()));
                    }
                }
            }
        } catch (Exception e) {
            // A quest hunt must never break a battle or a capture.
            Teras.LOGGER.warn("Failed advancing '{}' hunt quests for {}: {}",
                    categoryName, player.getGameProfile().getName(), e.toString());
        }
    }

    /** Whether {@code objective}'s condition is satisfied by this Pokémon's species or types. */
    private static boolean matches(IQuestObjective objective, Stats form, String species) {
        String text = objective.getText();
        if (text == null || text.isEmpty()) return false;
        String condition = text.split(":")[0].trim();

        if (condition.toLowerCase().contains(TYPE_CONDITION)) {
            String[] parts = condition.split(" ");
            if (parts.length < 2) return false;
            ResourceKey<Type> required = TYPES_BY_SPANISH_NAME.get(parts[1].toUpperCase());
            if (required == null) {
                Teras.LOGGER.warn("Quest objective '{}' names an unknown type '{}'", text, parts[1]);
                return false;
            }
            return form.getTypes().stream().anyMatch(type -> type.is(required));
        }
        return species.equalsIgnoreCase(condition);
    }

    /**
     * Bumps progress by one, returning whether it moved. 1.16.5 messaged the player that they'd
     * advanced even when the objective was already full (and separately that it was complete); here a
     * full objective is simply left alone.
     */
    private static boolean tryAdvance(IQuestObjective objective) {
        if (objective.getProgress() >= objective.getMaxProgress()) return false;
        objective.setProgress(objective.getProgress() + 1);
        return true;
    }

    private static IQuestCategory findCategory(String name) {
        for (IQuestCategory category : NpcAPI.Instance().getQuests().categories()) {
            if (category.getName().equalsIgnoreCase(name)) return category;
        }
        return null;
    }
}
