package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.api.pokemon.ability.Ability;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.ChangeAbilityAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class ChangeAbilityActionHandler implements BattleActionHandler<ChangeAbilityAction> {
    @Override
    public void handle(ChangeAbilityAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        Ability newAbility = (Ability) getProtectedProperty("newAbility", action);
        if (newAbility == null) return;

        appendLine(terasBattle, "|-ability|" + getPositionAndNameString(pokemon, terasBattle)
                + "|" + newAbility.getName());
    }
}
