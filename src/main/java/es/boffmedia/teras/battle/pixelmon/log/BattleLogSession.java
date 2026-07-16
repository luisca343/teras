package es.boffmedia.teras.battle.pixelmon.log;

import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.pokemon.ability.Ability;
import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.DamageTypeEnum;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.AttackResult;
import com.pixelmonmod.pixelmon.battles.controller.log.MoveResults;
import com.pixelmonmod.pixelmon.battles.controller.log.SingleInstanceMoveResult;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.PokemonRelatedBattleAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.*;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.status.ElectricTerrain;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.Gravity;
import com.pixelmonmod.pixelmon.battles.status.GrassyTerrain;
import com.pixelmonmod.pixelmon.battles.status.Hail;
import com.pixelmonmod.pixelmon.battles.status.MagicRoom;
import com.pixelmonmod.pixelmon.battles.status.MistyTerrain;
import com.pixelmonmod.pixelmon.battles.status.PsychicTerrain;
import com.pixelmonmod.pixelmon.battles.status.Rainy;
import com.pixelmonmod.pixelmon.battles.status.Sandstorm;
import com.pixelmonmod.pixelmon.battles.status.Snow;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import com.pixelmonmod.pixelmon.battles.status.Sunny;
import com.pixelmonmod.pixelmon.battles.status.Terrain;
import com.pixelmonmod.pixelmon.battles.status.TrickRoom;
import com.pixelmonmod.pixelmon.battles.status.WonderRoom;
import es.boffmedia.teras.Teras;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a Pokémon Showdown replay log for a single Pixelmon {@link BattleController}, one
 * {@link BattleAction} at a time. Each live event flowing through {@code BattleLog.logEvent} (see
 * {@code mixin/BattleLogMixin}) is translated into Showdown protocol lines ({@code |move|},
 * {@code |-damage|}, {@code |switch|}, …) that the web replay viewer renders — this is NOT Pixelmon's
 * own human-readable {@code appendLog} debug text.
 *
 * <p>Ported from the 1.16.5 {@code TerasBattleLog} + {@code *ActionHandler} system and adapted to the
 * Pixelmon 9.3.16 action model: actions extending {@link PokemonRelatedBattleAction} expose the live
 * {@link PixelmonWrapper} via {@code getPokemon()}; the rest carry only a {@code pokemonName} String,
 * so positions are resolved against the controller's live {@code controlledPokemon} at event time
 * (which is why this runs live, not by polling {@code getAllActions()} at the end). Remaining
 * protected fields with no getter are read reflectively via {@link #field}.</p>
 */
public final class BattleLogSession {

    private static final char[] LETTERS = {'a', 'b', 'c', 'd', 'e', 'f'};

    private static final Map<StatusType, String> STATUS_IDS = statusIds();

    /** Status moves that report nothing when they fail (a re-used Protect, a redundant screen, …). */
    private static final Set<String> GUARDED_STATUS_MOVES = Set.of(
            "Protect", "Detect", "Endure", "King's Shield", "Spiky Shield", "Baneful Bunker",
            "Obstruct", "Quick Guard", "Wide Guard", "Crafty Shield", "Mat Block", "Feint",
            "Follow Me", "Rage Powder", "Ally Switch", "Helping Hand", "Tailwind", "Lucky Chant",
            "Safeguard", "Mist", "Light Screen", "Reflect", "Aurora Veil", "Brick Break", "Defog",
            "Haze", "Heal Bell", "Heal Pulse", "Memento");

    /** Showdown-reportable stat stages, in the order they are emitted. */
    private static final BattleStatsType[] BOOST_STATS = {
            BattleStatsType.ATTACK, BattleStatsType.DEFENSE, BattleStatsType.SPECIAL_ATTACK,
            BattleStatsType.SPECIAL_DEFENSE, BattleStatsType.SPEED,
            BattleStatsType.ACCURACY, BattleStatsType.EVASION};

    private final BattleController bc;
    private final List<String> lines = new ArrayList<>();
    /** position ("p1a") -> the {@code getPokemonName()} last emitted as active there; dedupes switches. */
    private final Map<String, String> activeName = new java.util.HashMap<>();
    /** names already reported fainted, so repeated damage sources don't emit a second {@code |faint|}. */
    private final Set<String> fainted = new java.util.HashSet<>();
    /** last seen stat stages per Pokémon, diffed to turn a StatChangeAction into boost/unboost. */
    private final Map<java.util.UUID, BattleStats> stageSnapshots = new java.util.HashMap<>();
    private boolean headerBuilt;

    public BattleLogSession(BattleController bc) {
        this.bc = bc;
    }

    /** Translates one live battle action into Showdown lines. Never throws (a broken line is skipped). */
    public void log(BattleAction action) {
        try {
            if (!headerBuilt) {
                appendHeader();
                headerBuilt = true;
            }
            dispatch(action);
        } catch (Exception e) {
            Teras.LOGGER.warn("Teras battle log: failed to translate {}: {}",
                    action.getClass().getSimpleName(), e.toString());
        }
    }

    /** The accumulated Showdown replay, or {@code null} if nothing was logged. */
    public String render() {
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private void line(String s) {
        lines.add(s);
    }

    /* ================================ Header ================================ */

    private void appendHeader() {
        List<BattleParticipant> participants = bc.participants;

        for (int i = 0; i < participants.size(); i++) {
            line("|player|p" + (i + 1) + "|" + participantName(participants.get(i)));
        }
        for (int i = 0; i < participants.size(); i++) {
            line("|teamsize|p" + (i + 1) + "|" + teamSize(participants.get(i)));
        }
        line("|gametype|" + gametype(participants));
        line("|gen|9");
        line("|tier|Circuito de Gimnasios de Teras");

        line("|clearpoke");
        for (int i = 0; i < participants.size(); i++) {
            for (PixelmonWrapper pw : participants.get(i).allPokemon) {
                if (pw != null) {
                    line("|poke|p" + (i + 1) + "|" + details(pw) + "|");
                }
            }
        }
        line("|teampreview");
        line("|start");

        // Lead switch-ins. Seeds activeName so the leads' own SwitchActions (if any) are deduped.
        for (int i = 0; i < participants.size(); i++) {
            List<PixelmonWrapper> active = participants.get(i).controlledPokemon;
            for (int slot = 0; slot < active.size(); slot++) {
                PixelmonWrapper pw = active.get(slot);
                if (pw != null) {
                    emitSwitch("p" + (i + 1) + LETTERS[slot], pw);
                }
            }
        }

        // Turn 1's TurnBeginAction is logged from the BattleController constructor, before any session
        // can be registered, so onTurnBegin never sees it.
        emitTurn(1);
    }

    private void emitTurn(int turn) {
        line("|turn|" + turn);
        line("|");
        line("|t:|" + System.currentTimeMillis() / 1000);
    }

    /* ============================== Dispatch =============================== */

    private void dispatch(BattleAction action) {
        if (action instanceof TurnBeginAction a)          onTurnBegin(a);
        else if (action instanceof AttackAction a)         onAttack(a);
        else if (action instanceof SwitchAction a)         onSwitch(a);
        else if (action instanceof DamagePokemonAction a)  onDamage(a);
        else if (action instanceof HealPokemonAction a)    onHeal(a);
        else if (action instanceof StatChangeAction a)     onStatChange(a);
        else if (action instanceof StatusAddAction a)      onStatusAdd(a);
        else if (action instanceof StatusRemoveAction a)   onStatusRemove(a);
        else if (action instanceof WeatherChangeAction a)  onWeatherChange(a);
        else if (action instanceof TerrainChangeAction a)  onTerrainChange(a);
        else if (action instanceof GlobalStatusAddAction a)    onGlobalStatusAdd(a);
        else if (action instanceof GlobalStatusRemoveAction a) onGlobalStatusRemove(a);
        else if (action instanceof MegaEvolveAction a)     onMegaEvolve(a);
        else if (action instanceof UltraBurstAction a)     onUltraBurst(a);
        else if (action instanceof EnterDynamaxAction a)   onEnterDynamax(a);
        else if (action instanceof ExitDynamaxAction a)    onExitDynamax(a);
        else if (action instanceof HeldItemChangeAction a) onHeldItemChange(a);
        else if (action instanceof ChangeAbilityAction a)  onChangeAbility(a);
        else if (action instanceof ChangeTypeAction a)     onChangeType(a);
        else if (action instanceof BagItemAction a)        onBagItem(a);
        else if (action instanceof TurnEndAction)          line("|upkeep");
        // BattleEndAction/BattleMessageAction/FleeAction/Select* carry no replay-visible content.
    }

    /* ============================== Handlers ============================== */

    private void onTurnBegin(TurnBeginAction action) {
        int turn = (int) field(action, "turn");
        emitTurn(turn + 1);
        resyncActive(); // catch forced faint-replacements that arrive without a SwitchAction
    }

    private void onAttack(AttackAction action) {
        PixelmonWrapper attacker = action.getPokemon();
        if (attacker == null) return;
        Attack attack = (Attack) field(action, "attack");
        MoveResults results = action.getMoveResults();
        if (attack == null || results == null) return;

        String move = attack.getActualMove().getAttackName();
        Set<PixelmonWrapper> targets = new LinkedHashSet<>(results.getTargetList());
        PixelmonWrapper first = targets.isEmpty() ? attacker : targets.iterator().next();

        StringBuilder moveLine = new StringBuilder("|move|")
                .append(posAndName(attacker)).append('|').append(move).append('|').append(posAndName(first));
        if (targets.size() > 1) {
            String slots = targets.stream().map(this::pos).reduce((x, y) -> x + "," + y).orElse("");
            moveLine.append("|[spread] ").append(slots);
        }
        AttackResult overall = results.getAttackResult();
        if (overall == AttackResult.charging) moveLine.append("|[still]");
        else if (overall == AttackResult.notarget) moveLine.append("|[notarget]");
        line(moveLine.toString());

        if (attack.getAttackCategory() == AttackCategory.STATUS) {
            onStatusMove(move, attacker, overall);
            return;
        }

        for (PixelmonWrapper target : targets) {
            if (target == null) continue;
            AttackResult result = resultFor(results, target);
            String targetName = posAndName(target);
            if (result == AttackResult.missed) {
                line("|-miss|" + posAndName(attacker) + "|" + targetName);
                continue;
            }
            if (result == AttackResult.failed || result == AttackResult.notarget) {
                line("|-fail|" + targetName);
                continue;
            }
            if (result == AttackResult.immune) {
                line("|-immune|" + targetName);
                continue;
            }
            emitDamage(target, targetName);
        }

        // Faints follow every damage line: Pixelmon logs PokemonFaintAction before the attack that
        // caused it, so emitting from that action instead would put |faint| ahead of its own |move|.
        for (PixelmonWrapper target : targets) {
            if (target != null && target.getHealth() <= 0) {
                emitFaint(target.getPokemonName());
            }
        }
    }

    /** Showdown effect lines for status moves that don't deal damage (protects, screens, rooms, …). */
    private void onStatusMove(String move, PixelmonWrapper user, AttackResult result) {
        switch (move) {
            case "Wonder Room", "Trick Room", "Magic Room" ->
                    line("|-fieldstart|move: " + move + "|[of] " + posAndName(user));
            default -> {
                if (GUARDED_STATUS_MOVES.contains(move) && result == AttackResult.failed) return;
                line("|-activate|" + posAndName(user) + "|move: " + move);
            }
        }
    }

    private void onSwitch(SwitchAction action) {
        // switchingTo is a defensive copy; resolve the live wrapper now occupying the slot by name.
        PixelmonWrapper incoming = liveByName(action.switchingTo.getPokemonName());
        if (incoming == null) return;
        emitSwitch(pos(incoming), incoming);
    }

    private void onDamage(DamagePokemonAction action) {
        DamageTypeEnum type = (DamageTypeEnum) field(action, "damageType");
        // Direct move damage is already emitted by onAttack; only indirect sources here.
        if (type == DamageTypeEnum.ATTACK || type == DamageTypeEnum.ATTACKFIXED || type == DamageTypeEnum.STRUGGLE) {
            return;
        }
        String name = (String) field(action, "pokemonName");
        PixelmonWrapper pw = liveByName(name);
        if (pw == null) return;
        int healthAfter = (int) field(action, "healthAfter");
        String from = (type == DamageTypeEnum.RECOIL || type == DamageTypeEnum.CRASH) ? "|[from] recoil" : "";
        String target = posAndName(pw);
        if (healthAfter <= 0) {
            line("|-damage|" + target + "|0 fnt" + from);
            emitFaint(name);
        } else {
            line("|-damage|" + target + "|" + healthAfter + "\\/" + pw.getMaxHealth() + from);
        }
    }

    private void onHeal(HealPokemonAction action) {
        // HealPokemonAction only carries the participant, not the Pokémon; attribute to its active lead.
        PixelmonWrapper pw = firstActiveOf((net.minecraft.network.chat.Component) field(action, "participantName"));
        if (pw == null) return;
        int healthAfter = (int) field(action, "healthAfter");
        line("|-heal|" + posAndName(pw) + "|" + healthAfter + "\\/" + pw.getMaxHealth());
    }

    /**
     * Emits stage deltas by diffing the Pokémon's live {@link BattleStats} against the last snapshot.
     * The action's own {@code oldStats}/{@code newStats} are raw stat totals, not stages, so they
     * cannot be used here. The first change seen for a Pokémon only records its baseline.
     */
    private void onStatChange(StatChangeAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        if (pw == null) return;
        BattleStats current = pw.getBattleStats();
        if (current == null) return;

        BattleStats previous = stageSnapshots.put(pw.getPokemonUUID(), new BattleStats(current));
        if (previous == null) return;

        String posName = posAndName(pw);
        for (BattleStatsType stat : BOOST_STATS) {
            int delta = current.getStage(stat) - previous.getStage(stat);
            if (delta == 0) continue;
            String kind = delta > 0 ? "boost" : "unboost";
            line("|-" + kind + "|" + posName + "|" + showdownStat(stat) + "|" + Math.abs(delta));
        }
    }

    private void onStatusAdd(StatusAddAction action) {
        StatusBase status = (StatusBase) field(action, "status");
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        if (status == null || pw == null) return;
        String posName = posAndName(pw);
        String id = STATUS_IDS.get(status.type);
        if (id != null) {
            line("|-status|" + posName + "|" + id);
        } else if (status.type == StatusType.Confusion) {
            line("|-start|" + posName + "|confusion");
        } else if (status.type == StatusType.Flinch) {
            line("|cant|" + posName + "|flinch");
        } else if (status.type == StatusType.ParadoxBoost && pw.getAbility() != null) {
            line("|-activate|" + posName + "|ability: " + pw.getAbility().getName());
        }
    }

    private void onStatusRemove(StatusRemoveAction action) {
        StatusBase status = (StatusBase) field(action, "status");
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        if (status == null || pw == null) return;
        String posName = posAndName(pw);
        if (status.type == StatusType.Confusion) {
            line("|-end|" + posName + "|confusion");
            return;
        }
        String id = STATUS_IDS.get(status.type);
        if (id != null) {
            line("|-curestatus|" + posName + "|" + id);
        }
    }

    private void onWeatherChange(WeatherChangeAction action) {
        GlobalStatusBase weather = (GlobalStatusBase) field(action, "newWeather");
        line("|-weather|" + weatherName(weather));
    }

    private void onTerrainChange(TerrainChangeAction action) {
        String newName = terrainName((Terrain) field(action, "newTerrain"));
        if (newName != null) {
            String of = action.getPokemon() != null ? "|[of] " + posAndName(action.getPokemon()) : "";
            line("|-fieldstart|move: " + newName + of);
            return;
        }
        String oldName = terrainName((Terrain) field(action, "oldTerrain"));
        if (oldName != null) {
            line("|-fieldend|move: " + oldName);
        }
    }

    private void onGlobalStatusAdd(GlobalStatusAddAction action) {
        GlobalStatusBase status = (GlobalStatusBase) field(action, "status");
        String name = addedFieldEffectName(status);
        if (name == null) return;
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        String of = pw != null ? "|[of] " + posAndName(pw) : "";
        line("|-fieldstart|move: " + name + of);
    }

    private void onGlobalStatusRemove(GlobalStatusRemoveAction action) {
        String name = fieldEffectName((GlobalStatusBase) field(action, "status"));
        if (name != null) {
            line("|-fieldend|move: " + name);
        }
    }

    private void onMegaEvolve(MegaEvolveAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        if (pw == null) return;
        String posName = posAndName(pw);
        line("|-mega|" + posName + "|" + pw.getSpecies().getName() + "|" + itemName(pw));
        line("|detailschange|" + posName + "|" + details(pw));
    }

    private void onUltraBurst(UltraBurstAction action) {
        PixelmonWrapper pw = action.getPokemon();
        if (pw == null) return;
        line("|-burst|" + posAndName(pw) + "|" + pw.getSpecies().getName() + "|" + itemName(pw));
    }

    private void onEnterDynamax(EnterDynamaxAction action) {
        PixelmonWrapper pw = action.getPokemon();
        if (pw == null) return;
        boolean gmax = (boolean) field(action, "gigantamax");
        line("|-start|" + posAndName(pw) + "|" + (gmax ? "Gigantamax" : "Dynamax"));
    }

    private void onExitDynamax(ExitDynamaxAction action) {
        PixelmonWrapper pw = action.getPokemon();
        if (pw == null) return;
        boolean gmax = (boolean) field(action, "gigantamax");
        line("|-end|" + posAndName(pw) + "|" + (gmax ? "Gigantamax" : "Dynamax"));
    }

    private void onHeldItemChange(HeldItemChangeAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        if (pw == null) return;
        // Pixelmon reports a cleared slot as a NoItem instance, not null.
        Object oldItem = presentItem(field(action, "oldItem"));
        Object newItem = presentItem(field(action, "newItem"));
        String posName = posAndName(pw);
        if (newItem != null) {
            line("|-item|" + posName + "|" + itemName(newItem));
        } else if (oldItem != null) {
            line("|-enditem|" + posName + "|" + itemName(oldItem));
        }
    }

    private void onChangeAbility(ChangeAbilityAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        Ability newAbility = (Ability) field(action, "newAbility");
        if (pw == null || newAbility == null) return;
        line("|-ability|" + posAndName(pw) + "|" + newAbility.getName());
    }

    @SuppressWarnings("unchecked")
    private void onChangeType(ChangeTypeAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "pokemonName"));
        List<net.minecraft.resources.ResourceKey<com.pixelmonmod.pixelmon.api.pokemon.type.Type>> types =
                (List<net.minecraft.resources.ResourceKey<com.pixelmonmod.pixelmon.api.pokemon.type.Type>>)
                        field(action, "newTypes");
        if (pw == null || types == null || types.isEmpty()) return;
        String joined = types.stream()
                .map(t -> t == null ? "???" : capitalize(t.location().getPath()))
                .reduce((x, y) -> x + "/" + y).orElse("");
        line("|-start|" + posAndName(pw) + "|typechange|" + joined);
    }

    private void onBagItem(BagItemAction action) {
        PixelmonWrapper pw = liveByName((String) field(action, "recipient"));
        if (pw == null) return;
        Object item = field(action, "item");
        line("|-heal|" + posAndName(pw) + "|" + pw.getHealth() + "\\/" + pw.getMaxHealth()
                + "|[from] item: " + itemName(item));
    }

    /* ============================== Emitters ============================== */

    private void emitSwitch(String pos, PixelmonWrapper pw) {
        String name = pw.getPokemonName();
        if (name.equals(activeName.get(pos))) return; // already on the field at this slot
        line("|switch|" + pos + ": " + name + "|" + details(pw) + "|" + pw.getHealth() + "\\/" + pw.getMaxHealth());
        activeName.put(pos, name);
        fainted.remove(name);
    }

    private void emitDamage(PixelmonWrapper target, String targetName) {
        int hp = target.getHealth();
        if (hp <= 0) {
            line("|-damage|" + targetName + "|0 fnt");
        } else {
            line("|-damage|" + targetName + "|" + hp + "\\/" + target.getMaxHealth());
        }
    }

    private void emitFaint(String name) {
        if (name == null || !fainted.add(name)) return;
        String pos = posOfName(name);
        if (pos != null) {
            line("|faint|" + pos + ": " + name);
        }
    }

    /** Emits {@code |switch|} for any slot whose live occupant differs from what was last reported. */
    private void resyncActive() {
        List<BattleParticipant> participants = bc.participants;
        for (int i = 0; i < participants.size(); i++) {
            List<PixelmonWrapper> active = participants.get(i).controlledPokemon;
            for (int slot = 0; slot < active.size(); slot++) {
                PixelmonWrapper pw = active.get(slot);
                if (pw != null) {
                    emitSwitch("p" + (i + 1) + LETTERS[slot], pw);
                }
            }
        }
    }

    /* ============================== Resolution ============================== */

    /** Showdown ident {@code "p1a: Name"} for a live wrapper. */
    private String posAndName(PixelmonWrapper pw) {
        return pos(pw) + ": " + pw.getPokemonName();
    }

    /** Position string ("p1a") for a wrapper, resolved from live battle state. */
    private String pos(PixelmonWrapper pw) {
        List<BattleParticipant> participants = bc.participants;
        for (int i = 0; i < participants.size(); i++) {
            List<PixelmonWrapper> active = participants.get(i).controlledPokemon;
            for (int slot = 0; slot < active.size(); slot++) {
                if (active.get(slot) == pw) {
                    return "p" + (i + 1) + LETTERS[slot];
                }
            }
        }
        String byName = posOfName(pw.getPokemonName());
        return byName != null ? byName : "p1a";
    }

    private String posOfName(String name) {
        List<BattleParticipant> participants = bc.participants;
        for (int i = 0; i < participants.size(); i++) {
            List<PixelmonWrapper> active = participants.get(i).controlledPokemon;
            for (int slot = 0; slot < active.size(); slot++) {
                PixelmonWrapper pw = active.get(slot);
                if (pw != null && name.equals(pw.getPokemonName())) {
                    return "p" + (i + 1) + LETTERS[slot];
                }
            }
        }
        for (Map.Entry<String, String> e : activeName.entrySet()) {
            if (name.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The live active wrapper with this {@code getPokemonName()}, or {@code null}. */
    private PixelmonWrapper liveByName(String name) {
        if (name == null) return null;
        for (BattleParticipant p : bc.participants) {
            for (PixelmonWrapper pw : p.controlledPokemon) {
                if (pw != null && name.equals(pw.getPokemonName())) {
                    return pw;
                }
            }
        }
        return null;
    }

    /** First active Pokémon of the participant with this display name (for participant-only actions). */
    private PixelmonWrapper firstActiveOf(net.minecraft.network.chat.Component participantName) {
        if (participantName == null) return null;
        String target = participantName.getString();
        for (BattleParticipant p : bc.participants) {
            if (p.getName() != null && target.equals(p.getName().getString())) {
                for (PixelmonWrapper pw : p.controlledPokemon) {
                    if (pw != null) return pw;
                }
            }
        }
        return null;
    }

    private AttackResult resultFor(MoveResults results, PixelmonWrapper target) {
        for (SingleInstanceMoveResult r : results.getMoveResultsList()) {
            if (r.getTarget() == target && r.getAttackResult() != null) {
                return r.getAttackResult();
            }
        }
        return results.getAttackResult();
    }

    /* ============================== Formatting ============================== */

    private static String participantName(BattleParticipant participant) {
        if (participant instanceof PlayerParticipant player) {
            return "player:" + player.player.getUUID() + ":" + player.player.getName().getString();
        }
        return "npc:" + (participant.getName() != null ? participant.getName().getString() : "Trainer");
    }

    /** Showdown DETAILS string: {@code "Species, L50, M, shiny"} (gender/shiny omitted when N/A). */
    private static String details(PixelmonWrapper pw) {
        String gender = pw.getGender() == Gender.FEMALE ? ", F"
                : pw.getGender() == Gender.MALE ? ", M" : "";
        String shiny = pw.getPalette() != null && pw.getPalette().isShiny() ? ", shiny" : "";
        return pw.getSpecies().getName() + ", L" + pw.getPokemonLevel() + gender + shiny;
    }

    private static int teamSize(BattleParticipant participant) {
        int count = 0;
        for (PixelmonWrapper pw : participant.allPokemon) {
            if (pw != null) count++;
        }
        return count == 0 ? 6 : count;
    }

    private static String gametype(List<BattleParticipant> participants) {
        if (participants.size() > 2) return "multi";
        int controlled = participants.isEmpty() ? 1 : participants.get(0).numControlledPokemon;
        if (controlled >= 3) return "triples";
        if (controlled == 2) return "doubles";
        return "singles";
    }

    /** Showdown boost-stat id, or {@code null} for HP/NONE (not boostable in the protocol). */
    private static String showdownStat(BattleStatsType stat) {
        if (stat == BattleStatsType.ATTACK) return "atk";
        if (stat == BattleStatsType.DEFENSE) return "def";
        if (stat == BattleStatsType.SPECIAL_ATTACK) return "spa";
        if (stat == BattleStatsType.SPECIAL_DEFENSE) return "spd";
        if (stat == BattleStatsType.SPEED) return "spe";
        if (stat == BattleStatsType.ACCURACY) return "accuracy";
        if (stat == BattleStatsType.EVASION) return "evasion";
        return null;
    }

    private static String weatherName(GlobalStatusBase weather) {
        if (weather instanceof Sunny) return "SunnyDay";
        if (weather instanceof Rainy) return "RainDance";
        if (weather instanceof Sandstorm) return "Sandstorm";
        if (weather instanceof Hail) return "Hail";
        if (weather instanceof Snow) return "Snow";
        return "none";
    }

    private static String terrainName(Terrain terrain) {
        if (terrain instanceof ElectricTerrain) return "Electric Terrain";
        if (terrain instanceof PsychicTerrain) return "Psychic Terrain";
        if (terrain instanceof MistyTerrain) return "Misty Terrain";
        if (terrain instanceof GrassyTerrain) return "Grassy Terrain";
        return null;
    }

    /** Rooms are excluded: their {@code |-fieldstart|} is already emitted by the status move itself. */
    private static String addedFieldEffectName(GlobalStatusBase status) {
        return status instanceof Gravity ? "Gravity" : null;
    }

    private static String fieldEffectName(GlobalStatusBase status) {
        if (status instanceof TrickRoom) return "Trick Room";
        if (status instanceof WonderRoom) return "Wonder Room";
        if (status instanceof MagicRoom) return "Magic Room";
        if (status instanceof Gravity) return "Gravity";
        return null;
    }

    private static Object presentItem(Object item) {
        return item instanceof com.pixelmonmod.pixelmon.items.heldItems.NoItem ? null : item;
    }

    private static String itemName(PixelmonWrapper pw) {
        return pw.hasHeldItem() ? itemName(pw.getUsableHeldItem()) : "";
    }

    /** A readable item name from a HeldItem/PixelmonItem (registry path, title-cased). */
    private static String itemName(Object item) {
        if (item instanceof net.minecraft.world.level.ItemLike itemLike) {
            var key = BuiltInRegistries.ITEM.getKey(itemLike.asItem());
            return prettify(key.getPath());
        }
        return "item";
    }

    private static String prettify(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(capitalize(part));
        }
        return sb.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<StatusType, String> statusIds() {
        Map<StatusType, String> map = new EnumMap<>(StatusType.class);
        map.put(StatusType.Burn, "brn");
        map.put(StatusType.Freeze, "frz");
        map.put(StatusType.Paralysis, "par");
        map.put(StatusType.Poison, "psn");
        map.put(StatusType.PoisonBadly, "tox");
        map.put(StatusType.Sleep, "slp");
        return map;
    }

    /* ============================== Reflection ============================== */

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

    /** Reads a protected/private field (walking the class hierarchy); {@code null} on failure. */
    private static Object field(Object obj, String name) {
        try {
            Field f = FIELDS.computeIfAbsent(obj.getClass().getName() + "#" + name, k -> findField(obj.getClass(), name));
            return f == null ? null : f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        Teras.LOGGER.warn("Teras battle log: field '{}' not found on {}", name, type.getName());
        return null;
    }
}
