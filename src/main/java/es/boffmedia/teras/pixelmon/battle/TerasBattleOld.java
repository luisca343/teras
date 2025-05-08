package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTierRegistry;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStats;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.TeamSelectionRegistry;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.TrainerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.npcs.NPCTrainer;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import es.boffmedia.teras.util.objects._old.pixelmon.PosicionEquipo;
import es.boffmedia.teras.util.data.Scoreboard;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.awt.*;
import java.util.*;
import java.util.List;

public class TerasBattleOld {
    BattleController battle;
    public ServerPlayerEntity player;
    public BattleConfig battleConfig;
    public MobEntity entity;
    public String log;

    public PlayerParticipant p1;
    public BattleParticipant p2;
    public BattleParticipant p3;
    public BattleParticipant p4;

    LogHelper logHelper;
    private ArrayList<PosicionEquipo> fainted = new ArrayList<>();
    boolean teamSelection = false;

    private HashMap<String, PixelmonWrapper> activePkm = new HashMap<>();
    private HashMap<String, BattleStats> activeStats = new HashMap<>();


    //private HashMap<Integer,HashMap<Integer, PixelmonWrapper>> activePokemon = new HashMap<>();
    //private HashMap<Integer,HashMap<Integer, BattleStats>> activeStats = new HashMap<>();

    ArrayList<String> pokemonInit = new ArrayList<>();
    ArrayList<String> switchInit = new ArrayList<>();

    public ArrayList<String> delayedMessages = new ArrayList<>();

    public List<Pokemon> playerTeam = new ArrayList<>();

    public TerasBattleOld() {
        this.player = player;
        this.battleConfig = new BattleConfig();
        this.log = "";
        logHelper = new LogHelper();
    }

    public TerasBattleOld(ServerPlayerEntity player, BattleConfig battleConfig) {
        this.player = player;
        this.battleConfig = battleConfig;
        this.log = "";
        logHelper = new LogHelper();
    }

    public void logEvent(BattleAction event){
        //TerasBattleLog.logEvent(event, this);
    }

    public int getHighestPlayerLevel(){
        return getPlayerParty().getHighestLevel();
    }

    public int getRivalTeamLevel(){
        return battleConfig.calculateTeamLevel(getHighestPlayerLevel());
    }

    public void start() {
        /* Register the battle */
        battleConfig.setNivelEquipo(getHighestPlayerLevel());

        if (battleConfig.healBeforeStart()) {
            getPlayerParty().heal();
        }


        Scoreboard.getOrCreateObjective(player, battleConfig.getNombreArchivo());

        BattleRules br = new BattleRules();
        br.setNewClauses(battleConfig.getNormas());
        br = br.set(BattleRuleRegistry.TEAM_SELECT, true);
        br = br.set(BattleRuleRegistry.TEAM_PREVIEW, true);

        br = br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getPlayerPkmCount());
        //br = br.set(BattleRuleRegistry.TURN_TIME, 45);
        br = br.set(BattleRuleRegistry.BATTLE_TYPE, battleConfig.getBattleType());

        br = br.set(TerasBattleRuleRegistry.SPECIAL_BATTLE, true);



