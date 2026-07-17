package es.boffmedia.teras.dex.api;

/**
 * What a SmartRotom scan learned about one Pokémon, in engine-neutral terms.
 *
 * <p>{@code dex} is the <b>national</b> dex number — the only species key both engines expose, and what
 * the web's {@code openDex(dex, form)} takes. {@code species} is the engine's own species name, which
 * the camera reports alongside it. {@code palette} is Pixelmon's palette name; Cobblemon has no
 * palettes and reports {@code shiny}/{@code none}.</p>
 */
public record DexScan(int dex, String species, String form, String palette) {

    /** What the web and backend expect for "no form" / "no palette". */
    public static final String NONE = "none";

    /**
     * A scan with {@code form}/{@code palette} normalized to {@link #NONE}. Build scans through here:
     * Pixelmon reports a default as empty and Cobblemon as null, and a null would drop the field from
     * the request body entirely.
     */
    public static DexScan of(int dex, String species, String form, String palette) {
        return new DexScan(dex, species == null ? "" : species, name(form), name(palette));
    }

    private static String name(String value) {
        return (value == null || value.isEmpty()) ? NONE : value;
    }
}
