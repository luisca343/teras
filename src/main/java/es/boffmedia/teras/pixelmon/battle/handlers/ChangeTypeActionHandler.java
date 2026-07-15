package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.api.pokemon.Element;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.ChangeTypeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import java.util.List;
import java.util.stream.Collectors;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class ChangeTypeActionHandler implements BattleActionHandler<ChangeTypeAction> {
    @Override
    @SuppressWarnings("unchecked")
    public void handle(ChangeTypeAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        List<Element> newTypes = (List<Element>) getProtectedProperty("newTypes", action);
        if (newTypes == null || newTypes.isEmpty()) return;

        String types = newTypes.stream().map(Element::getName).collect(Collectors.joining("/"));
        appendLine(terasBattle, "|-start|" + getPositionAndNameString(pokemon, terasBattle)
                + "|typechange|" + types);
    }
}
