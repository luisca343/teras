package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.BattleEndAction;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.getProtectedProperty;

public class BattleEndActionHandler implements BattleActionHandler<BattleEndAction> {
    @Override
    public void handle(BattleEndAction action, TerasBattle terasBattle) {
        BattleController bc = terasBattle.getBattle();
        Teras.LOGGER.error("===Battle End===");
        
        // Future implementation for determining the winner
        /* Example implementation:
        String winner = bc.participants.stream()
                .filter((participant) -> participant.controlledPokemon.size() > 0)
                .findFirst()
                .map(BattleParticipant::getDisplayName)
                .orElse("unknown");
        */
    }
}