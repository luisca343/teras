package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.BagItemAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.PixelmonItem;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class BagItemActionHandler implements BattleActionHandler<BagItemAction> {
    @Override
    public void handle(BagItemAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        PixelmonItem item = (PixelmonItem) getProtectedProperty("item", action);
        String itemName = item != null ? item.getLocalizedName() : "item";

        appendLine(terasBattle, "|-heal|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + pokemon.getHealth() + "\\/" + pokemon.getMaxHealth()
                + "|[from] item: " + itemName);
    }
}
