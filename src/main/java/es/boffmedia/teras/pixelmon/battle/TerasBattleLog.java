package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.Weather;
import es.boffmedia.teras.pixelmon.battle.handlers.BattleActionHandler;
import es.boffmedia.teras.pixelmon.battle.handlers.BattleActionHandlerFactory;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerasBattleLog {
    final char[] teamPos = {'a', 'b', 'c', 'd', 'e', 'f'};

    public static void initLog(TerasBattle battle){
        battle.setLog(new ArrayList<>());
    }

    public static ArrayList<String> getBattleLog(TerasBattle battle){
        if(battle.getLog().isEmpty()) {
            initLog(battle);
        }
        return battle.getLog();
    }

    public static void appendLine(TerasBattle battle, String log){
        battle.appendLog(log);
        if(battle.delayedMessages.size() > 0){
            battle.delayedMessages.forEach(battle::appendLog);
            battle.delayedMessages.clear();
        }
    }

    public static void logEvent(BattleAction action, TerasBattle battle) {
        BattleActionHandler handler = BattleActionHandlerFactory.getHandler(action);
        if (handler != null) {
            handler.handle(action, battle);
        }
        
        /* ====== TO DO ======
         * Añadir |faint|p1a: Mega Salamanca
         * Arreglar el switch cuando entra un pokemon después de debilitar a otro (es un switch normal)
         * ====== TO DO ======
         */
    }

    // Helpers
    public static String getPositionAndNameString(PixelmonWrapper pokemon, TerasBattle terasBattle){
        return terasBattle.getPositionString(pokemon) + ": " + pokemon.getNickname();
    }

    /** Showdown DETAILS string: "Species, L50, M, shiny" (gender/shiny omitted when not applicable). */
    public static String getPokemonDetails(PixelmonWrapper pw){
        String gender = pw.getGender().equals(Gender.FEMALE) ? ", F"
                : (pw.getGender().equals(Gender.MALE) ? ", M" : "");
        String shiny = pw.getRealTextureNoCheck().contains("shiny") ? ", shiny" : "";
        return pw.getSpecies().getName() + ", L" + pw.getPokemonLevel().getPokemonLevel() + gender + shiny;
    }

    public static char getPositionLetter(int index){
        final char[] letters = {'a', 'b', 'c', 'd', 'e', 'f'};
        return letters[index];
    }

    public enum WeatherType {
        SUNNY("SunnyDay"),
        RAIN("RainDance"),
        SANDSTORM("Sandstorm"),
        HAIL("Hail"),
        NONE("none");

        private final String showdownName;

        WeatherType(String showdownName) {
            this.showdownName = showdownName;
        }

        public String getShowdownName() {
            return showdownName;
        }

        public static WeatherType fromPixelmonWeather(Weather weather) {
            switch(weather.type) {
                case Sunny: return SUNNY;
                case Rainy: return RAIN;
                case Sandstorm: return SANDSTORM;
                case Hail: return HAIL;
                default: return NONE;
            }
        }
    }
    
    public static void appendStartBattle(TerasBattle terasBattle) {
        BattleController bc = terasBattle.battle;
        initLog(terasBattle);

        List<BattleParticipant> participants = terasBattle.getParticipants();

        for (BattleParticipant participant : participants) {
            String participantName = terasBattle.getParticipantName(participant);
            appendLine(terasBattle, "|player|p" + (participants.indexOf(participant) + 1) + "|" + participantName);
        }

        for (BattleParticipant participant : participants) {
            appendLine(terasBattle, "|teamsize|p" + (participants.indexOf(participant) + 1) + "|" + countTeamSize(participant));
        }

        appendLine(terasBattle, "|gametype|" + resolveGametype(participants));
        appendLine(terasBattle, "|gen|9");
        appendLine(terasBattle, "|tier|Circuito de Gimnasios de Teras");

        int index = 1;

        List<BattleParticipant> participantsList = new ArrayList<>(bc.participants);

        for (BattleParticipant participant : participantsList) {
            terasBattle.setParticipant(index - 1, participant);

            for (int i = 0; i < participant.allPokemon.length; i++) {
                PixelmonWrapper pokemon = participant.allPokemon[i];

                if (pokemon != null) {
                    terasBattle.getPokemonInit().add("|poke|p" + index + "|" + getPokemonDetails(pokemon) + "|");
                }
            }

            int max = participant.numControlledPokemon;

            for (int i = 0; i < max; i++) {
                PixelmonWrapper pw = (PixelmonWrapper) participant.allPokemon[i];
                if (pw != null) {
                    terasBattle.swapv2(index, i, pw);
                    terasBattle.getSwitchInit().add("|switch|" + terasBattle.getPositionString(index, i) + ": "
                            + pw.getNickname() + "|" + getPokemonDetails(pw) + "|"
                            + pw.getHealth() + "\\/" + pw.getMaxHealth());
                }
            }

            index++;
        }

        // Team preview block.
        appendLine(terasBattle, "|clearpoke");
        terasBattle.getPokemonInit().forEach((line) -> appendLine(terasBattle, line));
        appendLine(terasBattle, "|teampreview");

        appendLine(terasBattle, "|start");
        terasBattle.getSwitchInit().forEach((line) -> appendLine(terasBattle, line));

        // |turn| / |t:| markers are emitted solely by TurnBeginActionHandler to avoid a duplicate |turn|1.
    }

    /** Number of Pokémon on a participant's side (non-null entries in allPokemon). */
    private static int countTeamSize(BattleParticipant participant) {
        int count = 0;
        for (PixelmonWrapper pw : participant.allPokemon) {
            if (pw != null) count++;
        }
        return count == 0 ? 6 : count;
    }

    /** Showdown gametype derived from the active battle, instead of a hardcoded value. */
    private static String resolveGametype(List<BattleParticipant> participants) {
        if (participants.size() > 2) return "multi";
        int controlled = participants.isEmpty() ? 1 : participants.get(0).numControlledPokemon;
        if (controlled >= 3) return "triples";
        if (controlled == 2) return "doubles";
        return "singles";
    }

    // Reflection utility. The (class, property) -> Field lookup is resolved once and cached so the
    // per-event hot path is a map get + Field.get rather than a full class-hierarchy scan every time.
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    public static Object getProtectedProperty(String property, Object obj) {
        Class<?> type = obj.getClass();
        Field f = FIELD_CACHE.computeIfAbsent(type.getName() + "#" + property, key -> {
            Field found = FieldUtils.getField(type, property, true);
            if (found == null) {
                throw new IllegalArgumentException("Property " + property + " not found in " + type);
            }
            found.setAccessible(true);
            return found;
        });
        try {
            return f.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access property: " + property, e);
        }
    }
}