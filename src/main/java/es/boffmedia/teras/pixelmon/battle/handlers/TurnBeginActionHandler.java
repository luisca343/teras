package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.TurnBeginAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import java.util.ArrayList;
import java.util.List;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class TurnBeginActionHandler implements BattleActionHandler<TurnBeginAction> {
    @Override
    public void handle(TurnBeginAction action, TerasBattle terasBattle) {
        BattleController bc = terasBattle.getBattle();
        int turn = (int) getProtectedProperty("turn", action);
        if(turn == 0) appendStartBattle(terasBattle);

        appendLine(terasBattle,"|turn|" + (turn+1));
        appendLine(terasBattle,"|");
        appendLine(terasBattle, "|t:|" + System.currentTimeMillis() / 1000);

        List<BattleParticipant> participantsList = new ArrayList<>(bc.participants);

        int participantIndex = 1;
        for (BattleParticipant participant : participantsList) {
            Teras.getLogger().warn(participant.controlledPokemon);
            for (int i = 0; i < participant.controlledPokemon.size(); i++) {
                PixelmonWrapper pokemon = participant.controlledPokemon.get(i);
                if(!terasBattle.getActivePokemon(participantIndex, i).equals(pokemon)){
                    String key = terasBattle.getPositionString(participantIndex, i);
                    String currentPosition = terasBattle.getPositionString(pokemon);

                    if(currentPosition == null || key.equals(currentPosition)) {
                        terasBattle.swapv2(participantIndex, i, pokemon);
                        appendLine(terasBattle,"|switch|" + terasBattle.getPositionString(participantIndex, i) + ": "
                                +pokemon.getNickname()  + "|" + pokemon.getSpecies().getName() + ", L"
                                + pokemon.getPokemonLevel().getPokemonLevel() + "|" + pokemon.getHealth() + "\\/" + pokemon.getMaxHealth());
                    } else {
                        Teras.getLogger().error("SWAP WAS CANCELLED, POKEMON IS ON THE FIELD");
                        return;
                    }
                }
            }
            participantIndex++;
        }
    }
}