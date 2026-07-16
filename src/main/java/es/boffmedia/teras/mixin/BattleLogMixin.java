package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import es.boffmedia.teras.battle.pixelmon.log.BattleLogRegistry;
import es.boffmedia.teras.battle.pixelmon.log.BattleLogSession;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes every {@code BattleLog.logEvent(BattleAction)} to Teras' Showdown-format translator, live as
 * the battle plays out. Only battles registered with {@link BattleLogRegistry} (i.e. Teras battles)
 * are logged; all other Pixelmon battles pass through untouched. Reading each action live is required
 * because most actions identify Pokémon by name, resolved against the controller's current active
 * slots — state that is gone by the time the battle ends.
 */
@Mixin(targets = "com.pixelmonmod.pixelmon.battles.controller.log.BattleLog")
public abstract class BattleLogMixin {

    @Shadow @Final protected BattleController bc;

    @Inject(method = "logEvent", at = @At("HEAD"))
    private void teras$logEvent(BattleAction action, CallbackInfo ci) {
        BattleLogSession session = BattleLogRegistry.get(bc);
        if (session != null) {
            session.log(action);
        }
    }
}
