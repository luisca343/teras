package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.Teras;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Where finished races are announced. Rewards, leaderboards, Grand Prix standings and the backend
 * report all subscribe here rather than being called from the race itself, so a race does not have
 * to know what anybody does with its result — and a failure in one consumer cannot stop the others.
 */
public final class RaceResults {
    private RaceResults() {}

    private static final List<Consumer<RaceResult>> LISTENERS = new ArrayList<>();

    /** Registers a consumer of finished races. Called during common setup. */
    public static synchronized void addListener(Consumer<RaceResult> listener) {
        LISTENERS.add(listener);
    }

    /** Announces a finished race. Server thread. */
    public static synchronized void publish(RaceResult result) {
        for (Consumer<RaceResult> listener : LISTENERS) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                Teras.LOGGER.error("Karts: a race-result listener failed for '{}'", result.trackName(), e);
            }
        }
    }
}
