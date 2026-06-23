package es.boffmedia.teras.net;

import es.boffmedia.teras.net.client.*;
import es.boffmedia.teras.net.server.*;
import es.boffmedia.teras.net.server.serverOld.SMessageFinalizarLlamada;
import es.boffmedia.teras.net.server.serverOld.SMessageIniciarLlamada;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

@Mod.EventBusSubscriber
public class Messages {

    private static final String PROTOCOL_VERSION = "1";
    public static int index = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("teras", "packetsystem"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    @SubscribeEvent
    public static void registryNetworkPackets(FMLCommonSetupEvent event) {
        INSTANCE.registerMessage(index++, SMessageDatosServer.class, SMessageDatosServer::encode, SMessageDatosServer::decode, SMessageDatosServer::handle);
        INSTANCE.registerMessage(index++, CMessageDatosServer.class, CMessageDatosServer::encode, CMessageDatosServer::decode, CMessageDatosServer::handle);

        INSTANCE.registerMessage(index++, CMessageConfigServer.class, CMessageConfigServer::encode, CMessageConfigServer::decode, CMessageConfigServer::handle);

        INSTANCE.registerMessage(index++, CMessageMCEFResponse.class, CMessageMCEFResponse::encode, CMessageMCEFResponse::decode, CMessageMCEFResponse::handle);
        INSTANCE.registerMessage(index++, SMessageCheckSpawns.class, SMessageCheckSpawns::encode, SMessageCheckSpawns::decode, SMessageCheckSpawns::handle);

        INSTANCE.registerMessage(index++, CMessageVerMisiones.class, CMessageVerMisiones::encode, CMessageVerMisiones::decode, CMessageVerMisiones::handle);

        INSTANCE.registerMessage(index++, CMessageRacePositionChange.class, CMessageRacePositionChange::encode, CMessageRacePositionChange::decode, CMessageRacePositionChange::handle);

        INSTANCE.registerMessage(index++, SMessageDarCaja.class, SMessageDarCaja::encode, SMessageDarCaja::decode, SMessageDarCaja::handle);

        INSTANCE.registerMessage(index++, SMessageEncenderPC.class, SMessageEncenderPC::encode, SMessageEncenderPC::decode, SMessageEncenderPC::handle);


        INSTANCE.registerMessage(index++, CMessageCambioRegion.class, CMessageCambioRegion::encode, CMessageCambioRegion::decode, CMessageCambioRegion::handle);

        INSTANCE.registerMessage(index++, CMessageFindPath.class, CMessageFindPath::encode, CMessageFindPath::decode, CMessageFindPath::handle);
        INSTANCE.registerMessage(index++, CMessageGps.class, CMessageGps::encode, CMessageGps::decode, CMessageGps::handle);
        INSTANCE.registerMessage(index++, SMessageChatMessage.class, SMessageChatMessage::encode, SMessageChatMessage::decode, SMessageChatMessage::handle);

        INSTANCE.registerMessage(index++, SMessageIniciarLlamada.class, SMessageIniciarLlamada::encode, SMessageIniciarLlamada::decode, SMessageIniciarLlamada::handle);
        INSTANCE.registerMessage(index++, SMessageFinalizarLlamada.class, SMessageFinalizarLlamada::encode, SMessageFinalizarLlamada::decode, SMessageFinalizarLlamada::handle);

        INSTANCE.registerMessage(index++, CMessageDriftState.class, CMessageDriftState::encode, CMessageDriftState::decode, CMessageDriftState::handle);
        INSTANCE.registerMessage(index++, SMessageUpdateDex.class, SMessageUpdateDex::encode, SMessageUpdateDex::decode, SMessageUpdateDex::handle);

        INSTANCE.registerMessage(index++, CMessageRunJS.class, CMessageRunJS::encode, CMessageRunJS::decode, CMessageRunJS::handle);

    }
}
