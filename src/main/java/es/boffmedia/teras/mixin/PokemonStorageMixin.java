package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import es.boffmedia.teras.storage.StorageSync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a player's storage dirty whenever a Pokémon enters, leaves or replaces a slot, so an open
 * SmartRotom PC refetches instead of acting on a view the game has moved past.
 *
 * <p>{@code notifyListeners} is the funnel every tracked mutation passes through — the in-game PC
 * screen, catching, depositing, trading, evolving — which is why it is hooked rather than the
 * individual call sites.</p>
 *
 * <p>{@code TAIL}, not {@code RETURN}: the method returns early when {@code shouldSendUpdates()} is
 * false, and those are exactly the bulk operations that suppress updates and send a full refresh of
 * their own afterwards.</p>
 */
@Mixin(PokemonStorage.class)
public abstract class PokemonStorageMixin {

    // require = 0: this is a convenience refresh, not a feature the game needs. If a Pixelmon update
    // moves notifyListeners out from under the injector, the right outcome is losing the live refresh
    // (PlayerCloseStorageEvent still covers the main case) — not refusing to launch.
    @Inject(method = "notifyListeners", at = @At("TAIL"), require = 0)
    private void teras$markStorageDirty(StoragePosition position, Pokemon pokemon,
                                        EnumUpdateType[] types, CallbackInfo ci) {
        // This mixin applies on both dists, and the Pixelmon PC screen mutates the CLIENT's own copy
        // of the storage (PokemonScreen.tryToSwap -> transfer -> set -> here). There is no server
        // there to resolve players against: getOwner() dereferences getCurrentServer() with no null
        // check and takes the game down. In single-player the integrated server is non-null on the
        // render thread too, so the thread check — not the null check — is what separates the two
        // copies of the same storage.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        // An EMPTY type array means the whole slot was replaced — Pixelmon's own signal for a
        // structural change, which is the only kind that can invalidate a positional swap. A
        // populated one is a field update (HP, Experience, Friendship…), and those fire on every
        // hit of every battle and every step taken: refetching 900 Pokémon for them would turn a
        // battle into a stream of full PC reloads.
        if (types.length > 0) {
            return;
        }
        PokemonStorage self = (PokemonStorage) (Object) this;
        // The owner covers a change made while nothing is open (a catch); the tracking players cover
        // a PCBox, whose own uuid is not the player's but whose viewers are the ones going stale.
        StorageSync.markDirty(self.getOwner());
        for (ServerPlayer viewer : self.trackingPlayers()) {
            StorageSync.markDirty(viewer);
        }
    }
}
