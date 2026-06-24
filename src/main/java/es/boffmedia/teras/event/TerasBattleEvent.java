package es.boffmedia.teras.event;

import com.pixelmonmod.pixelmon.api.battles.BattleResults;
import com.pixelmonmod.pixelmon.api.events.BeatTrainerEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import es.boffmedia.teras.util.objects.TrainerDefeatMoney;
import es.boffmedia.teras.util.objects.logros.LogroCombate;
import es.boffmedia.teras.pixelmon.battle.*;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.data.Scoreboard;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class TerasBattleEvent {


    @SubscribeEvent
    public void beatTrainer(BeatTrainerEvent event){
        if(event.trainer.getWinMoney() == 0){
            Teras.LOGGER.info("No hay dinero que ganar, esto podría ser un combate custom, verdad Luisca??");
            return;
        }
        
        
        TrainerDefeatMoney defeatMoney = new TrainerDefeatMoney(event.player.getStringUUID(), event.trainer.getWinMoney());
        SmartRotomService.defeatTrainer(defeatMoney);
    }

    public void inicioCombateEntrenador(BattleStartedEvent event, BattleSession combate){
        Teras.LOGGER.info("Iniciando combate entrenador");
        //TerasBattleLog.parseLog(event.bc.battleLog, combate);
    }

    public void inicioCombateSalvaje(BattleStartedEvent event, BattleSession combate){
        Teras.LOGGER.info("Iniciando combate salvaje");
    }

    public void finCombateEntrenador(BattleEndEvent event, TerasBattle combate){
        Teras.LOGGER.info("Fin combate entrenador");
        //TerasBattleLog.parseLog(event.getBattleController().battleLog, combate);



        boolean ganador = getGanador(event, combate);
        String nombreGanador = ganador ? combate.getP1().getDisplayName() : combate.getP2().getDisplayName();
        TerasBattleLog.appendLine(combate, "|win|" + nombreGanador);

        if(combate instanceof NPCTerasBattle){
            LogroCombate logroCombate = getLogroCombate((NPCTerasBattle) combate, ganador);
            SmartRotomService.saveBattle(logroCombate);
        }

        Teras.getLogger().info(combate.getLogString());
        FileHelper.writeStringFile("logs/terasbattle/\"+combate.getBattleConfig().getNombreArchivo()+\".log", combate.getLogString());

    }

    private static @NotNull LogroCombate getLogroCombate(NPCTerasBattle combate, boolean ganador) {
        LogroCombate logroCombate = new LogroCombate();
        logroCombate.setUuid(combate.getPlayer().getStringUUID());

        logroCombate.setName1(combate.getPlayer().getName().getString());
        logroCombate.setName2(combate.getBattleConfig().getNombre());

        logroCombate.setLogro(combate.getBattleConfig().getLogro());
        logroCombate.setVictoria(ganador);
        logroCombate.setReplay(combate.getLogString());
        List<Pokemon> team = combate.getP1Team();
        List<Pokemon> teamRival = combate.getP2Team();
        logroCombate.setEquipo(team);
        logroCombate.setTeam2(teamRival);
        return logroCombate;
    }

    public void finCombateSalvaje(BattleEndEvent event, TerasBattle combate){
        getGanador(event, combate);
    }

    public boolean getGanador(BattleEndEvent event, TerasBattle combate){
        LivingEntity ganador = null, perdedor = null;
        try{
            for(Map.Entry<BattleParticipant, BattleResults> entry : event.getResults().entrySet()){
                if(entry.getValue() == BattleResults.VICTORY){
                    ganador = entry.getKey().getEntity();
                } else if(entry.getValue() == BattleResults.DEFEAT){
                    perdedor = entry.getKey().getEntity();
                }
            }
        }catch (Exception e){
            Teras.getLogger().error("Error resolving battle results", e);
        }

        if(ganador instanceof ServerPlayerEntity && combate instanceof NPCTerasBattle) {
            NPCTerasBattle npcTerasBattle = (NPCTerasBattle) combate;
            String nombreObjetivo = npcTerasBattle.getBattleConfig().getNombreObjetivo();

            Scoreboard.set(npcTerasBattle.getPlayer(), nombreObjetivo, 1);
            MessageHelper.enviarMensaje(npcTerasBattle.getPlayer(), TextFormatting.GREEN + "Has ganado el combate contra " + npcTerasBattle.getBattleConfig().getNombre());
            MessageHelper.enviarMensaje(npcTerasBattle.getPlayer(), TextFormatting.GREEN + "Obtienes " + npcTerasBattle.getBattleConfig().getDinero() + " Pokedolares");
            return true;
        }else {
            ServerPlayerEntity player = (ServerPlayerEntity) combate.getP1().getEntity();
            MessageHelper.enviarMensaje(player, TextFormatting.RED + "Has perdido el combate contra " + combate.getP2().getDisplayName());
            return false;
        }

    }

    @SubscribeEvent
    public void onBattleStart(BattleStartedEvent event){
        if(Teras.getLBC().existsTerasBattle(event.getBattleController().battleIndex)) {
            TerasBattle combate = Teras.getLBC().getTerasBattle(event.getBattleController().battleIndex);
            Teras.LOGGER.info("Inicializando combate desde evento");
            /*
            if(combate.getBattleConfig().esEntrenador()) inicioCombateEntrenador(event, combate);
            else inicioCombateSalvaje(event, combate);
            */
        }
    }

    @SubscribeEvent
    public void onBattleEnd(BattleEndEvent event){
        if(Teras.getLBC().existsTerasBattle(event.getBattleController().battleIndex)) {
            TerasBattle combate = Teras.getLBC().getTerasBattle(event.getBattleController().battleIndex);
            Teras.LOGGER.info("Finalizando combate desde evento");

            /*
            Iterator<Map.Entry<BattleParticipant, BattleResults>> iterator = event.getResults().entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<BattleParticipant, BattleResults> entry = iterator.next();
                Teras.LOGGER.info("Resultado: " + entry.getValue());

                if(entry.getValue() == BattleResults.VICTORY){
                    Teras.LOGGER.info("Ganador: " + entry.getKey().getDisplayName());
                } else {
                    Teras.LOGGER.info("Perdedor: " + entry.getKey().getDisplayName());
                }

            }*/

            if(combate instanceof NPCTerasBattle){
                finCombateEntrenador(event, combate);
            } else {
                Teras.getLogger().info("The type of the batle is: " + combate.getClass().getName());
            }

            //Teras.getLogger().info(combate.getLogString());

            /*
            if(combate.getRivalParticipant().getEntity() != null) combate.getRivalParticipant().getEntity().remove();


            if( combate.getBattleConfig().esEntrenador()) {
                //TerasBattleLog.parseLog(event.getBattleController().battleLog, combate);

                if(combate instanceof CombateFrenteBatalla) finCombateFrenteBatalla(event, combate);
                else finCombateEntrenador(event, combate);
            }
            else finCombateSalvaje(event, combate);

            MobEntity entity = Teras.getLBC().getTerasBattle(event.getBattleController().battleIndex).getEntity();
            if(entity != null){
                entity.remove();
            }*/
            Teras.getLBC().removeTerasBattle(event.getBattleController().battleIndex);
        }
    }

    private void finCombateFrenteBatalla(BattleEndEvent event, BattleSession combate) {
        /*
        CombateFrenteBatalla combateFrenteBatalla = (CombateFrenteBatalla) combate;
        if(getGanador(event, combateFrenteBatalla)){
            // Send message to all players
            event.getPlayers().forEach(p -> {
                String modalidad = combateFrenteBatalla.getModalidad();
                int victorias = p.getPersistentData().getInt(modalidad) + 1;
                p.getPersistentData().putInt(modalidad, victorias);
                TorreBatallaController.eleccionesCombate(p);
            });
           } else {
            event.getPlayers().forEach(p -> {
                MessageHelper.enviarMensaje(p, "§cHas perdido el combate contra " + combateFrenteBatalla.getBattleConfig().getNombre());
            });
        }*/
    }
}
