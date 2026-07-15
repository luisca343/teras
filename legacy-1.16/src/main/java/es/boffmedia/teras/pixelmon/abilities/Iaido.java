package es.boffmedia.teras.pixelmon.abilities;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.pokemon.ability.AbstractAbility;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;


public class Iaido extends AbstractAbility {
    public Iaido() {
    }

    public void preProcessAttackUser(PixelmonWrapper pokemon, PixelmonWrapper target, Attack a) {
        if (pokemon.getSpecies().is(new RegistryValue[]{PixelmonSpecies.AEGISLASH}) && (Attack.dealsDamage(a) || a.isZ && !a.getAttackCategory().equals(AttackCategory.STATUS)) && pokemon.getForm().isForm(new String[]{"shield"})) {
            pokemon.setForm("blade");
            pokemon.bc.sendToAll("pixelmon.abilities.stancechange.blade", new Object[]{pokemon.getNickname()});
        }
    }

    public void applySwitchOutEffect(PixelmonWrapper oldPokemon) {
        if (oldPokemon.getSpecies().is(new RegistryValue[]{PixelmonSpecies.AEGISLASH}) && oldPokemon.getForm().isForm(new String[]{"blade"})) {
            oldPokemon.setForm("shield");
        }

    }
}
