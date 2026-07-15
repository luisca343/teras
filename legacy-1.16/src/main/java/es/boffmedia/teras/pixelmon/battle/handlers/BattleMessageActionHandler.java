package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.BattleMessageAction;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.getProtectedProperty;

public class BattleMessageActionHandler implements BattleActionHandler<BattleMessageAction> {
    @Override
    public void handle(BattleMessageAction action, TerasBattle battle) {
        String message = (String) getProtectedProperty("message", action);
        Teras.LOGGER.error(message);
    }
}