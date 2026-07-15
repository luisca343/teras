package es.boffmedia.teras.pixelmon.attacks;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.registries.PixelmonItems;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.battles.attacks.specialAttacks.basic.SpecialAttackBase;
import com.pixelmonmod.pixelmon.battles.controller.log.AttackResult;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;

public class TestAttack extends SpecialAttackBase {
    public AttackResult applyEffectStart(PixelmonWrapper user, PixelmonWrapper target) {
        if (!user.hasHeldItem() || user.getUsableHeldItem() == PixelmonItems.flying_gem)
            user.attack.setOverridePower(user.attack.getMove().getBasePower() * 2);
        return AttackResult.proceed;
    }

}
