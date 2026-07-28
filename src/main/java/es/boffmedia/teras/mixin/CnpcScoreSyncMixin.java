package es.boffmedia.teras.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.ServerTickHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Optional;

/**
 * Turns CustomNPCs' {@code Optional.of} into {@code Optional.ofNullable} where it builds a score
 * packet at login. <b>This is the fix that makes conditioned scoreboard objectives usable at all in
 * this pack.</b>
 *
 * <h2>The bug being patched</h2>
 *
 * <p>{@code ServerTickHandler.playerLogin} walks {@code Availability.scores} — every objective any
 * dialogue's availability names — and sends each one to the joining player:</p>
 *
 * <pre>new ClientboundSetScorePacket(name, objective, access.get(),
 *         Optional.of(access.display()), Optional.of(info.numberFormat()))</pre>
 *
 * <p>Both {@code display()} and {@code numberFormat()} are {@code @Nullable} in vanilla, and both
 * are null on any score written with a plain {@code set(int)}. {@code Optional.of} throws, the
 * throw escapes {@code PlayerList.placeNewPlayer}, and the player is kicked with <i>"Invalid player
 * data"</i> — on every attempt, so the server is unjoinable for as long as the objective exists.
 * The vanilla packet takes {@code Optional.empty()} for both quite happily; that is what the field
 * being null means.</p>
 *
 * <p>Seeding those two fields ({@code DungeonObjectives.seed}) fixes the objectives Teras owns, but
 * only those: the set also holds whatever else the pack's dialogues condition on. Patching the call
 * is what covers all of them.</p>
 *
 * <p>Both {@code Optional.of} calls in the class are these two, so the redirect matches exactly
 * twice and nothing else in CustomNPCs is touched.</p>
 *
 * <h2>2 · One pass over the levels, not one per level</h2>
 *
 * <p>The method's outer loop is {@code for (ServerLevel level : server.getAllLevels())} and its
 * first act is {@code level.getScoreboard()} — but the scoreboard is <b>server-wide</b>, so every
 * level hands back the same one and the whole body runs identically once per dimension. The body
 * sends a {@code ClientboundSetObjectivePacket} ADD, and the client throws
 * {@code "An objective with the name … already exists!"} on the second one, which is a protocol
 * error and a disconnect. On a pack with more than one dimension this method could never have
 * worked, for any conditioned objective.</p>
 *
 * <p>So the level list is redirected to the overworld alone. Nothing is skipped: the other levels
 * were re-doing the same work on the same scoreboard.</p>
 *
 * <p>Vanilla sends none of these itself — {@code updateEntireScoreboard} only sends objectives that
 * are in a <b>display slot</b>, which these never are — so this method's single remaining ADD is
 * the one and only one the client gets, and it has to stay.</p>
 */
@Mixin(ServerTickHandler.class)
public abstract class CnpcScoreSyncMixin {

    @Redirect(
            method = "playerLogin",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;of(Ljava/lang/Object;)Ljava/util/Optional;"),
            require = 2)
    private Optional<?> teras$nullableScoreFields(Object value) {
        return Optional.ofNullable(value);
    }

    @Redirect(
            method = "playerLogin",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"),
            require = 1)
    private Iterable<ServerLevel> teras$onceNotPerDimension(MinecraftServer server) {
        return List.of(server.overworld());
    }
}
