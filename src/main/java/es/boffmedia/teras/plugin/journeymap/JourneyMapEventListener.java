package es.boffmedia.teras.plugin.journeymap;

import es.boffmedia.teras.util.PolygonCreator;
import es.boffmedia.teras.Teras;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.IThemeButton;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.display.ThemeButtonDisplay;
import journeymap.client.api.event.forge.FullscreenDisplayEvent;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;

public class JourneyMapEventListener {
    private final IClientAPI jmAPI;
    private final HashMap<ChunkPos, PolygonOverlay> slimeChunkOverlays;

    public JourneyMapEventListener(IClientAPI iClientAPI) {
        this.jmAPI = iClientAPI;
        this.slimeChunkOverlays = new HashMap<>();


        //ClientEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(Teras.MOD_ID, this::onFullscreenAddonButton);
        /*
        ClientEventRegistry.MAP_TYPE_BUTTON_DISPLAY_EVENT.subscribe(Teras.MOD_ID, this::onFullscreenMapTypeButton);
        ClientEventRegistry.CUSTOM_TOOLBAR_UPDATE_EVENT.subscribe(Teras.MOD_ID, this::onCustomToolbarEvent);
        ClientEventRegistry.ENTITY_RADAR_UPDATE_EVENT.subscribe(Teras.MOD_ID, this::onRadarEntityUpdateEvent);
        ClientEventRegistry.MAPPING_EVENT.subscribe(Teras.MOD_ID, this::mappingStageEvent);
        ClientEventRegistry.DEATH_WAYPOINT_EVENT.subscribe(Teras.MOD_ID, this::onDeathpoint);
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT_EVENT.subscribe(Teras.MOD_ID, this::optionsRegistryEvent);
        ClientEventRegistry.INFO_SLOT_REGISTRY_EVENT_EVENT.subscribe(Teras.MOD_ID, this::infoSlotRegistryEvent);
        */
    }

    private static boolean buttonOn = true;

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFullscreenAddonButton(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        Teras.getLogger().info("Fired button render");
        ThemeButtonDisplay buttonDisplay = event.getThemeButtonDisplay();

        /*
        IThemeButton button1 = buttonDisplay.addThemeButton("Test1", "alert", b -> System.out.println("ALERT ALERT"));
        IThemeButton button2 = buttonDisplay.addThemeButton("Test2", "grid", b -> System.out.println("ALERT ALERT"));
        IThemeButton button3 = buttonDisplay.addThemeButton("Test3", "day", b -> System.out.println("ALERT ALERT"));
        IThemeButton button4 = buttonDisplay.addThemeButton("Test4", "biome", b -> System.out.println("ALERT ALERT"));
        IThemeButton button5 = buttonDisplay.addThemeButton("Test5", "keys", b -> System.out.println("ALERT ALERT"));
        */
        IThemeButton button6 = buttonDisplay.addThemeToggleButton("Pueblos", "Text 6 Off", "grid", buttonOn, b -> {

            if (buttonOn) {
                PolygonCreator.removeAll();
            } else {
                PolygonCreator.createPolygon();
            }
            toggleButton(b, buttonOn = !buttonOn);
        });
    }

    private static void toggleButton(IThemeButton button, boolean toggled) {
        button.setToggled(toggled);
    }
}