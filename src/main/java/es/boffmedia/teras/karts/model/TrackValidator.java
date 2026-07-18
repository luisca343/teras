package es.boffmedia.teras.karts.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a circuit can actually be raced, and says in Spanish what is missing.
 *
 * <p>Run on {@code /karts circuito validar} and again before a race starts — the second time
 * matters, because a track can be edited into an unraceable state after it was last saved.</p>
 */
public final class TrackValidator {
    private TrackValidator() {}

    /** Below three gates a "lap" has no shape, and the ranking spline degenerates into a line. */
    public static final int MIN_CHECKPOINTS = 3;

    public static List<String> validate(KartTrack track) {
        List<String> problems = new ArrayList<>();
        if (track == null) {
            problems.add("El circuito no existe.");
            return problems;
        }
        if (track.checkpoints().size() < MIN_CHECKPOINTS) {
            problems.add("Necesita al menos " + MIN_CHECKPOINTS + " checkpoints (tiene "
                    + track.checkpoints().size() + ").");
        }
        if (track.startingPoints().isEmpty()) {
            problems.add("Necesita al menos un punto de salida.");
        }
        if (track.dimension() == null || track.dimension().isBlank()) {
            problems.add("No tiene dimensión asignada; vuelve a guardarlo desde el mundo del circuito.");
        }

        // A grid slot inside the first gate would count that checkpoint the instant the race
        // starts, handing whoever spawned there a free gate.
        List<TrackCheckpoint> checkpoints = track.checkpoints();
        if (!checkpoints.isEmpty()) {
            TrackCheckpoint first = checkpoints.get(0);
            for (int i = 0; i < track.startingPoints().size(); i++) {
                if (first.contains(track.startingPoints().get(i))) {
                    problems.add("El punto de salida " + (i + 1)
                            + " está dentro del primer checkpoint; muévelo detrás de la línea.");
                }
            }
        }

        for (int i = 0; i < checkpoints.size(); i++) {
            TrackCheckpoint checkpoint = checkpoints.get(i);
            if (checkpoint.midpoint().distanceTo(checkpoint.cornerA()) < 1.0e-6) {
                problems.add("El checkpoint " + (i + 1) + " no tiene volumen; redefínelo con dos esquinas distintas.");
            }
        }

        if (problems.isEmpty() && !track.path().isUsable()) {
            problems.add("No se pudo trazar la línea del circuito; revisa que los checkpoints no estén todos en el mismo punto.");
        }
        return problems;
    }

    public static boolean isRaceable(KartTrack track) {
        return validate(track).isEmpty();
    }
}
