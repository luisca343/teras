package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatusRemoveAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import java.util.EnumMap;
import java.util.Map;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class StatusRemoveActionHandler implements BattleActionHandler<StatusRemoveAction> {
    private static final Map<StatusType, String> CURE_MAP = createCureMap();

    private static Map<StatusType, String> createCureMap() {
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
    public void handle(StatusRemoveAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        StatusBase status = (StatusBase) getProtectedProperty("status", action);
        if (pokemon == null || status == null) return;

        String position = getPositionAndNameString(pokemon, terasBattle);

        if (status.type == StatusType.Confusion) {
            appendLine(terasBattle, "|-end|" + position + "|confusion");
            return;
        }

        String cured = CURE_MAP.get(status.type);
        if (cured != null) {
            appendLine(terasBattle, "|-curestatus|" + position + "|" + cured);
        }
    }
}
