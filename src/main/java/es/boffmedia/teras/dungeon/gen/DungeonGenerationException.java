package es.boffmedia.teras.dungeon.gen;

import java.util.List;

/**
 * Every generation attempt failed validation. Carries the seed so the failing case is reproducible
 * — the legacy generator's answer to constraint failure was to ship the broken floor (its
 * validator call was commented out).
 */
public class DungeonGenerationException extends RuntimeException {

    private final String seedString;
    private final List<String> lastErrors;

    public DungeonGenerationException(int stage, String seedString, int attempts, List<String> lastErrors) {
        super("Dungeon generation failed after " + attempts + " attempts (stage " + stage
                + ", seed \"" + seedString + "\"): " + String.join("; ", lastErrors));
        this.seedString = seedString;
        this.lastErrors = List.copyOf(lastErrors);
    }

    public String seedString() {
        return seedString;
    }

    public List<String> lastErrors() {
        return lastErrors;
    }
}
