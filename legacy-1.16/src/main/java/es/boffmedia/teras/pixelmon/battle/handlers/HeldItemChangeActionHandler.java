package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.HeldItemChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.HeldItem;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class HeldItemChangeActionHandler implements BattleActionHandler<HeldItemChangeAction> {
    @Override
    public void handle(HeldItemChangeAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        HeldItem oldItem = (HeldItem) getProtectedProperty("oldItem", action);
        HeldItem newItem = (HeldItem) getProtectedProperty("newItem", action);
        String pos = getPositionAndNameString(pokemon, terasBattle);

        if (newItem != null) {
            appendLine(terasBattle, "|-item|" + pos + "|" + newItem.getLocalizedName());
        } else if (oldItem != null) {
            appendLine(terasBattle, "|-enditem|" + pos + "|" + oldItem.getLocalizedName());
        }
    }
}
