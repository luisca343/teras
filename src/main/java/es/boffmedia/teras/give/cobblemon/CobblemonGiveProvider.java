package es.boffmedia.teras.give.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.give.api.GiveProvider;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon {@link GiveProvider}. Specs arrive in Pixelmon syntax, so this delegates to
 * {@link CobblemonSpecTranslator} — which refuses anything it can't map for certain, because
 * Cobblemon parses leniently and would silently downgrade an unmapped spec (see that class).
 *
 * <p>Cobblemon-coupled: only reachable behind {@link PokemonEngines#isCobblemonLoaded()}.</p>
 */
public final class CobblemonGiveProvider implements GiveProvider {

    @Override
    public String engineId() {
        return PokemonEngines.COBBLEMON;
    }

    @Override
    public boolean givePokemon(ServerPlayer player, String spec, boolean sendMessage) {
        if (player == null || spec == null || spec.isBlank()) {
            return false;
        }
        String properties = CobblemonSpecTranslator.translate(spec);
        if (properties == null) {
            return false;
        }
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse(properties, " ", "=").create();
            boolean given = Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
            if (given && sendMessage) {
                player.sendSystemMessage(Component.literal("Has recibido ")
                        .append(Component.literal(pokemon.getSpecies().getName())
                                .withStyle(ChatFormatting.YELLOW))
                        .append("!"));
            } else if (!given) {
                Teras.LOGGER.error("givePokemon: could not add '{}' to {}'s party", spec,
                        player.getGameProfile().getName());
            }
            return given;
        } catch (Exception e) {
            Teras.LOGGER.error("givePokemon: Cobblemon rejected '{}' (from spec '{}'): {}",
                    properties, spec, e.toString());
            return false;
        }
    }
}
