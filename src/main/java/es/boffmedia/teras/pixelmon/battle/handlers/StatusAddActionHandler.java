package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatusAddAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import java.util.EnumMap;
import java.util.Map;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class StatusAddActionHandler implements BattleActionHandler<StatusAddAction> {
    private static final Map<StatusType, String> STATUS_MAP = createStatusMap();
    
    private static Map<StatusType, String> createStatusMap() {
        Map<StatusType, String> map = new EnumMap<>(StatusType.class);
        map.put(StatusType.Burn, "brn");
        map.put(StatusType.Freeze, "frz");
        map.put(StatusType.Paralysis, "par");
        map.put(StatusType.Poison, "psn");
        map.put(StatusType.PoisonBadly, "tox");
        map.put(StatusType.Sleep, "slp");
        return map;
    }

    @Override
    public void handle(StatusAddAction action, TerasBattle terasBattle) {
        StatusBase status = (StatusBase) getProtectedProperty("status", action);
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);

        String showdownStatus = STATUS_MAP.get(status.type);
        if (showdownStatus != null) {
            appendLine(terasBattle, "|-status|" + getPositionAndNameString(pokemon, terasBattle) + "|" + showdownStatus);
        } else if (status.type == StatusType.Confusion) {
            appendLine(terasBattle, "|-start|" + getPositionAndNameString(pokemon, terasBattle) + "|confusion");
        } else if (status.type == StatusType.Flinch) {
            appendLine(terasBattle, "|cant|" + getPositionAndNameString(pokemon, terasBattle) + "|flinch");
        } else if (status.type == StatusType.ParadoxBoost) {
            appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + 
                       "|ability: " + pokemon.getAbility().getName());
        }
    }
}