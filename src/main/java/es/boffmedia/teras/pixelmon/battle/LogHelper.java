package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.util.objects._old.pixelmon.PosicionEquipo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class LogHelper {
    public LogHelper() { }

    public HashMap<PixelmonWrapper, ArrayList<Integer>> boosts = new HashMap<>();
    
    public void enviarMensajeCambio(PosicionEquipo pos, PixelmonWrapper pk, TerasBattle combate) {
        combate.appendLog("|switch|" + pos.toString() + ": " + getDatosPoke(pk, false) + System.lineSeparator());
    }

    public String getDatosPoke(PixelmonWrapper pk, boolean first){
        String gender = pk.getGender().equals(Gender.FEMALE) ? ", F" :  (pk.getGender().equals(Gender.MALE) ? ", M" : "");
        String shiny = pk.getRealTextureNoCheck().contains("shiny") ? ", shiny" : "";
        String nickname = pk.getNickname();

        String nombre = pk.getSpecies().getName();
        int health = first ? pk.getMaxHealth() : pk.getHealth();

        return nickname + "|" + nombre + gender + shiny + "|" + health + "\\/" + pk.getMaxHealth();
    }

    /*
    public int getPokemonRestantes(PosicionEquipo posObjetivo, TerasBattle combate) {
        // get team
        return posObjetivo.getEquipo() == 1 ? combate.getPlayerParticipant().countAblePokemon() : combate.getRivalParticipant().countAblePokemon();
    }*/

    public int getPlayerContrario(PosicionEquipo posicion) {
        int equipo = posicion.getEquipo();
        if(equipo == 1) return 2;
        else return 1;
    }

    public void setBoosts(PixelmonWrapper pkm, ArrayList<Integer> boosts){
        this.boosts.put(pkm, boosts);
    }

    public ArrayList<Integer> getBoosts(UUID uuid){
        return boosts.get(uuid);
    }

    public void initBoosts(PixelmonWrapper pkm) {
        ArrayList<Integer> boosts = new ArrayList<>();
        for(int i = 0; i < 7; i++) boosts.add(0);
        this.boosts.put(pkm, boosts);
    }

    public enum BattleBoostsType {
        ACCURACY(0, "acc"),
        EVASION(1, "eva"),
        ATTACK(2, "atk"),
        DEFENSE(3, "def"),
        SPECIAL_ATTACK(4, "specialattack"),
        SPECIAL_DEFENSE(5, "specialdefense"),
        SPEED(6, "spe"),
        HP(7, "hp");
        private final int index;
        private final String name;

        private BattleBoostsType(int index, String name) {
            this.index = index;
            this.name = name;
        }

        public static BattleBoostsType fromIndex(int index) {
            for(BattleBoostsType type : BattleBoostsType.values()) {
                if(type.index == index) return type;
            }
            return null;
        }

        public String getName() {
            return name;
        }
    }

    public ArrayList<Integer> getBoostsActuales(PixelmonWrapper pokemon) {
        ArrayList<Integer> boosts = new ArrayList<>();
        //int hp = pokemon.getBattleStats().getStage(BattleStatsType.HP);
        int accuracy = pokemon.getBattleStats().getStage(BattleStatsType.ACCURACY);
        int evasion = pokemon.getBattleStats().getStage(BattleStatsType.EVASION);
        int atk = pokemon.getBattleStats().getStage(BattleStatsType.ATTACK);
        int def = pokemon.getBattleStats().getStage(BattleStatsType.DEFENSE);
        int spatk = pokemon.getBattleStats().getStage(BattleStatsType.SPECIAL_ATTACK);
        int spdef = pokemon.getBattleStats().getStage(BattleStatsType.SPECIAL_DEFENSE);
        int speed = pokemon.getBattleStats().getStage(BattleStatsType.SPEED);

        boosts.add(accuracy);
        boosts.add(evasion);
        boosts.add(atk);
        boosts.add(def);
        boosts.add(spatk);
        boosts.add(spdef);
        boosts.add(speed);

        return boosts;
    }
}
