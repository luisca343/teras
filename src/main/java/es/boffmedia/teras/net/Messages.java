package es.boffmedia.teras.net;

import es.boffmedia.teras.net.client.*;
import es.boffmedia.teras.net.server.*;
import es.boffmedia.teras.net.server.serverOld.SMessageFinalizarLlamada;
import es.boffmedia.teras.net.server.serverOld.SMessageIniciarLlamada;
import es.boffmedia.teras.net.video.FrameVideoMessage;
import es.boffmedia.teras.net.video.OpenVideoManagerScreen;
import es.boffmedia.teras.net.video.UploadVideoUpdateMessage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

@Mod.EventBusSubscriber
public class Messages {

    private static final String PROTOCOL_VERSION = "2";
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

        FrameVideoMessage frameMsg = new FrameVideoMessage();
        INSTANCE.registerMessage(index++, FrameVideoMessage.class, frameMsg::encode, frameMsg::decode, frameMsg::handle);

        OpenVideoManagerScreen openMsg = new OpenVideoManagerScreen();
        INSTANCE.registerMessage(index++, OpenVideoManagerScreen.class, openMsg::encode, openMsg::decode, openMsg::handle);

        UploadVideoUpdateMessage uploadMsg = new UploadVideoUpdateMessage();
        INSTANCE.registerMessage(index++, UploadVideoUpdateMessage.class, uploadMsg::encode, uploadMsg::decode, uploadMsg::handle);
    }

    public static <MSG> void sendTo(MSG msg, PlayerEntity player) {
        INSTANCE.sendTo(msg, ((ServerPlayerEntity)player).connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT);
    }

    public static <MSG> void sendToClient(MSG message, World level, BlockPos pos) {
        sendToClient(message, level.getChunkAt(pos));
    }

    public static <MSG> void sendToClient(MSG msg, Chunk chunk) {
        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static <MSG> void sendToAllTracking(MSG msg, LivingEntity entityToTrack) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entityToTrack), msg);
    }

    public static <MSG> void sendToAll(MSG msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static <MSG> void sendToServer(MSG msg) {
        INSTANCE.sendToServer(msg);
    }
}
