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
        br = br.set(BattleRuleRegistry.BATTLE_TYPE, battleConfig.getBattleType());
        // NUM_POKEMON is a single global property that caps the player's team-selection size.
        // The rival is an NPC whose party is sized when it is built (getPartRivalEntrenador),
        // so it is not driven by this property — that is what lets sizes differ (e.g. 3vs6).
        br = br.set(BattleRuleRegistry.NUM_POKEMON, battleConfig.getPlayerPkmCount());
        br = br.set(TerasBattleRuleRegistry.SPECIAL_BATTLE, true);

        setBattleType(battleConfig.getBattleType().toString());

        PlayerParticipant playerParticipant = getPlayerParticipant();
        BattleParticipant rivalParticipant = getRivalParticipant();

        if (playerParticipant == null || rivalParticipant == null) {
            Teras.LOGGER.error("No se pudo iniciar el combate '" + battleConfig.getNombreArchivo()
                    + "': falta un participante (equipo rival vacío o configuración inválida).");
            cleanupNpcEntity();
            return;
        }

        if(battleConfig.getRivalPkmCount() >= 6) {
            // Pass the rules so SPECIAL_BATTLE is applied; otherwise the BattleStartedEvent
            // handler would register this battle a second time and rebuild the header.
            battle = BattleRegistry.startBattle(
                    new BattleParticipant[]{playerParticipant},
                    new BattleParticipant[]{rivalParticipant},
                    br);
            Teras.getLBC().addTerasBattle(battle.battleIndex, this);

            cleanupNpcEntity();
        } else {
            TeamSelectionRegistry.Builder test =
                    TeamSelectionRegistry
                            .builder()
                            .members(playerParticipant.getEntity(), rivalParticipant.getEntity())
                            .battleRules(br)
                            .showOpponentTeam()
                            .closeable()
                            .battleStartConsumer(bc -> {
                                battle = bc;
                                Teras.getLBC().addTerasBattle(bc.battleIndex, this);
                                cleanupNpcEntity();
                            })
                            .cancelConsumer(ts -> {
                                Teras.LOGGER.error("CANCELADO");
                                cleanupNpcEntity();
                            });
            test.start();
        }

    }

    /** Removes the trainer entity spawned for this battle, guarding against a null/leaked handle. */
    protected void cleanupNpcEntity() {
        if (npcEntity != null) {
            npcEntity.remove();
            npcEntity = null;
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
        // PlayerParticipant derives its controlled (sent-out) count from the number of lead Pokémon it
        // is given. On the direct >=6 path there is no team-selection UI to fill those leads, so we must
        // hand it the right number ourselves — e.g. 2 for a doubles battle — or it defaults to one lead
        // and the player only ever sends out a single Pokémon.
        int active = Math.max(1, getPlayerControlledCount());
        List<Pokemon> team = getPlayerParty().getTeam();
        Pokemon[] leads = team.stream().limit(active).toArray(Pokemon[]::new);
        if (leads.length == 0) {
            return new PlayerParticipant(player, (Pokemon) null);
        }
        return new PlayerParticipant(player, leads);
    }

    /**
     * How many Pokémon the player sends out at once. Derived from the battle type for a 1-vs-1-trainer
     * battle; multi battles override this to 1 because the partner trainer fills the second doubles slot.
     */
    protected int getPlayerControlledCount(){
        return battleConfig.getPlayerActivePkmCount();
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
        // Controlled count = Pokémon this trainer sends out at once, derived from the battle
        // type (1 singles, 2 doubles, 3 triples, …) instead of a hardcoded 1.
        return new TrainerParticipant(npc, battleConfig.getRivalActivePkmCount());
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
