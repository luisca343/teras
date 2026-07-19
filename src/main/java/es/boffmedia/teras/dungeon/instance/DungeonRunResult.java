package es.boffmedia.teras.dungeon.instance;

import java.util.List;

/**
 * What a finished run reports to the backend — completed or abandoned, both are worth a row.
 * Assembled by {@link DungeonRunManager} at the end of a run and handed to
 * {@code SmartRotomService.saveDungeonRun}; nothing here touches the world, so the wire shape can
 * be asserted in a test without a server.
 *
 * <p>Coins are run-level rather than per-player because the purse is shared — a per-participant
 * coin column would have to invent a split that the game never made.</p>
 */
public record DungeonRunResult(
        String seed,
        int startStage,
        int endStage,
        int stagesCleared,
        boolean completed,
        long durationMs,
        List<String> curses,
        int coinsEarned,
        int coinsSpent,
        int coinsConverted,
        List<Participant> participants) {

    /**
     * @param abandoned whether this member walked out before the run ended, as opposed to being
     *                  there when it finished either way
     */
    public record Participant(String uuid, String name, int deaths, boolean abandoned) {}
}
