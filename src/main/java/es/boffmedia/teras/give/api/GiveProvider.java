package es.boffmedia.teras.give.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Gives a player a Pokémon from a spec string, for whichever engine is installed — mirroring
 * {@code battle.api.BattleProvider} and {@code dex.api.DexProvider}. Exactly one is active at runtime,
 * chosen by {@link GiveProviders}.
 *
 * <p>Every implementation must be called on the <b>server thread</b>: party and PC storage are game
 * state.</p>
 */
public interface GiveProvider {

    /** The engine this provider drives ({@code pixelmon} / {@code cobblemon}). */
    String engineId();

    /**
     * Gives {@code player} the Pokémon described by {@code spec}, returning whether it landed.
     *
     * <p><b>Specs are Pixelmon {@code PokemonSpec} syntax</b> ({@code Incineroar lvl:50 otn:Wolfey
     * ivhp:31}) — that is what the backend stores for every live row, whatever engine is running. A
     * provider that cannot express a spec must return {@code false}, never a substitute: granting the
     * wrong Pokémon is worse than granting none, because nothing downstream would ever notice.</p>
     *
     * @param sendMessage whether to tell the player in chat (1.16.5 honoured this; the backend sends it)
     */
    boolean givePokemon(ServerPlayer player, String spec, boolean sendMessage);
}
