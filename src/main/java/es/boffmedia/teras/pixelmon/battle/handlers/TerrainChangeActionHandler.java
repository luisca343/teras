package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.TerrainChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.ElectricTerrain;
import com.pixelmonmod.pixelmon.battles.status.GlobalStatusBase;
import com.pixelmonmod.pixelmon.battles.status.GrassyTerrain;
import com.pixelmonmod.pixelmon.battles.status.MistyTerrain;
import com.pixelmonmod.pixelmon.battles.status.PsychicTerrain;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class TerrainChangeActionHandler implements BattleActionHandler<TerrainChangeAction> {
    @Override
    public void handle(TerrainChangeAction action, TerasBattle terasBattle) {
        BattleController bc = terasBattle.getBattle();
        GlobalStatusBase newGlobalStatus = (GlobalStatusBase) getProtectedProperty("newTerrain", action);
        GlobalStatusBase oldGlobalStatus = (GlobalStatusBase) getProtectedProperty("oldTerrain", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        if (newGlobalStatus instanceof ElectricTerrain) {
            appendLine(terasBattle, "|-fieldstart|Electric Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if (newGlobalStatus instanceof PsychicTerrain) {
            appendLine(terasBattle, "|-fieldstart|Psychic Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if (newGlobalStatus instanceof MistyTerrain) {
            appendLine(terasBattle, "|-fieldstart|Misty Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        } else if (newGlobalStatus instanceof GrassyTerrain) {
            appendLine(terasBattle, "|-fieldstart|Grassy Terrain|" + getPositionAndNameString(pokemon, terasBattle));
        }
    }
}