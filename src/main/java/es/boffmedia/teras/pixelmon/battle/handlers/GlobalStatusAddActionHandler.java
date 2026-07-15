package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.GlobalStatusAddAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.Gravity;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class GlobalStatusAddActionHandler implements BattleActionHandler<GlobalStatusAddAction> {
    @Override
    public void handle(GlobalStatusAddAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        GlobalStatusBase status = (GlobalStatusBase) getProtectedProperty("status", action);
        if (pokemon == null || status == null) return;

        // Weather/terrain have dedicated handlers; rooms are emitted by AttackActionHandler.
        String move = fieldEffectName(status);
        if (move == null) return;

        appendLine(terasBattle, "|-fieldstart|move: " + move + "|[of] " + getPositionAndNameString(pokemon, terasBattle));
    }

    private static String fieldEffectName(GlobalStatusBase status) {
        if (status instanceof Gravity) return "Gravity";
        return null;
    }
}
