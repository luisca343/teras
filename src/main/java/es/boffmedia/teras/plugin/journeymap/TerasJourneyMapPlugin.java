package es.boffmedia.teras.plugin.journeymap;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.gui.FullscreenMap;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumSet;

@ParametersAreNonnullByDefault
@ClientPlugin
public class TerasJourneyMapPlugin implements IClientPlugin {
    private static FullscreenMap fullscreenMap;
    private JourneyMapEventListener journeyMapEventListener;

    @Override
    public void initialize(IClientAPI iClientAPI) {
        Teras.PROXY.setjmAPI(iClientAPI);
        iClientAPI.subscribe(getModId(), EnumSet.allOf(ClientEvent.Type.class));

        journeyMapEventListener = new JourneyMapEventListener(iClientAPI);

        MinecraftForge.EVENT_BUS.register(journeyMapEventListener);


    }

    @Override
    public String getModId() {
        return Teras.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent clientEvent) {
        Teras.getLogger().info("MAP EVENT FIRED: " + clientEvent.type);


    }
}
