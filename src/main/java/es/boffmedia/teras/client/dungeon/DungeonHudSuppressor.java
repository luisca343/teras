package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Hides other mods' HUDs for the length of a run.
 *
 * <p>Pixelmon draws down the left edge, which is where the stat panel lives, and inside a dungeon there
 * is nothing for it to say — {@code DungeonPokemonGuard} has already made sure no Pokémon can be out.
 * Two overlays fighting for the same column is the visible half of a system that should not be running
 * here at all.</p>
 *
 * <h2>Two mechanisms, because one is not enough</h2>
 *
 * <p>Anything drawn as a registered GUI layer is matched by its <b>namespace</b> and cancelled, so this
 * compiles and runs with the mod absent and adding another is one string. That does not reach a mod
 * that draws from {@code RenderGuiEvent} instead of registering a layer, which is what Pixelmon's party
 * overlay does — {@link PixelmonHud} handles that one through Pixelmon's own state field.</p>
 *
 * <h2>Why it keys on the map and not on the stat panel</h2>
 *
 * <p>It used to test {@code ClientCombatStats.visible()}, which is gated on {@code combate.activado}:
 * turning the combat rebuild off therefore brought another mod's HUD back into a dungeon it still had
 * nothing to say in. Being <b>in a run</b> is the actual condition, and the minimap is the client's
 * run-scoped signal — the server sends {@code DungeonMapPayload.hidden()} on every way out.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class DungeonHudSuppressor {
    private DungeonHudSuppressor() {}

    private static final String[] HIDDEN = {"pixelmon", "cobblemon"};

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!ClientDungeonMap.isVisible()) {
            return;
        }
        String namespace = event.getName().getNamespace();
        for (String hidden : HIDDEN) {
            if (hidden.equals(namespace)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Puts the party overlay away on the way in and back on the way out.
     *
     * <p>Driven from a tick rather than from the payload handler because it has to be <b>edge
     * triggered in both directions</b>: entering has to remember what to restore, and leaving has to
     * restore it exactly once. A tick sees every transition, including the ones no packet announces —
     * a run failing mid-build, or the map going quiet because the server stopped.</p>
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientDungeonMap.isVisible()) {
            PixelmonHud.hide();
        } else {
            PixelmonHud.restore();
        }
    }

    /** Never leave another mod's HUD hidden on a server this player is no longer on. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PixelmonHud.restore();
    }
}
