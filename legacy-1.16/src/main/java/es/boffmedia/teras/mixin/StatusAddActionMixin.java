package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.StatusBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatusAddAction")
public class StatusAddActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/battles/status/StatusBase;)V")
    private void onInit(int turn, PixelmonWrapper pokemon, StatusBase status, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
