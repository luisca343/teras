package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.controller.log.action.type.StatChangeAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatChangeAction.class)
public class StatChangeActionMixin {

    private PixelmonWrapper pokemon;

    @Inject(at = @At(value = "RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;[I[I)V")
    private void init(int turn, PixelmonWrapper pokemon, int[] oldStats, int[] newStats, CallbackInfo ci) {
        if(pokemon != null) this.pokemon = pokemon;
    }

}
