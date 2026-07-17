package es.boffmedia.teras.economy.pixelmon;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.ShopEvent;
import com.pixelmonmod.pixelmon.api.shop.ShopBuilder;
import com.pixelmonmod.pixelmon.api.shop.ShopItem;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.economy.EconomyStore;
import es.boffmedia.teras.util.net.SmartRotomService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Mirrors every Pixelmon shopkeeper buy/sell to starbank's {@code /shop} route — the only place the
 * npc and item names the ledger memo needs still exist (the {@link EconomyStore} funnel sees a bare
 * amount). Compiles against Pixelmon; installed only behind the {@code isPixelmonLoaded()} guard in
 * {@link PixelmonEconomyHook}.
 *
 * <p>By {@code Post} the transaction has already run {@code BankAccount.take}/{@code add} into the
 * cache, so this writes the backend only; touching the cache would double-count. Registered on
 * {@code Pixelmon.EVENT_BUS} — {@code ShopEvent} posts there, not on the NeoForge bus.</p>
 *
 * <p>{@code Purchase.Post} carries the {@code addItem} stack, which is drained to count 0 (and reads
 * as AIR) once inserted, so the buy's type and count are captured at {@code Purchase.Pre} and
 * reconciled at Post, where the leftover count is the part that didn't fit — Pixelmon charges only
 * for the rest.</p>
 */
public final class PixelmonShopSync {
    private PixelmonShopSync() {}

    private static boolean registered;

    /** Pre-time copies of in-flight buys, keyed by buyer. Server thread only; Pre and Post of one
     * purchase run synchronously, so at most the latest entry per player is live. */
    private static final java.util.Map<UUID, ItemStack> PENDING_BUYS = new java.util.HashMap<>();

    public static void install() {
        if (registered) {
            return;
        }
        registered = true;
        Pixelmon.EVENT_BUS.register(PixelmonShopSync.class);
        Teras.LOGGER.info("Teras shop sync installed (pixelmon)");
    }

    @SubscribeEvent
    public static void onPurchasePre(ShopEvent.Purchase.Pre event) {
        ServerPlayer player = event.getPlayer();
        ItemStack item = event.getItem();
        if (player != null && item != null && !item.isEmpty()) {
            PENDING_BUYS.put(player.getUUID(), item.copy());
            EconomyStore.skipNextSync(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onSellPre(ShopEvent.Sell.Pre event) {
        if (event.getPlayer() != null) {
            EconomyStore.skipNextSync(event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPurchase(ShopEvent.Purchase.Post event) {
        if (event.getPlayer() != null) {
            EconomyStore.clearSkipNextSync(event.getPlayer().getUUID());
        }
        report(event, resolveBought(event), true);
    }

    @SubscribeEvent
    public static void onSell(ShopEvent.Sell.Post event) {
        if (event.getPlayer() != null) {
            EconomyStore.clearSkipNextSync(event.getPlayer().getUUID());
        }
        report(event, event.getItem(), false);
    }

    /**
     * The stack actually bought: the Pre-time copy shrunk by the Post-time leftover (the part that
     * didn't fit and wasn't charged). Empty when nothing was inserted, or when no Pre was seen and the
     * event stack is drained — the latter is WARNed, as a real money move then goes unreported.
     */
    private static ItemStack resolveBought(ShopEvent.Purchase.Post event) {
        ItemStack leftover = event.getItem();
        ItemStack requested = event.getPlayer() != null ? PENDING_BUYS.remove(event.getPlayer().getUUID()) : null;
        if (requested == null) {
            if (leftover == null || leftover.isEmpty()) {
                Teras.LOGGER.warn("Shop purchase NOT synced to starbank: Post carried a drained stack and no Pre was captured");
            }
            return leftover;
        }
        int bought = requested.getCount() - (leftover == null ? 0 : leftover.getCount());
        return bought > 0 ? requested.copyWithCount(bought) : ItemStack.EMPTY;
    }

    private static void report(ShopEvent event, ItemStack item, boolean buy) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null || item == null || item.isEmpty()) {
            return;
        }
        long unitPrice = Math.round(unitPrice(event.getShop(), item, buy));
        if (unitPrice <= 0) {
            // The skip flag already ate the funnel sync, so an unpriceable transaction needs a generic
            // fallback sync or starbank never hears about the money move.
            Teras.LOGGER.warn("Shop {} has no {} price for {}x {} at {} — syncing as a generic balance set",
                    buy ? "purchase" : "sale", buy ? "buy" : "sell",
                    item.getCount(), item.getHoverName().getString(), npcName(event.getEntity()));
            EconomyStore.requestSync(player.getUUID(), "[JUEGO] Operación de tienda (sin precio)");
            return;
        }
        UUID uuid = player.getUUID();
        String npcName = npcName(event.getEntity());
        String itemName = item.getHoverName().getString();
        int count = item.getCount();
        // Ordered lane, not EXECUTOR: /shop applies a delta the cache already holds; a later
        // /set-balance overtaking it would double-apply.
        EconomyStore.runOrdered(() ->
                SmartRotomService.shopTransaction(uuid, buy, npcName, itemName, unitPrice, count));
    }

    /**
     * The per-unit price this shop lists for {@code item} on the given side, or 0 when it lists none. A
     * shop can list the same item type on both a buy and a sell entry, so prefer the entry priced for
     * this operation ({@link ShopItem#isPurchasable()}/{@link ShopItem#isSellable()} with a positive
     * price) and fall back to the first same-type price, which still prices a single-entry shop.
     */
    private static double unitPrice(ShopBuilder shop, ItemStack item, boolean buy) {
        if (shop == null) {
            return 0;
        }
        double sameTypeFallback = 0;
        for (ShopItem shopItem : shop.items()) {
            if (shopItem == null || !ItemStack.isSameItem(shopItem.itemStack(), item)) {
                continue;
            }
            double price = buy ? shopItem.buyPrice() : shopItem.sellPrice();
            boolean pricedForThisSide = buy ? shopItem.isPurchasable() : shopItem.isSellable();
            if (pricedForThisSide && price > 0) {
                return price;
            }
            if (sameTypeFallback <= 0 && price > 0) {
                sameTypeFallback = price;
            }
        }
        return sameTypeFallback;
    }

    private static String npcName(Entity entity) {
        return entity != null ? entity.getName().getString() : "Tienda";
    }
}
