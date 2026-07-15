package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.UltraBurstAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.HeldItem;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class UltraBurstActionHandler implements BattleActionHandler<UltraBurstAction> {
    @Override
    public void handle(UltraBurstAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        HeldItem item = pokemon.getHeldItem();
        String itemName = item != null ? item.getLocalizedName() : "Ultranecrozium Z";

        appendLine(terasBattle, "|-burst|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + pokemon.getSpecies().getName() + "|" + itemName);
    }
}
