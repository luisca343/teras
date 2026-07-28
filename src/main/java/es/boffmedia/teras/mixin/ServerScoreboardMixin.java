package es.boffmedia.teras.mixin;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Makes {@code startTrackingObjective} idempotent: tracking an objective the clients already have
 * is a no-op instead of a second ADD packet.
 *
 * <h2>Why this is needed</h2>
 *
 * <p>Vanilla sends {@code clientbound/set_objective} ADD to everyone and only then adds the
 * objective to {@code trackedObjectives} — it never checks whether it is already in there, because
 * vanilla only ever calls this from {@code setDisplayObjective}, which checks first.</p>
 *
 * <p>CustomNPCs calls it directly, from {@code Availability.initScore}, <b>once per
 * {@code ServerLevel}</b> — and every level returns the same server-wide scoreboard. On a pack with
 * more than one dimension that is N identical ADD packets, and a client that receives the second
 * one throws {@code "An objective with the name … already exists!"} and is disconnected. That is
 * what happened every time {@code teras_ascensor} was referenced from a dialogue's availability
 * while anyone was online.</p>
 *
 * <p>The guard cannot change vanilla behaviour: vanilla never reaches this method with an already
 * tracked objective.</p>
 */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin {

    @Shadow @Final private Set<Objective> trackedObjectives;

    @Inject(method = "startTrackingObjective", at = @At("HEAD"), cancellable = true)
    private void teras$skipAlreadyTracked(Objective objective, CallbackInfo ci) {
        if (trackedObjectives.contains(objective)) {
            ci.cancel();
        }
    }
}
