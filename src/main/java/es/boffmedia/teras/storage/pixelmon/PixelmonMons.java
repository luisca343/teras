package es.boffmedia.teras.storage.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import es.boffmedia.teras.pixelmon.PokemonFields;
import es.boffmedia.teras.storage.model.StoredMon;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Pixelmon {@link Pokemon} → {@link StoredMon}, shared by the PC routes and the battle teams.
 *
 * <p>Shared deliberately: the web PC matches a team slot to the Pokémon sitting in storage by hashing
 * dex, palette, nature, ability and IVs. Two mappers that drifted on any of those would stop the
 * teams resolving to their real box positions.</p>
 */
public final class PixelmonMons {
    private PixelmonMons() {}

    public static StoredMon read(Pokemon pokemon) {
        return new StoredMon(
                pokemon.getDex(),
                PokemonFields.nature(pokemon),
                PokemonFields.species(pokemon),
                PokemonFields.form(pokemon),
                PokemonFields.palette(pokemon),
                PokemonFields.displayName(pokemon),
                pokemon.getPokemonLevel(),
                heldItemId(pokemon.getHeldItem()),
                PokemonFields.ability(pokemon),
                PokemonFields.moves(pokemon),
                PokemonFields.ivs(pokemon),
                PokemonFields.evs(pokemon),
                PokemonFields.stats(pokemon),
                pokemon.getHealth(),
                pokemon.getGender() != null
                        ? pokemon.getGender().name().toLowerCase(Locale.ROOT) : "none",
                status(pokemon));
    }

    /** Description id, not registry key — see {@link StoredMon}. */
    private static String heldItemId(ItemStack stack) {
        return (stack == null ? ItemStack.EMPTY : stack).getDescriptionId();
    }

    /** Fainting is not a Pixelmon status, but the web PC greys a Pokémon out on {@code "fainted"}. */
    private static String status(Pokemon pokemon) {
        if (pokemon.getHealth() <= 0) {
            return "fainted";
        }
        if (pokemon.getStatus() == null || pokemon.getStatus().type == null) {
            return "none";
        }
        return pokemon.getStatus().type.name().toLowerCase(Locale.ROOT);
    }
}
