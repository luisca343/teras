package es.boffmedia.teras.util.objects._old.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;

public class PokemonData {
    private int dex;
    private String nature;
    private String species;
    private String form;
    private String palette;
    private String name;
    private int level;
    private String item;
    private String ability;
    private String[] moves;
    private int[] ivs;
    private int[] evs;
    private int[] stats;

    public PokemonData(Pokemon pokemon) {
        dex = pokemon.getSpecies().getDex();
        species = pokemon.getSpecies().getName();
        form = pokemon.getForm().getName();
        palette = pokemon.getPalette().getName();
        name = pokemon.getDisplayName();
        level = pokemon.getPokemonLevel();
        item = pokemon.getHeldItemAsItemHeld().getLocalizedName();
        ability = pokemon.getAbility().getLocalizedName();
        nature = pokemon.getNature().getLocalizedName();
        moves = new String[4];
        for (int i = 0; i < 4; i++) {
            if(pokemon.getMoveset().get(i) == null) continue;
            moves[i] = pokemon.getMoveset().get(i).getMove().getLocalizedName();
        }
        ivs = new int[6];
        for (int i = 0; i < 6; i++) {
            if (pokemon.getIVs() == null) continue;
            ivs[i] = pokemon.getIVs().getArray()[i];
        }
        evs = new int[6];
        for (int i = 0; i < 6; i++) {
            if (pokemon.getEVs() == null) continue;
            evs[i] = pokemon.getEVs().getArray()[i];
        }
        stats = new int[6];
        stats[0] = pokemon.getStats().getHP();
        stats[1] = pokemon.getStats().getAttack();
        stats[2] = pokemon.getStats().getDefense();
        stats[3] = pokemon.getStats().getSpecialAttack();
        stats[4] = pokemon.getStats().getSpecialDefense();
        stats[5] = pokemon.getStats().getSpeed();
    }

    public int getDex() {
        return dex;
    }

    public void setDex(int dex) {
        this.dex = dex;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public String getPalette() {
        return palette;
    }

    public void setPalette(String palette) {
        this.palette = palette;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getAbility() {
        return ability;
    }

    public void setAbility(String ability) {
        this.ability = ability;
    }

    public String[] getMoves() {
        return moves;
    }

    public void setMoves(String[] moves) {
        this.moves = moves;
    }

    public int[] getIvs() {
        return ivs;
    }

    public void setIvs(int[] ivs) {
        this.ivs = ivs;
    }

    public int[] getEvs() {
        return evs;
    }

    public void setEvs(int[] evs) {
        this.evs = evs;
    }

    public int[] getStats() {
        return stats;
    }

    public void setStats(int[] stats) {
        this.stats = stats;
    }
}

