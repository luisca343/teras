package es.boffmedia.teras.model.battle;

import com.pixelmonmod.pixelmon.api.pokemon.Element;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.List;

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
    private List<MoveData> moves;
    private int[] ivs;
    private int[] evs;
    private int[] stats;
    private int hp;
    private String gender;
    private String ball;
    private String[] types;
    private String status;

    public PokemonData(Pokemon pokemon) {
        dex = pokemon.getSpecies().getDex();
        species = pokemon.getSpecies().getName();
        form = pokemon.getForm().getName();
        palette = pokemon.getPalette().getName();
        name = pokemon.getDisplayName();
        level = pokemon.getPokemonLevel();
        item = pokemon.getHeldItemAsItemHeld().getDescriptionId();
        ability = pokemon.getAbility().getLocalizedName();
        nature = pokemon.getNature().getLocalizedName();
        gender = pokemon.getGender().name();
        status = pokemon.getStatus().type.name();

        List<Element> types = pokemon.getSpecies().getForm(pokemon.getForm().getName()).getTypes();
        this.types = new String[types.size()];
        for (int i = 0; i < types.size(); i++) {
            this.types[i] = types.get(i).getLocalizedName();
        }

        ball = pokemon.getBall().getName();

        moves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (pokemon.getMoveset().get(i) == null) continue;
            moves.add(new MoveData(
                    pokemon.getMoveset().get(i).getMove().getLocalizedName(),
                    pokemon.getMoveset().get(i).getMove().getAttackType().getName(),
                    pokemon.getMoveset().get(i).getMove().getAttackCategory().name(),
                    pokemon.getMoveset().get(i).getMove().getBasePower(),
                    pokemon.getMoveset().get(i).getMove().getAccuracy()
            ));
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

        hp = pokemon.getHealth();
    }

    public static class MoveData {
        private String name;
        private String type;
        private String category;
        private int power;
        private int accuracy;

        public MoveData(String name, String type, String category, int power, int accuracy) {
            this.name = name;
            this.type = type;
            this.category = category;
            this.power = power;
            this.accuracy = accuracy;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getPower() {
            return power;
        }

        public void setPower(int power) {
            this.power = power;
        }

        public int getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(int accuracy) {
            this.accuracy = accuracy;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }

    // Getters and setters for other fields...
}