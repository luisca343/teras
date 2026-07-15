package es.boffmedia.teras;

import es.boffmedia.teras.init.ItemInit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Teras(IEventBus modBus, ModContainer container) {
        // Registries
        ItemInit.ITEMS.register(modBus);
        ItemInit.CREATIVE_TABS.register(modBus);

        // Client-only wiring lives in TerasClient, gated by dist at registration time.
        LOGGER.info("Teras 1.21.1 loading (mod bus attached)");
    }
}
