package es.boffmedia.teras.model.world;

import java.util.List;

/**
 * A darCaja claim result: item stacks to chest ({@code objetos}) and Pokémon to give to the party
 * ({@code pokemon}). Both come off the response root. {@code pokemon} is empty for item-only sources
 * (mine), which is what keeps a source-only claim identical to the pre-arcade contract.
 *
 * <p>A {@code null} {@code CajaGrant} means the claim failed — grant nothing. An empty one (both lists
 * empty) means the claim succeeded and the player was owed nothing, which is not an error.</p>
 */
public record CajaGrant(List<ObjetoMC> objetos, List<PokemonSpec> pokemon) {

    public boolean isEmpty() {
        return objetos.isEmpty() && pokemon.isEmpty();
    }
}
