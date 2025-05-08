package es.boffmedia.teras.event;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.misiones.QuestData;
import es.boffmedia.teras.util.file.FileHelper;
import io.leangen.geantyref.TypeToken;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.event.DialogEvent;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.event.QuestEvent;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
public class CustomNPCsEvents {

    @SubscribeEvent
    public static void test(NpcEvent.UpdateEvent event){


    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onReadDialogue(DialogEvent.OpenEvent event){
        Teras.getLogger().info("Se ha leido el dialogo");
    }

    @SubscribeEvent
    public static void openDialog(DialogEvent.OpenEvent event){
        IQuest quest = event.dialog.getQuest();
        ServerPlayerEntity player = event.player.getMCEntity();

        PlayerWrapper wrapper = new PlayerWrapper(player);

        if(quest == null || !wrapper.canQuestBeAccepted(quest.getId())) return;

        String npcName = event.npc.getName();
        Map<Integer, QuestData> listaMisiones = (Map<Integer, QuestData>) FileHelper.readFile("config/teras/misiones.json", new TypeToken<Map<Integer, QuestData>>(){}.getType());
        if(listaMisiones == null){
            listaMisiones = new HashMap<>();
        }

        QuestData datosMision = new QuestData();
        QuestData datosMisionArchivo = new QuestData();

        if(listaMisiones.containsKey(quest.getId())){
            datosMisionArchivo = listaMisiones.get(quest.getId());
        }
        /* Datos de la mision */


        datosMision.setId(quest.getId());
        datosMision.setName(quest.getName());
        datosMision.setLogText(quest.getLogText());
        datosMision.setCompleteText(quest.getCompleteText());
        datosMision.setRepeatable(quest.getIsRepeatable());
        datosMision.setType(quest.getType());
        datosMision.setNextQuest(-1);

        if(quest.getNextQuest() != null){
            datosMision.setNextQuest(quest.getNextQuest().getId());
        }
        datosMision.setCategory(quest.getCategory().getName());

        /* Datos del NPC */
        datosMision.setNpcName(event.npc.getName());
        datosMision.setX(event.npc.getX());
        datosMision.setY(event.npc.getY());
        datosMision.setZ(event.npc.getZ());

        String rutaSkin = event.npc.getEntityNbt().getString("Texture");
        String[] partes = rutaSkin.split("/");
        datosMision.setSkin(partes[partes.length-1]);

        // Comparamos los datos de la mision actual con los datos de la mision guardada
        if(!datosMision.equals(datosMisionArchivo)){
            listaMisiones.put(quest.getId(), datosMision);
            FileHelper.writeFile("config/teras/misiones.json", listaMisiones);
        }
    }

    @SubscribeEvent
    public static void misionCumplida(QuestEvent.QuestCompletedEvent event){
        ServerPlayerEntity jugador = (ServerPlayerEntity) event.player.getMCEntity();
    }
}
