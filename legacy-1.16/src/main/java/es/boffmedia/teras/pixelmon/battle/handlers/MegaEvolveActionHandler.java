package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.MegaEvolveAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.HeldItem;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class MegaEvolveActionHandler implements BattleActionHandler<MegaEvolveAction> {
    @Override
    public void handle(MegaEvolveAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        HeldItem item = pokemon.getHeldItem();
        String itemName = item != null ? item.getLocalizedName()
                : (String) getProtectedProperty("newForm", action);

        appendLine(terasBattle, "|-mega|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + pokemon.getSpecies().getName() + "|" + itemName);
        appendLine(terasBattle, "|detailschange|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + getPokemonDetails(pokemon));
    }
}
