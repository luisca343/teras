package es.boffmedia.teras.util.objects;

/**
 * One entry of the {@code getSpawns} response sent to the SmartRotom web, ported 1:1 from the 1.16.5
 * {@code util.objects.post.PokedexSpawnChance}. Serialized to JSON by Gson, so the field names are
 * the contract the web reads: {@code dex, species, form, palette, rarity, percentage}.
 */
public class PokedexSpawnChance {
    private final int dex;
    private final String species;
    private final String form;
    private final String palette;
    private final float rarity;
    private final double percentage;

    public PokedexSpawnChance(int dex, String species, String form, String palette, float rarity, double percentage) {
        this.dex = dex;
        this.species = species;
        this.form = form;
        this.palette = palette;
        this.rarity = rarity;
        this.percentage = percentage;
    }
}
