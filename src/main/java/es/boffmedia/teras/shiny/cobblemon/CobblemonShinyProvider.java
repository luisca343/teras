package es.boffmedia.teras.shiny.cobblemon;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import es.boffmedia.teras.integration.PokemonEngines;
import es.boffmedia.teras.shiny.api.ShinyCandidate;
import es.boffmedia.teras.shiny.api.ShinyProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon's read for the shiny cue — the twin of
 * {@link es.boffmedia.teras.shiny.pixelmon.PixelmonShinyProvider}, and the same {@code compileOnly}
 * class-load rules apply.
 *
 * <p>Absorbs the three places the engines differ: Cobblemon has a real {@code getShiny()} boolean
 * rather than a palette (mapped to the same {@code shiny}/{@code none} vocabulary
 * {@link es.boffmedia.teras.dex.cobblemon.CobblemonDex} already uses), it has no bosses, and it has
 * no uncatchable flag — but it does have <b>battle clones</b>, temporary copies stood up for a
 * battle, which are excluded here for the same reason an owned Pokémon is: sparkling at one would
 * announce a sighting that is not there to be caught.</p>
 */
public final class CobblemonShinyProvider implements ShinyProvider {

    @Override
    public String engineId() {
        return PokemonEngines.COBBLEMON;
    }

    @Override
    public ShinyCandidate read(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return null;
        }
        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) {
            return null;
        }
        boolean wild = pokemonEntity.getOwner() == null
                && !pokemonEntity.isBattleClone()
                && !pokemonEntity.isBattling();
        return new ShinyCandidate(
                pokemon.getShiny() ? "shiny" : ShinyCandidate.NONE,
                wild,
                false,
                true);
    }

    @Override
    public boolean isBattling(ServerPlayer player) {
        return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
    }
}
