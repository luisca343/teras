package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.GlobalStatusRemoveAction;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.Gravity;
import com.pixelmonmod.pixelmon.battles.status.MagicRoom;
import com.pixelmonmod.pixelmon.battles.status.TrickRoom;
import com.pixelmonmod.pixelmon.battles.status.WonderRoom;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class GlobalStatusRemoveActionHandler implements BattleActionHandler<GlobalStatusRemoveAction> {
    @Override
    public void handle(GlobalStatusRemoveAction action, TerasBattle terasBattle) {
        GlobalStatusBase status = (GlobalStatusBase) getProtectedProperty("status", action);
        if (status == null) return;

        // The remove action carries no participant; only field-wide effects can be attributed.
        String move = fieldEffectName(status);
        if (move == null) return;

        appendLine(terasBattle, "|-fieldend|move: " + move);
    }

    private static String fieldEffectName(GlobalStatusBase status) {
        if (status instanceof TrickRoom) return "Trick Room";
        if (status instanceof WonderRoom) return "Wonder Room";
        if (status instanceof MagicRoom) return "Magic Room";
        if (status instanceof Gravity) return "Gravity";
        return null;
    }
}
