package es.boffmedia.teras.model.world;

/**
 * One entry in a darCaja grant. Port of the 1.16.5 {@code model.world.ObjetoMC}.
 *
 * <p>The component names are the JSON keys SmartRotom sends, and Gson binds them by name — renaming
 * either one breaks deserialization silently rather than at compile time.</p>
 */
public record ObjetoMC(String id, int cantidad) {}
