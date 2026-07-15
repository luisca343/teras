package es.boffmedia.teras.pixelmon.battle.handlers;

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
        GlobalStatusBase newGlobalStatus = (GlobalStatusBase) getProtectedProperty("newTerrain", action);
        GlobalStatusBase oldGlobalStatus = (GlobalStatusBase) getProtectedProperty("oldTerrain", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        String newName = terrainName(newGlobalStatus);
        if (newName != null) {
            appendLine(terasBattle, "|-fieldstart|move: " + newName + "|[of] " + getPositionAndNameString(pokemon, terasBattle));
            return;
        }

        String oldName = terrainName(oldGlobalStatus);
        if (oldName != null) {
            appendLine(terasBattle, "|-fieldend|move: " + oldName);
        }
    }

    private static String terrainName(GlobalStatusBase status) {
        if (status instanceof ElectricTerrain) return "Electric Terrain";
        if (status instanceof PsychicTerrain) return "Psychic Terrain";
        if (status instanceof MistyTerrain) return "Misty Terrain";
        if (status instanceof GrassyTerrain) return "Grassy Terrain";
        return null;
    }
}