        TeamSelectionRegistry.Builder test =
                TeamSelectionRegistry
                        .builder()
                        .members(getPlayerParticipant().getEntity(), getRivalParticipant().getEntity())
                        .battleRules(br)
                        .showOpponentTeam()
                        .closeable()
                        .battleStartConsumer(bc -> {
                            battle = bc;
                            //Teras.getLBC().addTerasBattle(bc.battleIndex, this);
                            //TerasBattleLog.appendStartBattle(this);

                            // First we need to get the team of the player

                            BattleParticipant part = null;

                            for (BattleParticipant participant : bc.participants) {
                                if(participant instanceof PlayerParticipant){
                                    part = participant;
                                }
                            }


                            for (PixelmonWrapper pixelmonWrapper : part.allPokemon) {
                                playerTeam.add(pixelmonWrapper.pokemon);
                            }



                            entity.remove();
                            entity = null;
                        })
                        .cancelConsumer(ts -> {
                            Teras.LOGGER.error("CANCELADO");
                        });
        test.start();

    }

    public BattleController getBattle(){
        return battle;
    }


    public String getName1(){
        return player.getDisplayName().getString();
    }

    public String getName2(){
        return getRivalParticipant().getDisplayName();
    }

    public PlayerPartyStorage getPlayerParty(){
        return StorageProxy.getParty(player);
    }

    public PlayerParticipant getPlayerParticipant(){
        if(p1 !=null) return p1;
        //List<Pokemon> pokemon = getPlayerParty().findAll(Pokemon::canBattle);
        return new PlayerParticipant(player, (Pokemon) null);
    }

    public BattleParticipant getRivalParticipant(){
        if(p2 !=null) return p2;
        if (battleConfig.esEntrenador()) {
            BattleParticipant part = getPartRivalEntrenador();
            p2 = part;
            return part;
        }
        else {
            BattleParticipant part = getPartRivalSalvaje();
            p2 = part;
            return part;
        }
    }

    protected BattleParticipant getPartRivalSalvaje() {
        Pokemon pkm = battleConfig.getFirstPokemon();
        PixelmonEntity pixelmon = pkm.getOrCreatePixelmon();
        player.level.addFreshEntity(pixelmon);

        setEntity(pixelmon);

        return new WildPixelmonParticipant(pixelmon);
    }

    protected TrainerParticipant getPartRivalEntrenador() {
        NPCTrainer npc = new NPCTrainer(player.level);
        npc.setBossTier(BossTierRegistry.NOT_BOSS);



        if(getRivalTeamLevel() > 100){
            int niveles = getRivalTeamLevel() - 100;
            BossTier tier = new BossTier("+"+niveles,"+"+niveles, false, 0, Color.BLACK, 1.0f, false,0.0, 0.0, "PALETA", 1.0, niveles);
            npc.setBossTier(tier);
        }

        npc.setBattleAIMode(battleConfig.getIA());
        if(!npc.isAddedToWorld()){
            npc.setTextureIndex(-1);
            String name = "aquaboss";
            npc.setName(battleConfig.getNombre());
            npc.setCustomSteveTexture(name);

            npc.setPos(player.getX(), player.getY(), player.getZ());
            player.level.addFreshEntity(npc);


            //npc.addEffect(new EffectInstance(Effects.INVISIBILITY, Integer.MAX_VALUE, 0, true, true));
            setEntity(npc);
        }

        List<Pokemon> equipoEntrenador = battleConfig.getEquipo();
        if(equipoEntrenador.isEmpty()){
            return null;
        }
        int i = 0;
        for (Pokemon pkm : equipoEntrenador) {
            npc.getPokemonStorage().set(i, pkm);

            i++;
            if (i == 6) break;
        }

        return new TrainerParticipant(npc, battleConfig.getNumPkmRival());
    }

    public BattleConfig getBattleConfig() {
        return battleConfig;
    }

    public void setBattleConfig(BattleConfig battleConfig) {
        this.battleConfig = battleConfig;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    public MobEntity getEntity() {
        return entity;
    }

    public void setEntity(MobEntity entity) {
        this.entity = entity;
    }

    public void setLog(String log){
        this.log = log;
    }

    public void appendLog(String log){
        this.log += log;
    }

    public String getLog(){
        return log;
    }

    public int getBattleId() {
        return battle.battleIndex;
    }

    public String getPositionString(int team, int position){
        return "p" + team + TerasBattleLog.getPositionLetter(position);
    }

    public boolean swapv2(int team, int position, PixelmonWrapper newPokemon) {
        if(activePkm.containsValue(newPokemon)){
            Teras.LOGGER.info("El pokemon ya esta en el equipo, no se puede añadir.");
            return false;
        }

        activePkm.put(getPositionString(team, position), newPokemon);
        return true;
    }

    public boolean swapv2(PixelmonWrapper pokemon, PixelmonWrapper switchingTo) {
        // Find the pokemon in the activePkm map
        for (Map.Entry<String, PixelmonWrapper> entry : activePkm.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                // Replace the pokemon with the new one
                activePkm.put(entry.getKey(), switchingTo);
                return true;
            }
        }
        return false;
    }

    /*
    public boolean swapPokemon(int team, int position, PixelmonWrapper newPokemon) {
        if(!activePokemon.containsKey(team)){
            activePokemon.put(team, new HashMap<>());
        }

        /*
        if(activePokemon.get(team).containsValue(newPokemon)){
            Teras.LOGGER.error("El pokemon ya esta en el equipo, no se puede añadir.");
            return false;
        }


       activePokemon.get(team).put(position, newPokemon);
        return true;
    }

    public boolean swapPokemon(PixelmonWrapper pokemon, PixelmonWrapper switchingTo) {
        for (Map.Entry<Integer, HashMap<Integer, PixelmonWrapper>> teamEntry : activePokemon.entrySet()) {
            for (Map.Entry<Integer, PixelmonWrapper> positionEntry : teamEntry.getValue().entrySet()) {
                if (positionEntry.getValue().equals(pokemon)) {
                    teamEntry.getValue().put(positionEntry.getKey(), switchingTo);
                    return true;
                }
            }
        }
        return false;
    }*/

    public BattleStats getStats(PixelmonWrapper pokemon){
        for (Map.Entry<String, PixelmonWrapper> entry : activePkm.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                return activeStats.get(entry.getKey());
            }
        }
        return null;
    }

    /*
    public BattleStats getStats(PixelmonWrapper pokemon) {
        for (Map.Entry<Integer, HashMap<Integer, PixelmonWrapper>> teamEntry : activePokemon.entrySet()) {
            for (Map.Entry<Integer, PixelmonWrapper> positionEntry : teamEntry.getValue().entrySet()) {
                if (positionEntry.getValue().equals(pokemon)) {
                    HashMap<Integer, BattleStats> stats = activeStats.get(teamEntry.getKey());
                    if (stats == null) {
                        return null;
                    }
                    return stats.get(positionEntry.getKey());
                }
            }
        }
        return null;
    }*/

    public void setStats(PixelmonWrapper pokemon, BattleStats currentStats) {
        for (Map.Entry<String, PixelmonWrapper> entry : activePkm.entrySet()) {
            if (entry.getValue().equals(pokemon)) {
                BattleStats copy = new BattleStats(currentStats);
                activeStats.put(entry.getKey(), copy);
            }
        }
    }

    /*
    public void setStats(PixelmonWrapper pokemon, BattleStats currentStats) {
        for (Map.Entry<Integer, HashMap<Integer, PixelmonWrapper>> teamEntry : activePokemon.entrySet()) {
            for (Map.Entry<Integer, PixelmonWrapper> positionEntry : teamEntry.getValue().entrySet()) {
                if (positionEntry.getValue().equals(pokemon)) {
                    if(!activeStats.containsKey(teamEntry.getKey())){
                        activeStats.put(teamEntry.getKey(), new HashMap<>());
                    }
                    // Create a copy of currentStats and add it to the map
                    BattleStats copy = new BattleStats(currentStats);
                    activeStats.get(teamEntry.getKey()).put(positionEntry.getKey(), copy);
                }
            }
        }
    }*/

    public PixelmonWrapper getActivePokemon(int team, int position) {
        String key = getPositionString(team, position);
        if (!activePkm.containsKey(key)) {
            return null;
        }

        return activePkm.get(key);
    }
    /*
    public PixelmonWrapper getActivePokemon(int team, int position){
        if(!activePokemon.containsKey(team)){
            return null;
        }
        return activePokemon.get(team).get(position);
    }*/

    public int getPositionNumber(char letter){
        return Arrays.asList(letters).indexOf(letter);
    }

    public HashMap<Integer, PixelmonWrapper> getActiveTeam(int team){
        HashMap<Integer, PixelmonWrapper> result = new HashMap<>();
        for (Map.Entry<String, PixelmonWrapper> entry : activePkm.entrySet()) {
            if (entry.getKey().startsWith("p" + team)) {
                int position = getPositionNumber(entry.getKey().charAt(2));
                result.put(position, entry.getValue());
            }
        }

        Teras.getLogger().warn("Active team: " + result.toString());
        return result;
    }


    /*
    public HashMap<Integer, PixelmonWrapper> getActiveTeam(int team){
        if(!activePokemon.containsKey(team)){
            return new HashMap<>();
        }
        return activePokemon.get(team);
    }*/


    final char[] letters = {'a', 'b', 'c', 'd', 'e', 'f'};

    public String getPositionString(PixelmonWrapper pkm){
        String result = null;
        for (Map.Entry<String, PixelmonWrapper> entry : activePkm.entrySet()) {
            if (entry.getValue().equals(pkm)) {
                result = entry.getKey();
                break;
            }
        }
        return result;
    }

    /*
    public String getPositionString(PixelmonWrapper pkm){
        String result = null;
        for (Map.Entry<Integer, HashMap<Integer, PixelmonWrapper>> teamEntry : activePokemon.entrySet()) {
            for (Map.Entry<Integer, PixelmonWrapper> positionEntry : teamEntry.getValue().entrySet()) {
                if (positionEntry.getValue().equals(pkm)) {
                    result = "p" + teamEntry.getKey() + letters[positionEntry.getKey()];
                    break;
                }
            }
            if (result != null) {
                break;
            }
        }
        return result;
    }*/

    public ArrayList<String> getPokemonInit() {
        return pokemonInit;
    }

    public void setPokemonInit(ArrayList<String> pokemonInit) {
        this.pokemonInit = pokemonInit;
    }

    public ArrayList<String> getSwitchInit() {
        return switchInit;
    }

    public void setSwitchInit(ArrayList<String> switchInit) {
        this.switchInit = switchInit;
    }

    public List<Pokemon> getPlayerTeam() {
        return playerTeam;
    }

    public void setPlayerTeam(List<Pokemon> playerTeam) {
        this.playerTeam = playerTeam;
    }
}


