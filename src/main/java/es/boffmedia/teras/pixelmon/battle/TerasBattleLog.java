package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.Weather;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.handlers.BattleActionHandler;
import es.boffmedia.teras.pixelmon.battle.handlers.BattleActionHandlerFactory;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.*;

public class TerasBattleLog {
    private static final Map<Double, Integer> MODIFIER_TO_STAGE = new HashMap<>();

    static {
        MODIFIER_TO_STAGE.put(1.0, 0);
        MODIFIER_TO_STAGE.put(1.5, 1);
        MODIFIER_TO_STAGE.put(2.0, 2);
        MODIFIER_TO_STAGE.put(2.5, 3);
        MODIFIER_TO_STAGE.put(3.0, 4);
        MODIFIER_TO_STAGE.put(3.5, 5);
        MODIFIER_TO_STAGE.put(4.0, 6);
        MODIFIER_TO_STAGE.put(0.67, -1);
        MODIFIER_TO_STAGE.put(0.5, -2);
        MODIFIER_TO_STAGE.put(0.4, -3);
        MODIFIER_TO_STAGE.put(0.33, -4);
        MODIFIER_TO_STAGE.put(0.28, -5);
        MODIFIER_TO_STAGE.put(0.25, -6);
    }

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

    public static void printLog(TerasBattle battle){
        Teras.LOGGER.info(getBattleLog(battle));
    }

    public static void logEvent(BattleAction action, TerasBattle battle) {
        Teras.LOGGER.info("=== Log event: " + action.toString() + " ===");
        
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

    public static int getBoostStage(double modifier) {
        return MODIFIER_TO_STAGE.getOrDefault(modifier, 0);
    }

    // Helpers
    public static String getPositionAndNameString(PixelmonWrapper pokemon, TerasBattle terasBattle){
        return terasBattle.getPositionString(pokemon) + ": " + pokemon.getNickname();
    }

    public static String getPositionString(PixelmonWrapper pokemon, BattleController bc){
        // Returns the position of the pokemon in the team
        // The position consists of the letter p, the participant number and the position in the team (a, b, c, d, e, f...)
        for (BattleParticipant participant : bc.participants) {
            int index = participant.getTeamPokemon().indexOf(pokemon);
            if(index != -1){
                return "p" + (bc.participants.indexOf(participant) + 1) + getPositionLetter(index);
            }
        }
        return null;
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
        Teras.getLogger().info("=== Starting battle ===");
        BattleController bc = terasBattle.battle;
        initLog(terasBattle);

        List<BattleParticipant> participants = terasBattle.getParticipants();

        for (BattleParticipant participant : participants) {
            String participantName = terasBattle.getParticipantName(participant);
            appendLine(terasBattle, "|player|p" + (participants.indexOf(participant) + 1) + "|" + participantName);
        }

        for (BattleParticipant participant : participants) {
            appendLine(terasBattle, "|teamsize|p" + (participants.indexOf(participant) + 1) + "|6");
        }

        appendLine(terasBattle, "|gametype|doubles");
        appendLine(terasBattle, "|gen|9");
        appendLine(terasBattle, "|tier|Circuito de Gimnasios de Teras");

        int index = 1;

        Teras.getLogger().warn("THE CURRENT INDEX IS: " + index);

        List<BattleParticipant> participantsList = new ArrayList<>(bc.participants);
        Teras.getLogger().info("Participants: " + participantsList.size());

        for (BattleParticipant participant : participantsList) {
            Teras.getLogger().info("Participant: " + participant.getDisplayName());
            terasBattle.setParticipant(index - 1, participant);

            for (int i = 0; i < participant.allPokemon.length; i++) {
                PixelmonWrapper pokemon = participant.allPokemon[i];

                if (pokemon != null) {
                    Teras.getLogger().info("Pokemon: " + pokemon.getSpecies().getName() + " " + pokemon.getNickname() + " " + pokemon.getPokemonUUID());
                    terasBattle.getPokemonInit().add("|poke|p" + index + "|" + pokemon.getSpecies().getName() + ", L" + pokemon.getPokemonLevel().getPokemonLevel() + "|");
                }
            }

            int max = participant.numControlledPokemon;
            HashMap<Integer, PixelmonWrapper> team = terasBattle.getActiveTeam(index);

            for (int i = 0; i < max; i++) {
                PixelmonWrapper pw = (PixelmonWrapper) participant.allPokemon[i];
                if (pw != null) {
                    terasBattle.swapv2(index, i, pw);
                    terasBattle.getSwitchInit().add("|switch|" + terasBattle.getPositionString(index, i) + ": "
                            + pw.getNickname() + "|" + pw.getSpecies().getName() + ", L"
                            + pw.getPokemonLevel().getPokemonLevel() + "|" + pw.getHealth() + "\\/" + pw.getMaxHealth());
                }
            }

            index++;
        }

        Teras.getLogger().info("|start battle|");

        appendLine(terasBattle, "|start");
        terasBattle.getSwitchInit().forEach((line) -> appendLine(terasBattle, line));

        appendLine(terasBattle, "|turn|1");
        appendLine(terasBattle, "|");
        appendLine(terasBattle, "|t:|" + System.currentTimeMillis() / 1000);
    }

    // Reflection utility
    public static Object getProtectedProperty(String property, Object obj) {
        try {
            Field f = FieldUtils.getField(obj.getClass(), property, true);
            if (f == null) {
                throw new IllegalArgumentException("Property " + property + " not found in " + obj.getClass());
            }
            f.setAccessible(true);
            return f.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access property: " + property, e);
        }
    }
}