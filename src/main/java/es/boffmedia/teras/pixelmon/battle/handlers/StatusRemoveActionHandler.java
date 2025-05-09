package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatusRemoveAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class StatusRemoveActionHandler implements BattleActionHandler<StatusRemoveAction> {
    @Override
    public void handle(StatusRemoveAction action, TerasBattle terasBattle) {
        // This could be implemented in the future as needed
        /*
        StatusBase status = (StatusBase) getProtectedProperty("status", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        if (status.type == StatusType.Flinch) {
            appendLine(terasBattle, "|cant|" + getPositionAndNameString(pokemon, terasBattle) + "|flinch");
        }
        */
    }
}