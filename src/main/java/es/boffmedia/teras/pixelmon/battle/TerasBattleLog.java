package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.AttackResult;
import com.pixelmonmod.pixelmon.battles.controller.log.MoveResults;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.*;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.AttackAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.BattleEndAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.BattleMessageAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatusAddAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.SwitchAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.TerrainChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.TurnBeginAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.WeatherChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.ElectricTerrain;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.GrassyTerrain;
import com.pixelmonmod.pixelmon.battles.status.MistyTerrain;
import com.pixelmonmod.pixelmon.battles.status.PsychicTerrain;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import com.pixelmonmod.pixelmon.battles.status.Weather;
import es.boffmedia.teras.Teras;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.*;

public class TerasBattleLog {

    //public static HashMap<Integer, HashMap<String, List<PixelmonWrapper>>> activePokemon = new HashMap<>();

    public static void initLog(TerasBattle battle){
        battle.setLog(new ArrayList<>());
    }
    final char[] teamPos = {'a', 'b', 'c', 'd', 'e', 'f'};

    public static ArrayList<String> getBattleLog(TerasBattle battle){
        if(battle.getLog().isEmpty()) {
            initLog(battle);
        }
        return battle.getLog();
    }


    public static void appendLine(TerasBattle battle, String log){
        //Teras.getLogger().warn("LOGGING: " + log);
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
        if (action instanceof TurnBeginAction) turnBeginAction((TurnBeginAction) action, battle);
        else if (action instanceof BattleEndAction) { battleEndAction((BattleEndAction) action, battle); }
        else if (action instanceof SwitchAction) switchAction((SwitchAction) action, battle);
        else if (action instanceof AttackAction) attackAction((AttackAction) action, battle);
        else if (action instanceof StatChangeAction) statChangeAction((StatChangeAction) action, battle);
        else if (action instanceof WeatherChangeAction) weatherChangeAction((WeatherChangeAction) action, battle);
        else if (action instanceof StatusAddAction) statusAddAction((StatusAddAction) action, battle);
        else if (action instanceof StatusRemoveAction) statusRemoveAction((StatusRemoveAction) action, battle);
        else if (action instanceof TerrainChangeAction) fieldChangeAction((TerrainChangeAction) action, battle);
        else if (action instanceof BattleMessageAction) battleMessageAction((BattleMessageAction) action, battle);


        /*




        else if (action instanceof BattleMessageAction) battleMessageAction((BattleMessageAction) action, battle);
        else if (action instanceof TurnEndAction) turnEndAction((TurnEndAction) action, battle);
        else Teras.LOGGER.info("Unknown action: " + action.getClass().getSimpleName());
        */



        /* ====== TO DO ======
         * Añadir |faint|p1a: Mega Salamanca
         * Arreglar el switch cuando entra un pokemon después de debilitar a otro (es un switch normal)
         * ====== TO DO ======
         */
    }

    private static void statusRemoveAction(StatusRemoveAction action, TerasBattle battle) {
        /*
        StatusBase status = (StatusBase) getProtectedProperty("status", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        if(status.type == StatusType.Flinch){
            appendLine(battle,"|cant|"+getPositionAndNameString(pokemon, battle)+"|flinch");
        }*/

    }

    private static void battleMessageAction(BattleMessageAction action, TerasBattle battle) {
        String message = (String) getProtectedProperty("message", action);
        Teras.LOGGER.error(message);
    }

    /* Actions */
    private static void battleEndAction(BattleEndAction action, TerasBattle terasBattle){

       /* BattleController bc = terasBattle.battle;
        Teras.LOGGER.error("===Battle End===");

        String winner = bc.participants.stream()
                .filter((participant) -> participant.controlledPokemon.size() > 0).findFirst().get().getDisplayName();

       */
    }


