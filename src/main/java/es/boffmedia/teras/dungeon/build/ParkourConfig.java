package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * El plomo's hands on ParCool. ParCool is <b>not</b> a Teras dependency: this drives its own
 * limitation commands as the server, so a server without it plays the floor unmodified.
 *
 * <p>The action names belong to ParCool and are the one part of this that can rot — verify them on
 * the live server with {@code /parcool limitation get global} and fix them in config.yml.</p>
 */
public record ParkourConfig(boolean enabled, List<String> limitCommands,
                            List<String> restoreCommands) {

    private static final List<String> ACTIONS = List.of("WallJump", "HorizontalWallRun", "CatLeap",
            "ClingToCliff", "Dive", "PoleClimb");

    static ParkourConfig defaults() {
        List<String> limit = new ArrayList<>();
        List<String> restore = new ArrayList<>();
        for (String action : ACTIONS) {
            limit.add("parcool limitation set individual of %player% boolean " + action + " false");
            restore.add("parcool limitation set individual of %player% boolean " + action + " true");
        }
        return new ParkourConfig(true, List.copyOf(limit), List.copyOf(restore));
    }

    static ParkourConfig read(YamlConfig yaml, ParkourConfig previous) {
        YamlConfig parkour = yaml.section("parcool");
        return new ParkourConfig(
                parkour.bool("habilitado", previous.enabled),
                commands(parkour, "comandosQuitar", previous.limitCommands),
                commands(parkour, "comandosDevolver", previous.restoreCommands));
    }

    /**
     * Replaces the list, or keeps the built-in one when the key is absent. Never merges: a
     * half-overridden command list would take the moveset away and give back something else, which
     * is worse than either doing nothing or doing all of it.
     */
    private static List<String> commands(YamlConfig section, String key, List<String> fallback) {
        List<Object> raw = section.list(key);
        if (raw.isEmpty()) {
            return fallback;
        }
        List<String> parsed = new ArrayList<>();
        for (Object line : raw) {
            String command = String.valueOf(line).trim();
            if (!command.isEmpty()) {
                parsed.add(command);
            }
        }
        return List.copyOf(parsed);
    }
}
