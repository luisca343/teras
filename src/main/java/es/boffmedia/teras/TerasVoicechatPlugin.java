package es.boffmedia.teras;


import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Enumeration;

@ForgeVoicechatPlugin
public class TerasVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi SERVER_API;
    public static String MUSIC_DISC_CATEGORY = "music_discs";
    public static String MUSIC_CATEGORY = "teras_music";

    @Nullable
    public static VolumeCategory musicDiscs;

    @Nullable
    public static VolumeCategory terasMusic;

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


        SERVER_API.registerVolumeCategory(musicDiscs);
        SERVER_API.registerVolumeCategory(terasMusic);
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
            Teras.getLogger().error("Error loading voicechat image", e);
        }
        return null;
    }

    
    
}