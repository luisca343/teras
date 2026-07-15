package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.HeldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.HeldItemChangeAction")
public class HeldItemChangeActionMixin {
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/items/HeldItem;Lcom/pixelmonmod/pixelmon/items/HeldItem;)V")
    private void onInit(int turn, PixelmonWrapper pokemon, HeldItem oldItem, HeldItem newItem, CallbackInfo ci) {
        this.pokemon = pokemon;
    }
}
