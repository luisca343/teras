package es.boffmedia.teras.mixin;

import noppes.npcs.CustomNpcs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * Fixes CustomNPCs' <b>scoreboard dirty-listener</b>: the null it passes to {@code Optional.of},
 * and the objective packet it sends a second time.
 *
 * <p>CustomNPCs registers this listener on {@code ServerScoreboard} at its own server start, so it
 * runs on <i>every</i> scoreboard mutation — a score changing included — and walks every objective
 * any dialogue's availability names, for every online player.</p>
 *
 * <h2>1 · The null</h2>
 *
 * <p>It builds a {@code ClientboundSetScorePacket} with {@code Optional.of(access.display())} and
 * {@code Optional.of(info.numberFormat())}. Both are {@code @Nullable} in vanilla and null on any
 * score written plainly, so any mutation of a conditioned objective throws into whoever made it.
 * Teras' own writes catch it; a plain {@code /scoreboard} command or another mod's write would
 * take the exception instead.</p>
 *
 * <h2>2 · The duplicate ADD, which is the one that disconnects people</h2>
 *
 * <p>When the player has no score in the objective yet, the listener also sends them a
 * {@code ClientboundSetObjectivePacket} ADD. It keeps no record of having done so, and neither does
 * {@code ServerTickHandler.playerLogin}, which sends the very same ADD to every joining player.
 * Both fire during a single login — the first score written at login makes the listener run, and it
 * walks the objectives that have not been reached yet — so the client is told to add the same
 * objective twice and dies with <i>"An objective with the name … already exists!"</i>, which is a
 * protocol error and a disconnect.</p>
 *
 * <p><b>The login one is the one worth keeping</b>: it fires exactly once per join, for every
 * conditioned objective, which is precisely the sync a joining client needs. This one fires an
 * unbounded number of times and only ever repeats it. So the display-slot count it guards that
 * branch with is redirected to report "displayed" and the branch never runs. The count is read for
 * nothing else in the method.</p>
 *
 * <p>Both injections are {@code require = 1} even though the target is a lambda and lambda names
 * are not stable across builds. Silence here is not safer than a boot failure: if these stop
 * applying, the server accepts nobody.</p>
 */
@Mixin(CustomNpcs.class)
public abstract class CnpcScoreListenerMixin {

    @Redirect(
            method = "lambda$serverstart$2(Lnet/minecraft/server/ServerScoreboard;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;of(Ljava/lang/Object;)Ljava/util/Optional;"),
            require = 2)
    private static Optional<?> teras$nullableScoreFields(Object value) {
        return Optional.ofNullable(value);
    }

    @Redirect(
            method = "lambda$serverstart$2(Lnet/minecraft/server/ServerScoreboard;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/ServerScoreboard;getObjectiveDisplaySlotCount(Lnet/minecraft/world/scores/Objective;)I"),
            require = 1)
    private static int teras$leaveTheAddToTheLoginSync(
            net.minecraft.server.ServerScoreboard scoreboard, net.minecraft.world.scores.Objective objective) {
        return 1;
    }
}
