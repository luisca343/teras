package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.EnterDynamaxAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class EnterDynamaxActionHandler implements BattleActionHandler<EnterDynamaxAction> {
    @Override
    public void handle(EnterDynamaxAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        boolean gigantamax = (boolean) getProtectedProperty("gigantamax", action);
        appendLine(terasBattle, "|-start|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + (gigantamax ? "Gigantamax" : "Dynamax"));
    }
}
