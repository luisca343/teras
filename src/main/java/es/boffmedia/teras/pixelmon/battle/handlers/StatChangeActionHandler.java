package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class StatChangeActionHandler implements BattleActionHandler<StatChangeAction> {
    @Override
    public void handle(StatChangeAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        BattleStats previousStats = terasBattle.getStats(pokemon);
        BattleStats currentStats = pokemon.getBattleStats();

        if (previousStats == null) {
            terasBattle.setStats(pokemon, currentStats);
            return;
        }

        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.ATTACK, "atk");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.DEFENSE, "def");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.SPECIAL_ATTACK, "spa");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.SPECIAL_DEFENSE, "spd");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.SPEED, "spe");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.ACCURACY, "accuracy");
        emitStatChange(terasBattle, pokemon, previousStats, currentStats, BattleStatsType.EVASION, "evasion");

        terasBattle.setStats(pokemon, currentStats);
    }

    private void emitStatChange(TerasBattle terasBattle, PixelmonWrapper pokemon,
                                BattleStats previousStats, BattleStats currentStats,
                                BattleStatsType type, String statName) {
        int previous = previousStats.getStage(type);
        int current = currentStats.getStage(type);
        if (current == previous) return;

        String messageType = current > previous ? "boost" : "unboost";
        int magnitude = Math.abs(current - previous);
        appendLine(terasBattle, "|-" + messageType + "|"
                + getPositionAndNameString(pokemon, terasBattle) + "|" + statName + "|" + magnitude);
    }
}
