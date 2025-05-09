package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.SwitchAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class SwitchActionHandler implements BattleActionHandler<SwitchAction> {
    @Override
    public void handle(SwitchAction action, TerasBattle terasBattle) {
        BattleController bc = terasBattle.getBattle();
        Teras.LOGGER.info("------------------ Switch action ------------------");
        
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        PixelmonWrapper switchingTo = (PixelmonWrapper) getProtectedProperty("switchingTo", action);

        if(!terasBattle.swapv2(pokemon, switchingTo)) return;
        
        appendLine(terasBattle, "|switch|" + terasBattle.getPositionString(switchingTo) + ": "
                + switchingTo.getNickname() + "|" + switchingTo.getSpecies().getName() + ", L"
                + switchingTo.getPokemonLevel().getPokemonLevel()
                + "|" + switchingTo.getHealth() + "\\/" + switchingTo.getMaxHealth());
    }
}