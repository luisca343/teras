package es.boffmedia.teras.karts.mode;

/**
 * The ordinary race: everyone lines up, votes to start, and drives the set number of laps. First
 * across the line wins; the race ends when nobody is still driving.
 *
 * <p>This is what the 1.16.5 karts did, and it stays the default.</p>
 */
public final class ClassicMode implements RaceMode {

    public static final String ID = "clasica";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Carrera clásica";
    }
}
