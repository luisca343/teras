package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.MegaEvolveAction")
public class MegaEvolveActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Ljava/lang/String;)V")
    private void onInit(int turn, PixelmonWrapper pokemon, String newForm, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
