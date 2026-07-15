package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.TeamSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeamSelection.class)
public class TeamSelectionMixin {
    @Redirect(method = "startBattle", at = @At(value = "INVOKE", target = "Lcom/pixelmonmod/pixelmon/api/util/helpers/RandomHelper;getRandomDistinctNumbersBetween(III)[I"))
    private int[] overrideRandomDistinctNumbersBetween(int start, int end, int count) {
        // Replace with your own logic
        return new int[]{0, 1, 2, 3, 4, 5};
    }
}