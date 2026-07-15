package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.TurnEndAction;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.appendLine;

public class TurnEndActionHandler implements BattleActionHandler<TurnEndAction> {
    @Override
    public void handle(TurnEndAction action, TerasBattle terasBattle) {
        appendLine(terasBattle, "|upkeep");
    }
}
