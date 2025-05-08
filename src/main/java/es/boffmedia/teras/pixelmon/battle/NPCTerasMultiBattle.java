package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTierRegistry;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.TrainerParticipant;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import es.boffmedia.teras.util.data.Scoreboard;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.awt.*;
import java.util.List;

public class NPCTerasMultiBattle extends NPCTerasBattle {
    BattleConfig companionConfig;

    BattleConfig configEnemigo1;
    BattleConfig configEnemigo2;

    public NPCTerasMultiBattle(ServerPlayerEntity player, BattleConfig configCompanero, BattleConfig configEnemigo1, BattleConfig configEnemigo2) {
        super(null);
        this.player = player;
        this.battleConfig = configEnemigo1;
        this.companionConfig = configCompanero;

        this.configEnemigo1 = configEnemigo1;
        this.configEnemigo2 = configEnemigo2;

    }

    public void start() {
        /* Register the battle */
        configEnemigo1.setNivelEquipo(getHighestPlayerLevel());
        configEnemigo2.setNivelEquipo(getHighestPlayerLevel());
        companionConfig.setNivelEquipo(getHighestPlayerLevel());

        if (battleConfig.healBeforeStart()) {
            getPlayerParty().heal();
        }

        Scoreboard.getOrCreateObjective(player, battleConfig.getNombreArchivo());
        BattleRules br = new BattleRules();
        br.setNewClauses(battleConfig.getNormas());
        br = br.set(BattleRuleRegistry.TEAM_SELECT, true);
        br = br.set(BattleRuleRegistry.TEAM_PREVIEW, false);

        br = br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getPlayerPkmCount());
        br = br.set(BattleRuleRegistry.BATTLE_TYPE, BattleType.DOUBLE);
        br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getRivalPkmCount());

        br = br.set(TerasBattleRuleRegistry.SPECIAL_BATTLE, true);

        setBattleType("MULTI");


        BattleParticipant[] team1 = new BattleParticipant[2];
        team1[0] = getPlayerParticipant();
        team1[1] = getPartnerParticipant();
        BattleParticipant[] team2 = new BattleParticipant[2];
        team2[0] = getRivalParticipant(configEnemigo1);
        team2[1] = getRivalParticipant(configEnemigo2);


            battle = BattleRegistry.startBattle(team1, team2, br);
            Teras.getLBC().addTerasBattle(battle.battleIndex, this);

            npcEntity.remove();
            npcEntity = null;
    }


    public BattleParticipant getRivalParticipant(BattleConfig configEnemigo){
        SpecialNpcTrainer npc = new SpecialNpcTrainer(player.level);
        npc.setBossTier(BossTierRegistry.NOT_BOSS);

        if(getRivalTeamLevel() > 100){
            int niveles = getRivalTeamLevel() - 100;
            BossTier tier = new BossTier("+"+niveles,"+"+niveles, false, 0, Color.BLACK, 1.0f, false,0.0, 0.0, "PALETA", 1.0, niveles);
            npc.setBossTier(tier);
        }

        npc.setBattleAIMode(configEnemigo.getIA());
        if(!npc.isAddedToWorld()){
            npc.setTextureIndex(-1);
            String name = "aquaboss";
            npc.setName(configEnemigo.getNombre());
            npc.setCustomSteveTexture(name);

            npc.setPos(player.getX(), player.getY(), player.getZ());
            player.level.addFreshEntity(npc);


            //npc.addEffect(new EffectInstance(Effects.INVISIBILITY, Integer.MAX_VALUE, 0, true, true));
            setEntity(npc);
        }

        List<Pokemon> equipoEntrenador = configEnemigo.getEquipo();
        if(equipoEntrenador.isEmpty()){
            return null;
        }
        int i = 0;
        for (Pokemon pkm : equipoEntrenador) {
            Teras.getLogger().info("Añadiendo pokemon " + i + " al entrenador "
                    + npc.getName().getString() + " con nombre " + pkm.getDisplayName());
            npc.getPokemonStorage().set(i, pkm);

            i++;
            if (i == configEnemigo.getNumPkmRival()) break;
        }
        return new TrainerParticipant(npc, 1);
    }

    public BattleParticipant getPartnerParticipant(){
        SpecialNpcTrainer npc = new SpecialNpcTrainer(player.level);
        npc.setBossTier(BossTierRegistry.NOT_BOSS);

        if(getRivalTeamLevel() > 100){
            int niveles = getRivalTeamLevel() - 100;
            BossTier tier = new BossTier("+"+niveles,"+"+niveles, false, 0, Color.BLACK, 1.0f, false,0.0, 0.0, "PALETA", 1.0, niveles);
            npc.setBossTier(tier);
        }

        npc.setBattleAIMode(companionConfig.getIA());
        if(!npc.isAddedToWorld()){
            npc.setTextureIndex(-1);
            String name = "aquaboss";
            npc.setName(companionConfig.getNombre());
            npc.setCustomSteveTexture(name);

            npc.setPos(player.getX(), player.getY(), player.getZ());
            player.level.addFreshEntity(npc);


            //npc.addEffect(new EffectInstance(Effects.INVISIBILITY, Integer.MAX_VALUE, 0, true, true));
            setEntity(npc);
        }

        List<Pokemon> equipoEntrenador = companionConfig.getEquipo();
        if(equipoEntrenador.isEmpty()){
            return null;
        }
        int i = 0;
        for (Pokemon pkm : equipoEntrenador) {
            Teras.getLogger().info("Añadiendo pokemon " + i + " al entrenador "
                    + npc.getName().getString() + " con nombre " + pkm.getDisplayName());
            npc.getPokemonStorage().set(i, pkm);

            i++;
            if (i == companionConfig.getNumPkmRival()) break;
        }
        return new TrainerParticipant(npc, 1);
    }

}
