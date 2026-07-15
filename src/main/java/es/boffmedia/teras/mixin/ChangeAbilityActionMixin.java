package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.api.pokemon.ability.Ability;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.ChangeAbilityAction")
public class ChangeAbilityActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/api/pokemon/ability/Ability;Lcom/pixelmonmod/pixelmon/api/pokemon/ability/Ability;)V")
    private void onInit(int turn, PixelmonWrapper pokemon, Ability oldAbility, Ability newAbility, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
