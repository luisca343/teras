package es.boffmedia.teras.pixelmon.attacks;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.battles.attacks.specialAttacks.basic.SpecialAttackBase;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;

public class DesenvaineSubito extends SpecialAttackBase {

    public void applyEffectAfterAllTargets(PixelmonWrapper user) {
        if(!user.getSpecies().is(new RegistryValue[]{PixelmonSpecies.AEGISLASH})) return;
        user.setForm("shield");
        user.bc.sendToAll("pixelmon.abilities.stancechange.shield", new Object[]{user.getNickname()});
    }
}
