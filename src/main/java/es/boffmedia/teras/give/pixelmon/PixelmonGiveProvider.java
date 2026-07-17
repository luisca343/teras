package es.boffmedia.teras.give.pixelmon;

import com.pixelmonmod.api.parsing.ParseAttempt;
import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.give.api.GiveProvider;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pixelmon {@link GiveProvider}. Port of the 1.16.5 {@code util.PokemonHelper.givePokemonToPlayer}.
 *
 * <p>The spec syntax is Pixelmon's own, so nothing is translated here — this is the engine the
 * backend's stored specs were written for.</p>
 *
 * <p>Pixelmon-coupled: only reachable behind {@link PokemonEngines#isPixelmonLoaded()}.</p>
 */
public final class PixelmonGiveProvider implements GiveProvider {

    @Override
    public String engineId() {
        return PokemonEngines.PIXELMON;
    }

    @Override
    public boolean givePokemon(ServerPlayer player, String spec, boolean sendMessage) {
        if (player == null || spec == null || spec.isBlank()) {
            return false;
        }
        try {
            ParseAttempt<PokemonSpecification> parsed = PokemonSpecificationProxy.create(spec);
            if (parsed == null || !parsed.wasSuccess()) {
                Teras.LOGGER.error("givePokemon: unparseable spec '{}': {}", spec,
                        parsed == null ? "null" : parsed.getError());
                return false;
            }
            Pokemon pokemon = PokemonFactory.create(parsed.get());
            if (pokemon == null) {
                Teras.LOGGER.error("givePokemon: spec '{}' produced no Pokémon", spec);
                return false;
            }

            // 1.16.5 took StorageProxy.getParty(uuid), which blocked on the storage load. 9.3.16's
            // getPartyNow returns what is already loaded — correct here because the player is online
            // (we resolved them off the player list) and so their party is in memory.
            PlayerPartyStorage party = StorageProxy.getPartyNow(player);
            if (party == null) {
                Teras.LOGGER.error("givePokemon: party unavailable for {}", player.getGameProfile().getName());
                return false;
            }

            boolean given = party.add(pokemon);
            if (!given) {
                PCStorage pc = StorageProxy.getPCForPlayerNow(player);
                given = pc != null && pc.add(pokemon);
            }
            if (given && sendMessage) {
                player.sendSystemMessage(Component.literal("Has recibido ")
                        .append(Component.literal(pokemon.getSpecies().getName())
                                .withStyle(ChatFormatting.YELLOW))
                        .append("!"));
            }
            return given;
        } catch (Exception e) {
            Teras.LOGGER.error("givePokemon: failed for {} with spec '{}': {}",
                    player.getGameProfile().getName(), spec, e.toString());
            return false;
        }
    }
}
