package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTierRegistry;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.TeamSelectionRegistry;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.*;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import es.boffmedia.teras.util.data.Scoreboard;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.awt.*;
import java.util.List;

public class NPCTerasBattle extends TerasBattle {
    BattleConfig battleConfig;
    ServerPlayerEntity player;
    public MobEntity npcEntity;

    public NPCTerasBattle(BattleController battle) {
        super(battle);
    }

    public NPCTerasBattle(ServerPlayerEntity player, BattleConfig configCombateEntrenador) {
        super(null);
        this.player = player;
        this.battleConfig = configCombateEntrenador;
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
        br = br.set(BattleRuleRegistry.TEAM_PREVIEW, false);

        br = br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getPlayerPkmCount());
        br = br.set(BattleRuleRegistry.BATTLE_TYPE, battleConfig.getBattleType());
        br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getRivalPkmCount());

        setBattleType(battleConfig.getBattleType().toString());
        
        br = br.set(TerasBattleRuleRegistry.SPECIAL_BATTLE, true);


        if(battleConfig.getRivalPkmCount() >= 6) {
            battle = BattleRegistry.startBattle(getPlayerParticipant(), getRivalParticipant());
            Teras.getLBC().addTerasBattle(battle.battleIndex, this);

            npcEntity.remove();
            npcEntity = null;
        } else if(battleConfig.getRivalPkmCount() < 6) {
            TeamSelectionRegistry.Builder test =
                    TeamSelectionRegistry
                            .builder()
                            .members(getPlayerParticipant().getEntity(), getRivalParticipant().getEntity())
                            .battleRules(br)
                            .showOpponentTeam()
                            .closeable()
                            .battleStartConsumer(bc -> {
                                battle = bc;
                                Teras.getLBC().addTerasBattle(bc.battleIndex, this);

                                // First we need to get the team of the player

                                BattleParticipant part = null;

                                for (BattleParticipant participant : bc.participants) {
                                    if(participant instanceof PlayerParticipant){
                                        part = participant;
                                    }
                                }



                                npcEntity.remove();
                                npcEntity = null;
                            })
                            .cancelConsumer(ts -> {
                                Teras.LOGGER.error("CANCELADO");
                            });
            test.start();
        }

    }


    /* Helper methods */

    public PlayerPartyStorage getPlayerParty(){
        return StorageProxy.getParty(player);
    }

    public int getHighestPlayerLevel(){
        return getPlayerParty().getHighestLevel();
    }

    public int getRivalTeamLevel(){
        return battleConfig.calculateTeamLevel(getHighestPlayerLevel());
    }


    /* Participants */

    public PlayerParticipant getPlayerParticipant(){
        if(p1 !=null) return (PlayerParticipant) p1;
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
        SpecialNpcTrainer npc = new SpecialNpcTrainer(player.level);
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
            Teras.getLogger().info("Añadiendo pokemon " + i + " al entrenador "
                    + npc.getName().getString() + " con nombre " + pkm.getDisplayName());
            npc.getPokemonStorage().set(i, pkm);

            i++;
            if (i == battleConfig.getNumPkmRival()) break;
        }
        return new TrainerParticipant(npc, 1);
    }

    public void setEntity(MobEntity entity) {
        this.npcEntity = entity;
    }

    public BattleConfig getBattleConfig() {
        return battleConfig;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

}
