package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.api.pokemon.Element;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.ChangeTypeAction")
public class ChangeTypeActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Ljava/util/List;Ljava/util/List;)V")
    private void onInit(int turn, PixelmonWrapper pokemon, List<Element> newTypes, List<Element> oldTypes, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
