package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * El plomo: the floor that takes your parkour away.
 *
 * <h2>Why by command and not by API</h2>
 *
 * <p>ParCool is a pack mod, not a Teras dependency — compiling against it would make it required to
 * load, which is exactly backwards for a modifier that should simply not happen on a server without
 * it. Its limitation system is per-player and drivable from the console, so this dispatches its
 * commands as the server and treats every failure as "the mod is not here": logged once at debug,
 * never fatal, and the floor plays unmodified.</p>
 *
 * <h2>Why the commands are config</h2>
 *
 * <p>The command grammar belongs to somebody else's mod and can change under us. The templates live
 * in {@code config.yml} with {@code %player%} substituted, so a rename in ParCool is a config edit
 * on a running server rather than a Teras release. If the shipped defaults name an action ParCool
 * does not know, the log says so and nothing else breaks.</p>
 */
public final class ParkourLimits {
    private ParkourLimits() {}

    /** Takes the moveset from everyone currently on the floor. Safe to call when unsupported. */
    static void apply(RunEngine.ActiveFloor floor) {
        run(floor, DungeonsConfig.parkourLimitCommands());
    }

    /** Gives it back. Called on every floor teardown, cursed or not, so a failure cannot strand. */
    static void clear(RunEngine.ActiveFloor floor) {
        run(floor, DungeonsConfig.parkourRestoreCommands());
    }

    private static void run(RunEngine.ActiveFloor floor, List<String> templates) {
        if (!DungeonsConfig.parkourLimitsEnabled() || templates.isEmpty()) {
            return;
        }
        MinecraftServer server = floor.level().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (java.util.UUID id : floor.run().party().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            for (String template : templates) {
                String command = template.replace("%player%", player.getName().getString());
                // The dispatcher swallows its own failures (an unknown command is a source message,
                // not an exception), so this cannot throw into the run loop. A server without
                // ParCool therefore plays the floor unmodified, which is the intended degradation.
                server.getCommands().performPrefixedCommand(source, command);
                Teras.LOGGER.debug("Dungeons: parkour limit command '{}'", command);
            }
        }
    }
}
