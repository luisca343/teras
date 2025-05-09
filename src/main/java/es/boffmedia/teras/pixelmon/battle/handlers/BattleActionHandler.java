package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

public interface BattleActionHandler<T extends BattleAction> {
    void handle(T action, TerasBattle battle);
}