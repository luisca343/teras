package es.boffmedia.teras.karts.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.store.TrackStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Owns the {@code /karts} root and hangs every subtree off it.
 *
 * <p><b>Why one owner rather than a class per subtree.</b> Registering the same literal twice makes
 * Brigadier merge the second node's children into the first and <i>discard the second node's
 * requirement</i> — so with separate registrations the permission level of whichever class happened
 * to register first would silently govern the whole tree. Karts spans level 0 (players joining a
 * race) to level 4 (bridge diagnostics), so that would either lock players out or hand them admin
 * commands, depending on class-loading order. The root is therefore left open and each subtree
 * carries its own {@code requires}.</p>
 *
 * <p>The root stays {@code /karts} rather than moving under {@code /teras}: the server's CustomNPCs
 * "TokiKarts" scripts already invoke {@code /karts carrera …}, and keeping the name lets them port
 * without edits.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class KartsCommand {
    private KartsCommand() {}

    /** Circuit editing, presets and selections: builder-level admin. */
    public static final int PERMISSION_ADMIN = 3;
    /** Running races on behalf of players — also what the TokiKarts NPCs use. */
    public static final int PERMISSION_RACE_CONTROL = 2;
    /** Bridge diagnostics. Operator only; not part of the normal surface. */
    public static final int PERMISSION_DEBUG = 4;

    /** Circuit names, for any command that takes one. */
    public static final SuggestionProvider<CommandSourceStack> TRACK_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(TrackStore.names(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("karts");
        CarreraCommands.contribute(root);
        KartProvisioningCommands.contribute(root);
        CircuitoCommands.contribute(root);
        KartsDebugCommand.contribute(root);
        event.getDispatcher().register(root);
    }
}
