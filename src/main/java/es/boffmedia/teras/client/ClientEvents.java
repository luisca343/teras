package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.renders.IItemRenderer;
import es.boffmedia.teras.client.renders.SmartRotomRenderer;
import es.boffmedia.teras.init.ItemInit;
import es.boffmedia.teras.items.SmartRotom;
import es.boffmedia.teras.mcef.TerasMCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client game-bus event hooks: the first-person SmartRotom hand renderer and the per-item browser
 * lifecycle.
 *
 * <p>Replaces the 1.16.5 {@code ClientProxy}: {@code onRenderPlayerHand} (cancel vanilla hand render
 * → {@link SmartRotomRenderer}) and the {@code onTick} pad GC. Each SmartRotom item has its own
 * browser keyed by its {@code smartrotom_id}; browsers are created for the item you're holding and
 * kept alive while the item stays anywhere in your hotbar/offhand, then closed when it leaves — so
 * we never keep a live Chromium instance for every SmartRotom in a full inventory.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static final IItemRenderer SMARTROTOM_RENDERER = new SmartRotomRenderer();

    /** Run the browser GC every 10 ticks, matching 1.16.5. */
    private static final int GC_INTERVAL_TICKS = 10;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onRenderPlayerHand(RenderHandEvent ev) {
        if (!ItemInit.SMARTROTOM.isBound() || ev.getItemStack().getItem() != ItemInit.SMARTROTOM.get()) {
            return;
        }

        float handSideSign = (ev.getHand() == InteractionHand.MAIN_HAND) ? 1.0f : -1.0f;
        SMARTROTOM_RENDERER.render(
                ev.getPoseStack(),
                ev.getItemStack(),
                handSideSign,
                ev.getSwingProgress(),
                ev.getEquipProgress(),
                ev.getMultiBufferSource(),
                ev.getPackedLight());
        ev.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post ev) {
        if (++tickCounter < GC_INTERVAL_TICKS) return;
        tickCounter = 0;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (TerasMCEF.activeBrowserIds().isEmpty() && !TerasMCEF.isReady()) return;

        // Collect the ids of every SmartRotom currently in the hotbar (slots 0-8) or offhand, and
        // (lazily) create the browser for the one(s) actually held so the in-hand renderer shows it.
        Inventory inv = player.getInventory();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        Set<UUID> present = new HashSet<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            trackSmartRotom(inv.getItem(slot), mainHand, offHand, present);
        }
        trackSmartRotom(offHand, mainHand, offHand, present);

        // Close browsers for items that have left the hotbar/offhand.
        for (UUID id : new ArrayList<>(TerasMCEF.activeBrowserIds())) {
            if (!present.contains(id)) {
                TerasMCEF.closeBrowser(id);
            }
        }
    }

    private static void trackSmartRotom(ItemStack stack, ItemStack mainHand, ItemStack offHand, Set<UUID> present) {
        if (!(stack.getItem() instanceof SmartRotom)) return;
        UUID id = SmartRotom.getId(stack);
        if (id == null) return; // id not yet assigned/synced by the server
        present.add(id);

        // Create the browser only for the item actually in a hand (identity match), so it's ready for
        // the in-hand renderer; other hotbar SmartRotoms are merely kept alive, not eagerly created.
        boolean held = (stack == mainHand || stack == offHand);
        if (held && TerasMCEF.isReady() && ServerConfig.isSynced() && TerasMCEF.getBrowser(id) == null) {
            TerasMCEF.getOrCreateBrowser(id, ServerConfig.getHome());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut ev) {
        // Free every Chromium instance when leaving a world/server.
        TerasMCEF.closeAll();
        // The config belonged to the server we just left; the next one sends its own.
        ServerConfig.clear();
    }
}
