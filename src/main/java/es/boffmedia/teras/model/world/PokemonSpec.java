package es.boffmedia.teras.model.world;

/**
 * One party grant in a darCaja response: a Pixelmon-format spec and how many to give. A Pokémon is not
 * a stack, so it travels separately from {@link ObjetoMC} — a chest can't hold an Incineroar.
 */
public record PokemonSpec(String spec, int cantidad) {}
