package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class StatChangeActionHandler implements BattleActionHandler<StatChangeAction> {
    @Override
    public void handle(StatChangeAction action, TerasBattle terasBattle) {
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

        checkAndAddStatChange(terasBattle, pokemon, currentAttackBoost, previousAttackBoost, "atk");
        checkAndAddStatChange(terasBattle, pokemon, currentDefenseBoost, previousDefenseBoost, "def");
        checkAndAddStatChange(terasBattle, pokemon, currentSpecialAttackBoost, previousSpecialAttackBoost, "spa");
        checkAndAddStatChange(terasBattle, pokemon, currentSpecialDefenseBoost, previousSpecialDefenseBoost, "spd");
        checkAndAddStatChange(terasBattle, pokemon, currentSpeedBoost, previousSpeedBoost, "spe");
        checkAndAddStatChange(terasBattle, pokemon, currentAccuracyBoost, previousAccuracyBoost, "accuracy");
        checkAndAddStatChange(terasBattle, pokemon, currentEvasionBoost, previousEvasionBoost, "evasion");

        terasBattle.setStats(pokemon, currentStats);
    }
    
    private void checkAndAddStatChange(TerasBattle terasBattle, PixelmonWrapper pokemon, 
                                      int current, int previous, String statName) {
        if (current != previous) {
            boolean isBoost = current > previous;
            String messageType = isBoost ? "boost" : "unboost";
            int magnitude = Math.abs(current - previous);
            
            terasBattle.delayedMessages.add("|-" + messageType + "|" + 
                getPositionAndNameString(pokemon, terasBattle) + "|" + statName + "|" + magnitude);
        }
    }
}