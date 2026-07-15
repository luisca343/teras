package es.boffmedia.teras.mixin;


import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.items.PixelmonItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.action.type.BagItemAction")
public class BagItemActionMixin {
    // The recipient is the third constructor arg (the Pokémon the item is used on), not the user.
    PixelmonWrapper pokemon;
    @Inject(at = @At("RETURN"), method = "<init>(ILcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;Lcom/pixelmonmod/pixelmon/items/PixelmonItem;)V")
    private void onInit(int turn, PixelmonWrapper user, PixelmonWrapper recipient, PixelmonItem item, CallbackInfo ci) {
        this.pokemon = recipient;
    }
}
