package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.HealPokemonAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class HealPokemonActionHandler implements BattleActionHandler<HealPokemonAction> {
    @Override
    public void handle(HealPokemonAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        int healthAfter = (int) getProtectedProperty("healthAfter", action);
        appendLine(terasBattle, "|-heal|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + healthAfter + "\\/" + pokemon.getMaxHealth());
    }
}
