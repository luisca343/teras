package es.boffmedia.teras.http;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.give.api.GiveProvider;
import es.boffmedia.teras.give.api.GiveProviders;
import es.boffmedia.teras.util.ItemResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-thread delivery for {@code /givepokemon} and {@code /giveitems}. Every method here must run
 * on the server thread — parties and inventories are game state.
 *
 * <p>The player is resolved <b>online-only</b>. The backend has already spent the ledger row by the
 * time it calls, so an offline player means the reward is lost; that is logged by the caller as the
 * only trace, exactly as {@code TerasNet.deliverCaja} does for darCaja.</p>
 */
final class GiveService {
    private GiveService() {}

    /** True if the Pokémon landed. False on any failure — the caller has already spent it. */
    static boolean givePokemon(MinecraftServer mc, GiveRequests.PokemonGive req) {
        ServerPlayer player = mc.getPlayerList().getPlayer(req.uuid());
        if (player == null) {
            return false;
        }
        GiveProvider provider = GiveProviders.get();
        if (provider == null) {
            Teras.LOGGER.error("givePokemon: no Pokémon engine installed; cannot grant '{}'", req.pokespec());
            return false;
        }
        return provider.givePokemon(player, req.pokespec(), req.sendMessage());
    }

    /**
     * The number of stacks delivered, or {@code -1} if the player is offline (nothing delivered).
     *
     * <p>Zero is distinct from {@code -1}: it means the player was here but every id was unresolvable —
     * still a delivered claim, not a lost one.</p>
     */
    static int giveItems(MinecraftServer mc, GiveRequests.ItemsGive req) {
        ServerPlayer player = mc.getPlayerList().getPlayer(req.uuid());
        if (player == null) {
            return -1;
        }
        int delivered = 0;
        for (GiveRequests.ItemGive item : req.items()) {
            ItemStack stack = ItemResolver.resolve(item.id(), item.amount());
            if (stack == null) {
                continue;
            }
            ItemResolver.applyDisplay(stack, item.displayName(), item.lore());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            delivered++;
        }
        return delivered;
    }
}
