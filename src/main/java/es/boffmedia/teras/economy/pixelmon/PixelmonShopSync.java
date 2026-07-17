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
 * Mirrors every Pixelmon shopkeeper buy/sell to starbank's {@code /shop} route. This is the only
 * place the npc and item names the ledger memo needs actually exist — the generic
 * {@link es.boffmedia.teras.economy.EconomyStore} funnel sees a bare amount. Compiles against
 * Pixelmon; only registered from behind the {@code isPixelmonLoaded()} guard in
 * {@link PixelmonEconomyHook}.
 *
 * <p>Pixelmon's ShopTransactionPacket has already run {@code BankAccount.take}/{@code add} (→ the
 * cache) by the time {@code Post} fires, so this writes to the backend only; touching the cache here
 * would double-count. Registered on {@code Pixelmon.EVENT_BUS} — {@code ShopEvent} is a plain
 * neoforged event, so the wrong bus compiles and silently never fires.</p>
 *
 * <p>{@code Purchase.Post} carries the same ItemStack instance the packet handler passed to
 * {@code player.addItem}, which drains it to count 0 on insert — and an empty stack reads as AIR,
 * so at Post time neither the type nor the count of a buy is recoverable from the event. The stack
 * is therefore captured at {@code Purchase.Pre} (full requested count, real type) and reconciled at
 * Post, where the event stack's count is the leftover that didn't fit in the inventory — Pixelmon
 * only charges for the inserted part.</p>
 */
public final class PixelmonShopSync {
    private PixelmonShopSync() {}

    private static boolean registered;

    /** Pre-time copies of in-flight buys, keyed by buyer. Server thread only; Pre and Post of one
     * purchase run synchronously inside the same packet handler, so at most the latest entry per
     * player is live (a cancelled Pre leaves a stale entry that the next buy overwrites). */
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
     * The stack actually bought: the Pre-time copy shrunk by whatever the Post-time stack still
     * holds (the part that didn't fit and wasn't charged). Empty when nothing was inserted, or when
     * no Pre was seen for this player and the event stack is drained — the latter is WARNed because
     * it means a real money move goes unreported.
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
            // The cache already moved (the skip flag ate the funnel sync expecting /shop to cover it),
            // so an unpriceable transaction must fall back to a generic balance sync or starbank never
            // hears about a real money move.
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
        // On the ordered lane, not EXECUTOR: /shop applies a delta the cache already contains, so a
        // later funnel /set-balance overtaking it on the wire would apply that delta twice.
        EconomyStore.runOrdered(() ->
                SmartRotomService.shopTransaction(uuid, buy, npcName, itemName, unitPrice, count));
    }

    /**
     * The per-unit price this shop lists for {@code item} on the given side, or 0 when it lists no
     * usable price. A shop can list the same item type more than once (a buy entry and a sell entry),
     * so matching by {@link ItemStack#isSameItem} alone and taking the first hit can return the wrong
     * side's price — 0 for a purchase against a sell-only entry, which silently dropped every buy.
     *
     * <p>So: prefer the entry actually priced for this operation ({@link ShopItem#isPurchasable()} /
     * {@link ShopItem#isSellable()} with a positive price); fall back to the first same-type price seen,
     * which still prices the common single-entry shop and guards against a stale purchasable/sellable
     * flag.</p>
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
