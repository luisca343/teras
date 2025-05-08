package es.boffmedia.teras.util.objects.post;

public class PokedexSpawnChance {
    int dex;

    String species;

    String form;

    String palette;

    float rarity;

    double percentage;


    public PokedexSpawnChance(int dex, String species, String form, String palette, float rarity, double percentage) {
        this.dex = dex;
        this.species = species;
        this.form = form;
        this.palette = palette;
        this.rarity = rarity;
        this.percentage = percentage;
    }

    public String toString() {
        return "PokedexSpawnChance{dex=" + this.dex + ", species='" + this.species + '\'' + ", form='" + this.form + '\'' + ", palette='" + this.palette + '\'' + ", rarity=" + this.rarity + ", percentage=" + this.percentage + '}';
    }

}
