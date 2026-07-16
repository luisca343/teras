package es.boffmedia.teras;

import es.boffmedia.teras.init.ComponentInit;
import es.boffmedia.teras.init.ItemInit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Teras — 1.21.1 NeoForge port.
 *
 * <p>First ported feature: the SmartRotom in-game browser (powered by MCEF).
 * The 1.16.5 mod was written against the montoyo MCEF API; on 1.21.1 we rebuild
 * that behaviour on top of CinemaMod MCEF + raw JCEF without modifying MCEF.</p>
 */
@Mod(Teras.MOD_ID)
public class Teras {
    public static final String MOD_ID = "teras";
    public static final Logger LOGGER = LoggerFactory.getLogger("Teras");

    /**
     * Shared pool for blocking off-thread work (remote combat-config / PokePaste fetches). Daemon
     * threads so a pending fetch never blocks JVM shutdown. Ported from the 1.16.5 {@code EXECUTOR}.
     */
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Teras-Worker");
        t.setDaemon(true);
        return t;
    });

    public Teras(IEventBus modBus, ModContainer container) {
        // Registries
        ItemInit.ITEMS.register(modBus);
        ItemInit.CREATIVE_TABS.register(modBus);
        ComponentInit.COMPONENTS.register(modBus);

        // Config belongs to whoever runs the server, so it loads at server start (TerasConfig), not
        // here: a client connected to a remote server has no business reading its own copy.
        modBus.addListener(this::onCommonSetup);

        // Client-only wiring lives in TerasClient, gated by dist at registration time.
        LOGGER.info("Teras 1.21.1 loading (mod bus attached)");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Quest system: no-op unless CustomNPCs is installed. QuestBridge is the only class named
            // here, so no `noppes` class is loaded on a server without it.
            es.boffmedia.teras.quests.QuestBridge.registerIfPresent();
            // Economy: takes over the engine's bank (Pixelmon only; Cobblemon has none) so in-game
            // currency is the starbank balance. Same isolation — EconomyBridge names no engine class.
            es.boffmedia.teras.economy.EconomyBridge.registerIfPresent();
        });
    }
}
