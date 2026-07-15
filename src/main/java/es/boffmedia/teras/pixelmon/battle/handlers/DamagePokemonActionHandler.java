package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.attacks.DamageTypeEnum;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.DamagePokemonAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class DamagePokemonActionHandler implements BattleActionHandler<DamagePokemonAction> {
    @Override
    public void handle(DamagePokemonAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        if (pokemon == null) return;

        DamageTypeEnum damageType = (DamageTypeEnum) getProtectedProperty("damageType", action);

        // Direct move damage is already emitted by AttackActionHandler; only indirect sources here.
        if (damageType == DamageTypeEnum.ATTACK || damageType == DamageTypeEnum.ATTACKFIXED
                || damageType == DamageTypeEnum.STRUGGLE) {
            return;
        }

        int healthAfter = (int) getProtectedProperty("healthAfter", action);
        String target = getPositionAndNameString(pokemon, terasBattle);
        String from = fromTag(damageType);

        if (healthAfter <= 0) {
            appendLine(terasBattle, "|-damage|" + target + "|0 fnt" + from);
            appendLine(terasBattle, "|faint|" + target);
        } else {
            appendLine(terasBattle, "|-damage|" + target + "|" + healthAfter + "\\/" + pokemon.getMaxHealth() + from);
        }
    }

    private static String fromTag(DamageTypeEnum damageType) {
        if (damageType == DamageTypeEnum.RECOIL || damageType == DamageTypeEnum.CRASH) {
            return "|[from] recoil";
        }
        return "";
    }
}
