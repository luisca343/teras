package es.boffmedia.teras;

import es.boffmedia.teras.init.BlockEntityInit;
import es.boffmedia.teras.init.BlockInit;
import es.boffmedia.teras.init.ComponentInit;
import es.boffmedia.teras.init.FluidInit;
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
        // Touch BlockInit first: its static init is what registers the per-Comida blocks *and* items.
        BlockInit.BLOCKS.register(modBus);
        BlockEntityInit.BLOCK_ENTITIES.register(modBus);
        FluidInit.FLUID_TYPES.register(modBus);
        FluidInit.FLUIDS.register(modBus);
        es.boffmedia.teras.init.GearMaterialInit.ARMOR_MATERIALS.register(modBus);
        ItemInit.ITEMS.register(modBus);
        ItemInit.CREATIVE_TABS.register(modBus);
        ComponentInit.COMPONENTS.register(modBus);
        es.boffmedia.teras.init.EntityInit.ENTITY_TYPES.register(modBus);
        es.boffmedia.teras.dungeon.gear.LootInit.LOOT_FUNCTIONS.register(modBus);

        // Config belongs to whoever runs the server, so it loads at server start (TerasConfig), not
        // here: a client connected to a remote server has no business reading its own copy.
        modBus.addListener(this::onCommonSetup);

        // Client-only wiring lives in TerasClient, gated by dist at registration time.
        LOGGER.info("Teras 1.21.1 loading (mod bus attached)");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Charms as Curios items. A no-op without Curios, which is a soft dependency: a server
        // running Teras for regions or starbank should not need a trinket mod for a system it does
        // not use. Charms fall back to the offhand there.
        event.enqueueWork(es.boffmedia.teras.dungeon.gear.GearCurios::register);
        event.enqueueWork(() -> {
            // Quest system: no-op unless CustomNPCs is installed. QuestBridge is the only class named
            // here, so no `noppes` class is loaded on a server without it.
            es.boffmedia.teras.quests.QuestBridge.registerIfPresent();
            // Dungeon enemy abilities on CustomNPCs' own event bus. Same isolation — AbilityBridge
            // names no `noppes` class, and the animated enemy runs its abilities either way.
            es.boffmedia.teras.dungeon.ability.AbilityBridge.registerIfPresent();
            // Economy: takes over the engine's bank (Pixelmon only; Cobblemon has none) so in-game
            // currency is the starbank balance. Same isolation — EconomyBridge names no engine class.
            es.boffmedia.teras.economy.EconomyBridge.registerIfPresent();
            // Pokédex: mirrors every dex registration to SmartRotom, for whichever engine is present.
            // Same isolation — DexBridge names no engine class.
            es.boffmedia.teras.dex.DexBridge.registerIfPresent();
            // Storage: tells an open SmartRotom PC to refetch when the game moves a Pokémon, so its
            // positional swaps never act on a stale view. Same isolation.
            es.boffmedia.teras.storage.StorageBridge.registerIfPresent();
            // Backpacks: lets Pixelmon reach battle items stored inside any container item. Pixelmon
            // only (Cobblemon has no BattleItemScanner). Same isolation — BackpackBridge names no
            // engine class.
            es.boffmedia.teras.battle.BackpackBridge.registerIfPresent();
            // Regions: the cartel on town enter. Engine-free; future consumers (quests, music)
            // register alongside.
            es.boffmedia.teras.region.RegionTracker.addListener(
                    new es.boffmedia.teras.region.RegionBannerSender());
            // Karts: pays out finished races, files circuit records, and reports them. Registered
            // as a listener so a race never has to know money or leaderboards exist.
            es.boffmedia.teras.karts.reward.RaceRewards.register();
        });
    }
}
