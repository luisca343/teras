package es.boffmedia.teras.pixelmon.battle;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import es.boffmedia.teras.Teras;

import java.util.*;

public class TerasBattle{
    BattleController battle;
    String battleType;

    BattleParticipant p1;
    BattleParticipant p2;
    BattleParticipant p3;
    BattleParticipant p4;

    private HashMap<String, PixelmonWrapper> activePokemon = new HashMap<>();
    private HashMap<String, BattleStats> activeStats = new HashMap<>();

    ArrayList<String> pokemonInit = new ArrayList<>();
    ArrayList<String> switchInit = new ArrayList<>();

    public ArrayList<String> log = new ArrayList<>();
    public ArrayList<String> delayedMessages = new ArrayList<>();

    public TerasBattle(BattleController battle){
        this.battle = battle;
        // The Showdown header is built once when the battle is registered (addTerasBattle).
    }

    public BattleController getBattle(){
        return battle;
    }

    public void logEvent(BattleAction event){
        TerasBattleLog.logEvent(event, this);
    }

    public List<BattleParticipant> getParticipants(){
        return battle.participants;
    }

    public String getParticipantName(BattleParticipant participant) {
        String participantName = "";

        if(participant instanceof PlayerParticipant){
            participantName = "player:" + ((PlayerParticipant) participant).player.getUUID()+":"+((PlayerParticipant) participant).player.getName().getString();
        } else {
            participantName = "npc:" + participant.getName().getString();
        }

        return participantName;
    }

    public ArrayList<String> getLog(){
        return log;
    }

    public String getLogString(){
        StringBuilder sb = new StringBuilder();
        for(String line : log){
            sb.append(line);
            sb.append("\n");
        }
        return sb.toString();
    }

    public void setLog(ArrayList<String> log){
        this.log = log;
    }

    public void appendLog(String line){
        log.add(line);
    }

    /* Pokémon Management */
    public final char[] letters = {'a', 'b', 'c', 'd', 'e', 'f'};

    public String getPositionString(int team, int position){
        return "p" + team + TerasBattleLog.getPositionLetter(position);
    }

    public String getPositionString(PixelmonWrapper pkm){
        String result = null;
        for (Map.Entry<String, PixelmonWrapper> entry : activePokemon.entrySet()) {
            if (entry.getValue().equals(pkm)) {
                result = entry.getKey();
                break;
            }
        }
        return result;
    }

    public int getPositionNumber(char letter){
        return Arrays.asList(letters).indexOf(letter);
    }

    public HashMap<Integer, PixelmonWrapper> getActiveTeam(int team){
        HashMap<Integer, PixelmonWrapper> result = new HashMap<>();
        for (Map.Entry<String, PixelmonWrapper> entry : activePokemon.entrySet()) {
            if (entry.getKey().startsWith("p" + team)) {
                int position = getPositionNumber(entry.getKey().charAt(2));
                result.put(position, entry.getValue());
            }
        }

        return result;
    }

    public PixelmonWrapper getActivePokemon(int team, int position) {
        String key = getPositionString(team, position);
        if (!activePokemon.containsKey(key)) {
            return null;
        }

        return activePokemon.get(key);
    }

    public boolean swapv2(int team, int position, PixelmonWrapper newPokemon) {
        if(activePokemon.containsValue(newPokemon)){
            Teras.LOGGER.info("El pokemon ya esta en el equipo, no se puede añadir.");
            return false;
        }

        activePokemon.put(getPositionString(team, position), newPokemon);
        return true;
    }

    public boolean swapv2(PixelmonWrapper pokemon, PixelmonWrapper switchingTo) {
        // Find the pokemon in the activePkm map
        for (Map.Entry<String, PixelmonWrapper> entry : activePokemon.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                // Replace the pokemon with the new one
                activePokemon.put(entry.getKey(), switchingTo);
                return true;
            }
        }
        return false;
    }


    public BattleStats getStats(PixelmonWrapper pokemon){
        for (Map.Entry<String, PixelmonWrapper> entry : activePokemon.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                return activeStats.get(entry.getKey());
            }
        }
        return null;
    }

    public void setStats(PixelmonWrapper pokemon, BattleStats currentStats) {
        for (Map.Entry<String, PixelmonWrapper> entry : activePokemon.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                BattleStats copy = new BattleStats(currentStats);
                activeStats.put(entry.getKey(), copy);
            }
        }
    }

    public ArrayList<String> getPokemonInit() {
        return pokemonInit;
    }
    public ArrayList<String> getSwitchInit() {
        return switchInit;
    }


    // Getters and Setters


    public BattleParticipant getP1() {
        return p1;
    }

    public List<Pokemon> getP1Team() {
        List<Pokemon> team = new ArrayList<>();
        for (PixelmonWrapper pkm : p1.allPokemon) {
            team.add(pkm.pokemon);
        }
        return team;
    }

    public BattleParticipant getP2() {
        return p2;
    }

    public List<Pokemon> getP2Team() {
        List<Pokemon> team = new ArrayList<>();
        for (PixelmonWrapper pkm : p2.allPokemon) {
            team.add(pkm.pokemon);
        }
        return team;
    }

    public BattleParticipant getP3() {
        return p3;
    }

    public BattleParticipant getP4() {
        return p4;
    }

    public String getBattleType() {
        return battleType;
    }

    public void setBattleType(String battleType) {
        this.battleType = battleType;
    }

    public void setParticipant(int index, BattleParticipant participant) {
        switch (index) {
            case 0:
                p1 = participant;
                break;
            case 1:
                p2 = participant;
                break;
            case 2:
                p3 = participant;
                break;
            case 3:
                p4 = participant;
                break;
        }

    }
}