    public static void turnBeginAction(TurnBeginAction action, TerasBattle terasBattle){
        BattleController bc = terasBattle.battle;
        int turn = (int) getProtectedProperty("turn", action);
        if(turn == 0) appendStartBattle(terasBattle);

        /*
        bc.globalStatusController.triggerWeatherChange(new com.pixelmonmod.pixelmon.battles.status.Sunny(999));
        bc.globalStatusController.triggerTerrainChange(new ElectricTerrain(999));
        */

        appendLine(terasBattle,"|turn|" + (turn+1));
        //appendLine(bc.battleIndex,"|callback|decision");
        appendLine(terasBattle,"|");
        appendLine(terasBattle, "|t:|" + System.currentTimeMillis() / 1000);

        List<BattleParticipant> participantsList = new ArrayList<>(bc.participants);
        //Collections.reverse(participantsList);

        int participantIndex = 1;
        for (BattleParticipant participant : participantsList) {
            Teras.getLogger().warn(participant.controlledPokemon);
            for (int i = 0; i < participant.controlledPokemon.size(); i++) {
                PixelmonWrapper pokemon = participant.controlledPokemon.get(i);
                if(!terasBattle.getActivePokemon(participantIndex, i).equals(pokemon)){
                    String key = terasBattle.getPositionString(participantIndex, i);
                    String currentPosition = terasBattle.getPositionString(pokemon);

                    if(currentPosition == null || key.equals(currentPosition)) {
                    terasBattle.swapv2(participantIndex, i, pokemon);
                    appendLine( terasBattle,"|switch|" + terasBattle.getPositionString(participantIndex, i) + ": "
                            +pokemon.getNickname()  + "|" + pokemon.getSpecies().getName() + ", L"
                            + pokemon.getPokemonLevel().getPokemonLevel() + "|" + pokemon.getHealth() + "\\/" + pokemon.getMaxHealth());
                    } else {
                        Teras.getLogger().error("SWAP WAS CANCELLED, POKEMON IS ON THE FIELD");
                        return;
                    }
                }
            }
            participantIndex++;
        }

    }

    public static void switchAction(SwitchAction action, TerasBattle terasBattle){
        BattleController bc = terasBattle.battle;
        Teras.LOGGER.info("------------------ Switch action ------------------");
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        PixelmonWrapper switchingTo = (PixelmonWrapper) getProtectedProperty("switchingTo", action);

        if(!terasBattle.swapv2(pokemon, switchingTo)) return;
        appendLine(terasBattle, "|switch|" + terasBattle.getPositionString(switchingTo) + ": "
                +switchingTo.getNickname()  + "|" + switchingTo.getSpecies().getName() + ", L"
                + switchingTo.getPokemonLevel().getPokemonLevel()
                + "|" + switchingTo.getHealth() + "\\/" + switchingTo.getMaxHealth());

    }



    public static void attackAction(AttackAction action, TerasBattle terasBattle){
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        Attack attack = (Attack) getProtectedProperty("attack", action);
        boolean wildPokemon = (boolean) getProtectedProperty("wildPokemon", action);
        String pokemonName = (String) getProtectedProperty("pokemonName", action);
        String[] targets = (String[]) getProtectedProperty("targets", action);
        MoveResults[] moveResults = (MoveResults[]) getProtectedProperty("moveResults", action);

        // The attack action is a bit more complex, as it can target multiple pokemon
        // move|p2a: Calyrex|Astral Barrage|p1a: Kyogren't|[spread] p1a,p1b

        String move = attack.getActualMove().getAttackName();
        String source = terasBattle.getPositionString(pokemon);

        String attackStr =  "|move|" + getPositionAndNameString(pokemon, terasBattle) + "|" + move + "|";

        PixelmonWrapper moveTarget = pokemon;
        for (MoveResults result : moveResults) {
            if (result.target != null) {
                moveTarget = result.target;
                break;
            }
        }

        String mainTargetStr = getPositionAndNameString(moveTarget, terasBattle);
        attackStr += mainTargetStr + "|";

        if(moveResults.length > 1){
            attackStr += "[spread] ";
            attackStr += Arrays.stream(moveResults).map((target) -> terasBattle.getPositionString(target.target)).reduce((a, b) -> a + "," + b).get();
        }

        appendLine(terasBattle, attackStr);

        if(attack.getAttackCategory().equals(AttackCategory.STATUS)){
            switch (move) {
                case "Wonder Room": case "Trick Room": case "Magic Room":
                    appendLine(terasBattle, "|-fieldstart|"+move+"|[of] " + getPositionAndNameString(pokemon, terasBattle));
                    break;
                case "Protect": case "Detect": case "Endure": case "King's Shield": case "Spiky Shield": case "Baneful Bunker": case "Obstruct":
                case "Quick Guard": case "Wide Guard": case "Crafty Shield": case "Mat Block": case "Feint": case "Follow Me": case "Rage Powder":
                case "Ally Switch": case "Helping Hand": case "Tailwind": case "Lucky Chant": case "Safeguard": case "Mist": case "Light Screen":
                case "Reflect": case "Aurora Veil": case "Brick Break": case "Defog": case "Haze": case "Heal Bell": case "Heal Pulse": case "Memento":
                    if(!attack.moveResult.getResult().equals(AttackResult.failed)) appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + "|move: " + move);
                    break;
                default:
                    appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + "|move: " + move);
                    break;
            }


