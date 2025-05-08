package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.objects._old.misiones.DatosNPC;
import es.boffmedia.teras.util.objects._old.misiones.QuestData;
import io.leangen.geantyref.TypeToken;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.montoyo.mcef.api.IJSQueryCallback;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SMessageVerMisiones implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    private IJSQueryCallback callback;

    Map<String, DatosNPC> datosNpc;
    Map<Integer, QuestData> datosMisionMap;

    public SMessageVerMisiones(String str){
        this.str = str;
    }

    @Override
    public void run(){
        NpcAPI npcApi = NpcAPI.Instance();

        datosMisionMap = ( Map<Integer, QuestData>) FileHelper.readFile("config/teras/misiones.json", new TypeToken< Map<Integer, QuestData>>(){}.getType());
        if(datosMisionMap == null){
            datosMisionMap = new HashMap<>();
        }

        /*
        PlayerWrapper wrapper = new PlayerWrapper(player);
        IQuest[] mActivas = wrapper.getActiveQuests();
        IQuest[] mCompletadas = wrapper.getFinishedQuests();
        ArrayList<QuestData> misiones = new ArrayList<>();

        for(IQuest mActiva : mActivas){
            QuestData datos = datosMisionMap.get(mActiva.getId());
            datos.setStatus(QuestStatus.ACTIVE);

            IQuestObjective[] objetivos = mActiva.getObjectives(wrapper);
            ArrayList<QuestObjective> objetivosMision = new ArrayList<>();
            if (objetivos.length > 0 ){
                for (IQuestObjective objetivo : objetivos) {
                    ObjetivoMision objetivoMision = new ObjetivoMision();
                    objetivoMision.setNombre(objetivo.getText());
                    objetivoMision.setProgreso(objetivo.getProgress());
                    objetivoMision.setTotal(objetivo.getMaxProgress());
                    objetivosMision.add(objetivoMision);
                }
            }

            datos.setObjectives(objetivosMision);
            misiones.add(datos);
        }

        for(IQuest mCompleta : mCompletadas){
            QuestData datos = datosMisionMap.get(mCompleta.getId());
            datos.setStatus(QuestStatus.COMPLETED);

            misiones.add(datos);
        }

        HashMap<String, Integer> categorias = new HashMap<>();

        for(IQuestCategory category : npcApi.getQuests().categories()){
            categorias.put(category.getName(), category.quests().size());
        }

        MisionesJugador misionesJugador = new MisionesJugador();
        misionesJugador.setMisiones(misiones);
        misionesJugador.setCategorias(categorias);


        Gson gson = new Gson();
        String res = gson.toJson(misionesJugador);
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageVerMisiones(res));*/
    }

    private void guardarMision(IQuest mActiva, PlayerWrapper wrapper) {

    }
    
    public static SMessageVerMisiones decode(PacketBuffer buf) {
        SMessageVerMisiones message = new SMessageVerMisiones(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeCharSequence(str, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
