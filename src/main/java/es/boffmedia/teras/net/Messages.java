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
        INSTANCE.registerMessage(index++, SMessageChatMessage.class, SMessageChatMessage::encode, SMessageChatMessage::decode, SMessageChatMessage::handle);

        INSTANCE.registerMessage(index++, SMessageIniciarLlamada.class, SMessageIniciarLlamada::encode, SMessageIniciarLlamada::decode, SMessageIniciarLlamada::handle);
        INSTANCE.registerMessage(index++, SMessageFinalizarLlamada.class, SMessageFinalizarLlamada::encode, SMessageFinalizarLlamada::decode, SMessageFinalizarLlamada::handle);

        INSTANCE.registerMessage(index++, CMessageDriftState.class, CMessageDriftState::encode, CMessageDriftState::decode, CMessageDriftState::handle);

        //INSTANCE.registerMessage(index++, MessageCarTest.class, MessageCarTest::encode, MessageCarTest::decode, MessageCarTest::handle);

        /// OOLD
        /*
        INSTANCE.registerMessage(index++, SMessageSetPC.class, SMessageSetPC::encode, SMessageSetPC::decode, SMessageSetPC::handle);

        INSTANCE.registerMessage(index++, SMessagePadCtrl.class, SMessagePadCtrl::encode, SMessagePadCtrl::decode, SMessagePadCtrl::handle);
        INSTANCE.registerMessage(index++, SMessageDarObjetos.class, SMessageDarObjetos::encode, SMessageDarObjetos::decode, SMessageDarObjetos::handle);
        INSTANCE.registerMessage(index++, SMessageVerMisiones.class, SMessageVerMisiones::encode, SMessageVerMisiones::decode, SMessageVerMisiones::handle);

        INSTANCE.registerMessage(index++, SMessageGetPC.class, SMessageGetPC::encode, SMessageGetPC::decode, SMessageGetPC::handle);
        INSTANCE.registerMessage(index++, CMessageGetPC.class, CMessageGetPC::encode, CMessageGetPC::decode, CMessageGetPC::handle);
        INSTANCE.registerMessage(index++, SMessageGetEquipo.class, SMessageGetEquipo::encode, SMessageGetEquipo::decode, SMessageGetEquipo::handle);
        INSTANCE.registerMessage(index++, SMessageCargarEquipo.class, SMessageCargarEquipo::encode, SMessageCargarEquipo::decode, SMessageCargarEquipo::handle);
        INSTANCE.registerMessage(index++, CMessageReturn.class, CMessageReturn::encode, CMessageReturn::decode, CMessageReturn::handle);
        INSTANCE.registerMessage(index++, CMessageCambioPosicion.class, CMessageCambioPosicion::encode, CMessageCambioPosicion::decode, CMessageCambioPosicion::handle);
        INSTANCE.registerMessage(index++, CMessageVerVideo.class, CMessageVerVideo::encode, CMessageVerVideo::decode, CMessageVerVideo::handle);
        INSTANCE.registerMessage(index++, CMessagePrepararNavegador.class, CMessagePrepararNavegador::encode, CMessagePrepararNavegador::decode, CMessagePrepararNavegador::handle);
        INSTANCE.registerMessage(index++, SMessageTaxi.class, SMessageTaxi::encode, SMessageTaxi::decode, SMessageTaxi::handle);

        INSTANCE.registerMessage(index++, CMessageRunJS.class, CMessageRunJS::encode, CMessageRunJS::decode, CMessageRunJS::handle);

        INSTANCE.registerMessage(index++, SMessageUpdateDex.class, SMessageUpdateDex::encode, SMessageUpdateDex::decode, SMessageUpdateDex::handle);
        INSTANCE.registerMessage(index++, CMessageWaypoints.class, CMessageWaypoints::encode, CMessageWaypoints::decode, CMessageWaypoints::handle);
*/

        /*
        INSTANCE.registerMessage(index++, FrameVideoMessage.class, FrameVideoMessage::encode, FrameVideoMessage::decode, FrameVideoMessage::handle);
        INSTANCE.registerMessage(index++, OpenVideoManagerScreen.class, OpenVideoManagerScreen::encode, OpenVideoManagerScreen::decode, OpenVideoManagerScreen::handle);
        INSTANCE.registerMessage(index++, UploadVideoUpdateMessage.class, UploadVideoUpdateMessage::encode, UploadVideoUpdateMessage::decode, UploadVideoUpdateMessage::handle);

*/
    }
}