            return;
        }
        for (MoveResults moveResult : moveResults) {
            PixelmonWrapper target = moveResult.target;
            String targetName = target.getNickname();

            int currentHealth = moveResult.getTarget().getHealth();
            int maxHealth = moveResult.getTarget().getMaxHealth();

            if(currentHealth == 0){
                appendLine(terasBattle, "|-damage|" + terasBattle.getPositionString(target) + ": " + targetName + "|0 fnt");
            }else{
                appendLine(terasBattle, "|-damage|" + terasBattle.getPositionString(target) + ": " + targetName + "|" + currentHealth + "\\/" + maxHealth);
            }


        }

        // after faint: |faint|p2b: Wugtrio, we loop again
        for (MoveResults moveResult : moveResults) {
            PixelmonWrapper target = moveResult.target;
            if(moveResult.getTarget().getHealth() == 0){
                appendLine(terasBattle, "|faint|" + terasBattle.getPositionString(target) + ": " + target.getNickname());
            }
        }
    }

    public static void statChangeAction(StatChangeAction action, TerasBattle terasBattle){
        int[] oldStats = (int[]) getProtectedProperty("oldStats", action);
        int[] newStats = (int[]) getProtectedProperty("newStats", action);
        String pokemonName = (String) getProtectedProperty("pokemonName", action);

        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);


        BattleStats previousStats = terasBattle.getStats(pokemon);
        BattleStats currentStats = pokemon.getBattleStats();

        // Null check the previous
        if(previousStats == null){
            terasBattle.setStats(pokemon, currentStats);
            return;
        }

        int currentAttackBoost = getBoostStage(currentStats.getAttackModifier());
        int currentDefenseBoost = getBoostStage(currentStats.getDefenseModifier());
        int currentSpecialAttackBoost = getBoostStage(currentStats.getSpecialAttackModifier());
        int currentSpecialDefenseBoost = getBoostStage(currentStats.getSpecialDefenseModifier());
        int currentSpeedBoost = getBoostStage(currentStats.getSpeedModifier());


        int currentAccuracyBoost = currentStats.getAccuracyStage();
        int currentEvasionBoost = currentStats.getEvasionStage();


        int previousAttackBoost = getBoostStage(previousStats.getAttackModifier());
        int previousDefenseBoost = getBoostStage(previousStats.getDefenseModifier());
        int previousSpecialAttackBoost = getBoostStage(previousStats.getSpecialAttackModifier());
        int previousSpecialDefenseBoost = getBoostStage(previousStats.getSpecialDefenseModifier());
        int previousSpeedBoost = getBoostStage(previousStats.getSpeedModifier());

        int previousAccuracyBoost = previousStats.getAccuracyStage();
        int previousEvasionBoost = previousStats.getEvasionStage();



        if(currentAttackBoost != previousAttackBoost){
            terasBattle.delayedMessages.add("|-" + (currentAttackBoost > previousAttackBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|atk|" + Math.abs(currentAttackBoost - previousAttackBoost));
        }

        if(currentDefenseBoost != previousDefenseBoost){
            terasBattle.delayedMessages.add("|-" + (currentDefenseBoost > previousDefenseBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|def|" + Math.abs(currentDefenseBoost - previousDefenseBoost));
        }

        if(currentSpecialAttackBoost != previousSpecialAttackBoost){
            terasBattle.delayedMessages.add("|-" + (currentSpecialAttackBoost > previousSpecialAttackBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|spa|" + Math.abs(currentSpecialAttackBoost - previousSpecialAttackBoost));
        }

        if(currentSpecialDefenseBoost != previousSpecialDefenseBoost){
            terasBattle.delayedMessages.add("|-" + (currentSpecialDefenseBoost > previousSpecialDefenseBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|spd|" + Math.abs(currentSpecialDefenseBoost - previousSpecialDefenseBoost));
        }

        if(currentSpeedBoost != previousSpeedBoost){
            terasBattle.delayedMessages.add("|-" + (currentSpeedBoost > previousSpeedBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|spe|" + Math.abs(currentSpeedBoost - previousSpeedBoost));
        }

        if(currentAccuracyBoost != previousAccuracyBoost){
            terasBattle.delayedMessages.add("|-" + (currentAccuracyBoost > previousAccuracyBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|accuracy|" + Math.abs(currentAccuracyBoost - previousAccuracyBoost));
        }

        if(currentEvasionBoost != previousEvasionBoost){
            terasBattle.delayedMessages.add("|-" + (currentEvasionBoost > previousEvasionBoost ? "boost" : "unboost") + "|" + getPositionAndNameString(pokemon, terasBattle) + "|evasion|" + Math.abs(currentEvasionBoost - previousEvasionBoost));
        }

        terasBattle.setStats(pokemon, currentStats);




        //Teras.getLogger().error("+++Pokemon: " + pokemon.getSpecies().getName() +" " + pokemon.getPokemonName() +" " + pokemon.getPokemonUUID());

        //Teras.getLogger().info("=== Stat change action for " + pokemonName + " ===");
        //int[] delta = new int[oldStats.length];
        //for (int i = 0; i < oldStats.length; i++) {
            //delta[i] = newStats[i] - oldStats[i];
            //Teras.getLogger().info(newStats[i] + " - " + oldStats[i] + " = " + delta[i]);
        //}

        /*
        for (int i = 0; i < delta.length; i++) {
            if (delta[i] == 0 || oldStats[i] == 0) continue;

            BattleStatsType stat = BattleStatsType.fromIndex(i);
            //Teras.LOGGER.info(pokemonName + " " + stat.name() + " " + delta[i]);

            String boostType = delta[i] > 0 ? "|-boost|" : "|-unboost|";
            String statName = stat.name().toLowerCase();
            String boost = boostType + terasBattle.getPositionString(pokemon) + "|" + statName + "|" + Math.abs(delta[i]);
            appendLine(terasBattle, boost);
        }*/
    }

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

    public static int getBoostStage(double modifier) {
        return MODIFIER_TO_STAGE.getOrDefault(modifier, 0);
    }

    public static void fieldChangeAction(TerrainChangeAction action, TerasBattle terasBattle){
        BattleController bc = terasBattle.battle;
        GlobalStatusBase newGlobalStatus = (GlobalStatusBase) getProtectedProperty("newTerrain", action);
        GlobalStatusBase oldGlobalStatus = (GlobalStatusBase) getProtectedProperty("oldTerrain", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);


        if(newGlobalStatus instanceof ElectricTerrain){
            appendLine(terasBattle,"|-fieldstart|Electric Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if(newGlobalStatus instanceof PsychicTerrain){
            appendLine(terasBattle,"|-fieldstart|Psychic Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if(newGlobalStatus instanceof MistyTerrain){
            appendLine(terasBattle,"|-fieldstart|Misty Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if(newGlobalStatus instanceof GrassyTerrain){
            appendLine(terasBattle,"|-fieldstart|Grassy Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        }


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

    public static void weatherChangeAction(WeatherChangeAction action, TerasBattle terasBattle) {
        GlobalStatusBase newGlobalStatus = (GlobalStatusBase) getProtectedProperty("newWeather", action);
        if (newGlobalStatus instanceof Weather) {
            Weather newWeather = (Weather) newGlobalStatus;
            WeatherType weatherType = WeatherType.fromPixelmonWeather(newWeather);
            appendLine(terasBattle, "|-weather|" + weatherType.getShowdownName() + "|");
        }
    }


    public static void statusAddAction(StatusAddAction action, TerasBattle terasBattle) {
        StatusBase status = (StatusBase) getProtectedProperty("status", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        Map<StatusType, String> statusMap = new EnumMap<>(StatusType.class);
        statusMap.put(StatusType.Burn, "brn");
        statusMap.put(StatusType.Freeze, "frz");
        statusMap.put(StatusType.Paralysis, "par");
        statusMap.put(StatusType.Poison, "psn");
        statusMap.put(StatusType.PoisonBadly, "tox");
        statusMap.put(StatusType.Sleep, "slp");
        statusMap.put(StatusType.Confusion, "confusion");

        String showdownStatus = statusMap.get(status.type);
        if (showdownStatus != null) {
            appendLine(terasBattle, "|-status|" + getPositionAndNameString(pokemon, terasBattle) + "|" + showdownStatus);
        } else if (status.type == StatusType.Flinch) {
            appendLine(terasBattle, "|-flinch|" + getPositionAndNameString(pokemon, terasBattle));
        } else if (status.type == StatusType.ParadoxBoost) {
            appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + "|ability: " + pokemon.getAbility().getName());
        }
    }

    /*
    public static PokemonPosition getPosition(PixelmonWrapper pokemon, TerasBattle terasBattle){
        BattleController bc = terasBattle.battle;
        int participantIndex = 0;
        for (BattleParticipant participant : bc.participants) {
            int index = participant.getTeamPokemon().indexOf(pokemon);
            if(index != -1){
                return new PokemonPosition(participantIndex, index);
            }
            participantIndex++;
        }

        return null;
    }

    public static void turnEndAction(TurnEndAction action,  TerasBattle terasBattle){
        BattleController bc = terasBattle.battle;

    }

    public static void battleMessageAction(BattleMessageAction action, TerasBattle terasBattle){
        String message = (String) getProtectedProperty("message", action);
        Teras.getLogger().error(message);
    }

*/
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

    public static void appendStartBattle(TerasBattle terasBattle){
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

        appendLine(terasBattle,"|gametype|doubles");
        appendLine(terasBattle,"|gen|9");
        appendLine(terasBattle,"|tier|Circuito de Gimnasios de Teras");

        int index = 1;

        Teras.getLogger().warn("THE CURRENT INDEX IS: " + index);

        List<BattleParticipant> participantsList = new ArrayList<>(bc.participants);
        //Collections.reverse(participantsList);

        Teras.getLogger().info("Participants: " + participantsList.size());


        for (BattleParticipant participant : participantsList) {
            Teras.getLogger().info("Participant: " + participant.getDisplayName());
            terasBattle.setParticipant(index - 1, participant);

            for(int i = 0; i < participant.allPokemon.length; i++){
                PixelmonWrapper pokemon = participant.allPokemon[i];

                if(pokemon != null) {
                    Teras.getLogger().info("Pokemon: " + pokemon.getSpecies().getName() + " " + pokemon.getNickname() + " " + pokemon.getPokemonUUID());
                    //terasBattle.swapv2(index[0], i, pokemon);
                    terasBattle.getPokemonInit().add("|poke|p" + index + "|" + pokemon.getSpecies().getName()  + ", L"+pokemon.getPokemonLevel().getPokemonLevel()+"|");
                }
            }


            int max = participant.numControlledPokemon;

            HashMap<Integer, PixelmonWrapper> team = terasBattle.getActiveTeam(index);

            /*team.forEach((key, pw) -> {
                if(positionIndex[0] >= terasBattle.getBattleConfig().getModalidad()[index[0] - 1]) return;
                if(pw != null) {
                    //terasBattle.swapv2(index[0], positionIndex[0], pw);
                    terasBattle.getSwitchInit().add("|switch|" + terasBattle.getPositionString(index[0], positionIndex[0]) + ": "
                            +pw.getNickname()  + "|" + pw.getSpecies().getName() + ", L"
                            + pw.getPokemonLevel().getPokemonLevel() + "|" + pw.getHealth() + "\\/" + pw.getMaxHealth());
                };
                positionIndex[0]++;
            });*/


            for(int i = 0; i < max; i++){
                PixelmonWrapper pw = (PixelmonWrapper) participant.allPokemon[i];
                //appendLine(terasBattle, "|switch|" + getPositionString(pokemon, bc) + ": " +switchingTo.getNickname()  + "|" + switchingTo.getSpecies().getName() + ", L" + switchingTo.getPokemonLevel() + "|" + switchingTo.getHealth() + "\\/" + switchingTo.getMaxHealth());
                if(pw != null) {
                    terasBattle.swapv2(index, i, pw);
                    terasBattle.getSwitchInit().add("|switch|" + terasBattle.getPositionString(index, i) + ": "
                            +pw.getNickname()  + "|" + pw.getSpecies().getName() + ", L"
                            + pw.getPokemonLevel().getPokemonLevel() + "|" + pw.getHealth() + "\\/" + pw.getMaxHealth());
                };
                //positionIndex[0]++;

                //finalActive.put(participant.getDisplayName(), Arrays.asList(participant.allPokemon));
            }

            index++;

        }



        Teras.getLogger().info("|start battle|");

        /*
        appendLine(terasBattle, "|clearpoke");
        terasBattle.getPokemonInit().forEach((line) -> appendLine(terasBattle, line))

        appendLine(terasBattle, "|teampreview");
        appendLine(terasBattle, "|");
        appendLine(terasBattle, "|t:|" + System.currentTimeMillis() / 1000);;
        */

        appendLine(terasBattle, "|start");
        terasBattle.getSwitchInit().forEach((line) -> appendLine(terasBattle, line));

        appendLine(terasBattle, "|turn|1");
        appendLine(terasBattle, "|");
        appendLine(terasBattle, "|t:|" + System.currentTimeMillis() / 1000);




    }


    // Reflection
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
