package es.boffmedia.teras.dungeon.instance;

/**
 * Reads the stage out of a dialogue option's command.
 *
 * <h2>Why the command is the declaration</h2>
 *
 * <p>El Guardián's descent options run
 * {@code /teras dungeon entrada iniciar @dp <etapa> [laberinto] [perdido]}, so the option already
 * says which depth it opens. Teras can therefore decide whether a player may see it <b>without any
 * new authoring syntax</b> — no marker on the option, no naming convention, nothing for an operator
 * to keep in sync. Whatever the option does is what it is judged on.</p>
 *
 * <p>This replaces gating by CustomNPCs' availability screen, which cannot work here twice over:
 * a Command option is the only type that can start a run and is the one type CNPC will never hide
 * (mimir 65), and the scoreboard objective its conditions need cannot exist in this modpack at all
 * without disconnecting clients (mimir 120).</p>
 *
 * <p>No Minecraft and no CustomNPCs here, so the parsing is unit-testable — which matters more than
 * usual for a rule whose failure mode is an option silently vanishing from an operator's dialogue.</p>
 */
public final class DescentCommand {
    private DescentCommand() {}

    /** The subcommand that starts a run; anything else is none of our business. */
    private static final String MARKER = "iniciar";

    /**
     * The stage {@code command} would start a run at, or <b>0</b> when it is not a descent command
     * or does not parse.
     *
     * <p>Zero means "not ours, leave it alone". Every uncertainty resolves that way on purpose:
     * removing an option an operator authored is far worse than showing one too many, and a
     * mistyped command must never make a dialogue quietly lose entries.</p>
     */
    public static int stageOf(String command) {
        if (command == null || !command.contains(MARKER)) {
            return 0;
        }
        String[] parts = command.trim().split("\\s+");
        for (int i = 0; i + 2 < parts.length; i++) {
            if (!parts[i].equals(MARKER)) {
                continue;
            }
            // parts[i + 1] is the player argument (@dp, a name, a selector); the stage follows it.
            try {
                return Math.max(0, Integer.parseInt(parts[i + 2]));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
