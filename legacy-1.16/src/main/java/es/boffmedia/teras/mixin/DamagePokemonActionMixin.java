package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.attacks.DamageTypeEnum;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.DamagePokemonAction")
public class DamagePokemonActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/battles/attacks/DamageTypeEnum;II)V")
    private void onInit(int turn, PixelmonWrapper pokemon, int damage, PixelmonWrapper source, DamageTypeEnum damageType, int healthBefore, int healthAfter, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
