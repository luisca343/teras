package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.clientOld.CMessageReturn;
import es.boffmedia.teras.util.objects._old.misiones.Mision;
import es.boffmedia.teras.util.objects._old.misiones.ObjetivoMision;
import es.boffmedia.teras.util.objects._old.unused.SetPC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.montoyo.mcef.api.IJSQueryCallback;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.wrapper.PlayerWrapper;
import noppes.npcs.util.NBTJsonUtil;

import java.util.ArrayList;
import java.util.function.Supplier;

public class SMessageSetPC implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    private IJSQueryCallback callback;

    public SMessageSetPC(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        SetPC setPC = gson.fromJson(str, SetPC.class);
        String datos = setPC.getDatos();

        PlayerPartyStorage storage = StorageProxy.getParty(player);
        PCStorage pc = StorageProxy.getPCForPlayer(player);
        try{

            CompoundNBT nbt = NBTJsonUtil.Convert(datos);
            PCStorage pc2 = pc.readFromNBT(nbt);


        }catch(Exception e){
            e.printStackTrace();
        }

        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageReturn(""));
    }

    public void guardarMision(ArrayList<Mision> lista, IQuest quest, PlayerWrapper wrapper, ArrayList<Integer> idActivas){
        Mision mision = new Mision();
        mision.setCategoria(quest.getCategory().getName());
        mision.setNombre(quest.getName());
        mision.setTipo(quest.getType());
        mision.setTextoCompletar(quest.getCompleteText());
        mision.setTextoLog(quest.getLogText());
        mision.setNombreNPC(quest.getNpcName());
        mision.setRepetible(quest.getIsRepeatable());
        mision.setId(quest.getId());
        


        if(idActivas.contains(quest.getId())){
            mision.setEstado("Activa");
            IQuestObjective[] objetivos = quest.getObjectives(wrapper);
            ArrayList<ObjetivoMision> objetivosMision = new ArrayList<>();
            if (objetivos.length > 0 ){
                for (IQuestObjective objetivo : objetivos) {
                    ObjetivoMision objetivoMision = new ObjetivoMision();
                    objetivoMision.setNombre(objetivo.getText());
                    objetivoMision.setProgreso(objetivo.getProgress());
                    objetivoMision.setTotal(objetivo.getMaxProgress());
                    objetivosMision.add(objetivoMision);
                }
            }
            mision.setObjetivos(objetivosMision);
        }else {
            mision.setEstado("Completa");
        }
        lista.add(mision);
    }

    public static SMessageSetPC decode(PacketBuffer buf) {
        SMessageSetPC message = new SMessageSetPC(buf.toString(Charsets.UTF_8));
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
