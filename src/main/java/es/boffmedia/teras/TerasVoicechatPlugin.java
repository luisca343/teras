package es.boffmedia.teras;


import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import es.boffmedia.teras.util.voicechat.CallManager;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;

@ForgeVoicechatPlugin
public class TerasVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi SERVER_API;
    public static String MUSIC_DISC_CATEGORY = "music_discs";
    public static String MUSIC_CATEGORY = "teras_music";
    public static String PHONE_CALL_CATEGORY = "phone_calls";

    @Nullable
    public static VolumeCategory musicDiscs;

    @Nullable
    public static VolumeCategory terasMusic;
    
    @Nullable
    public static VolumeCategory phoneCalls;

    private void onServerStarted(VoicechatServerStartedEvent event) {
        SERVER_API = event.getVoicechat();

        musicDiscs = SERVER_API.volumeCategoryBuilder()
                .setId(MUSIC_DISC_CATEGORY)
                .setName("Discos")
                .setDescription("El volumen de los discos de música")
                .setIcon(getIcon("category_music_discs.png"))
                .build();

        terasMusic = SERVER_API.volumeCategoryBuilder()
                .setId(MUSIC_CATEGORY)
                .setName("Música")
                .setDescription("El volumen de la música")
                .setIcon(getIcon("category_music.png"))
                .build();

        phoneCalls = SERVER_API.volumeCategoryBuilder()
                .setId(PHONE_CALL_CATEGORY)
                .setName("Llamadas")
                .setDescription("El volumen de las llamadas telefónicas")
                .setIcon(getIcon("category_phone_calls.png"))
                .build();

        SERVER_API.registerVolumeCategory(musicDiscs);
        SERVER_API.registerVolumeCategory(terasMusic);
        SERVER_API.registerVolumeCategory(phoneCalls);
    }
    
    /**
     * Intercepts microphone packets and forwards them to call participants
     * This allows players to hear both proximity voice AND call audio simultaneously
     */
    private void onMicrophonePacket(MicrophonePacketEvent event) {
        UUID senderUUID = event.getSenderConnection().getPlayer().getUuid();
        
        // Check if the sender is in a call
        if (!CallManager.isInCall(senderUUID)) {
            return; // Not in a call, normal proximity voice continues
        }
        
        // Get all participants in the same call (excluding sender)
        Set<UUID> participants = CallManager.getCallParticipants(senderUUID);
        
        // Get the sender
        ServerPlayerEntity sender = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayer(senderUUID);
        
        if (sender == null) {
            return;
        }
        
        // Forward the packet to each call participant
        for (UUID participantUUID : participants) {
            VoicechatConnection connection = SERVER_API.getConnectionOf(participantUUID);
            if (connection != null && !connection.isDisabled()) {
                try {
                    ServerPlayerEntity participant = ServerLifecycleHooks.getCurrentServer()
                            .getPlayerList()
                            .getPlayer(participantUUID);
                    
                    if (participant == null) {
                        continue;
                    }
                    
                    // Create an entity audio channel attached to the sender
                    EntityAudioChannel channel = SERVER_API.createEntityAudioChannel(
                        UUID.randomUUID(), 
                        SERVER_API.fromServerPlayer(sender)
                    );
                    
                    // Set phone call category
                    channel.setCategory(PHONE_CALL_CATEGORY);
                    
                    // Send the opus audio bytes
                    channel.send(event.getPacket().getOpusEncodedData());
                    
                } catch (Exception e) {
                    System.err.println("Error forwarding call audio to " + participantUUID + ": " + e.getMessage());
                }
            }
        }
    }


    /**
     * @return the unique ID for this voice chat plugin
     */
    @Override
    public String getPluginId() {
        return Teras.MOD_ID;
    }

    /**
     * Called when the voice chat initializes the plugin.
     *
     * @param api the voice chat API
     */
    @Override
    public void initialize(VoicechatApi api) {

    }

    /**
     * Called once by the voice chat to register all events.
     *
     * @param registration the event registration
     */
    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }


    @Nullable
    private int[][] getIcon(String path) {
        try {
            Enumeration<URL> resources = TerasVoicechatPlugin.class.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                BufferedImage bufferedImage = ImageIO.read(resources.nextElement().openStream());
                if (bufferedImage.getWidth() != 16) {
                    continue;
                }
                if (bufferedImage.getHeight() != 16) {
                    continue;
                }
                int[][] image = new int[16][16];
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        image[x][y] = bufferedImage.getRGB(x, y);
                    }
                }
                return image;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}