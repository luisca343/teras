package es.boffmedia.teras.shiny.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.palette.PaletteProperties;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.integration.PokemonEngines;
import es.boffmedia.teras.shiny.api.ShinyCandidate;
import es.boffmedia.teras.shiny.api.ShinyProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Pixelmon's read for the shiny cue. Compiles against Pixelmon ({@code compileOnly}), so it is only
 * ever named from behind an {@code isPixelmonLoaded()} guard — see
 * {@link es.boffmedia.teras.shiny.api.ShinyProviders}.
 *
 * <p>Deliberately narrower than {@link es.boffmedia.teras.dex.pixelmon.PixelmonDexProvider}, which
 * accepts any {@code AbstractBaseEntity} so it also scans statues. A statue is furniture: it can be
 * shiny, it is not a sighting, and it never moves — so this asks for {@link PixelmonEntity}
 * specifically, and the statue case is excluded by the type rather than by a flag.</p>
 *
 * <p><b>9.3.16 note:</b> {@code Pokemon.isShiny()} no longer exists (only {@code setShiny} survived
 * the rewrite), so shininess is read off the palette name. That is also why
 * {@link es.boffmedia.teras.shiny.ShinyRules} works in palettes: it is the surviving API, not a
 * preference.</p>
 */
public final class PixelmonShinyProvider implements ShinyProvider {

    @Override
    public String engineId() {
        return PokemonEngines.PIXELMON;
    }

    @Override
    public ShinyCandidate read(Entity entity) {
        if (!(entity instanceof PixelmonEntity pokemonEntity)) {
            return null;
        }
        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) {
            return null;
        }
        PaletteProperties palette = pokemon.getPalette();
        return new ShinyCandidate(
                palette == null ? ShinyCandidate.NONE : palette.getName(),
                pokemonEntity.getOwner() == null,
                pokemon.isBoss(),
                !pokemonEntity.isUncatchable());
    }

    @Override
    public boolean isBattling(ServerPlayer player) {
        return BattleRegistry.getBattle(player) != null;
    }
}
