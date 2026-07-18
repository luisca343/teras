package es.boffmedia.teras.storage.model;

import java.util.List;

/**
 * One Pokémon as the SmartRotom PC reads it — the backend's {@code ExtendedPokemonW}.
 *
 * <p>Not {@link es.boffmedia.teras.battle.model.TeamMember}, though the first thirteen components
 * line up: {@code item} here is a <b>description id</b> ({@code item.minecraft.air}), which is what
 * the web PC tests emptiness against and renders the last dot-segment of, and {@code hp}/{@code
 * status} have no honest value in a battle snapshot.</p>
 *
 * <p>Stat arrays are {@code [HP, ATK, DEF, SPA, SPD, SPE]}. No {@code types}: the web PC looks those
 * up on the species itself.</p>
 */
public record StoredMon(
        int dex,
        String nature,
        String species,
        String form,
        String palette,
        String name,
        int level,
        String item,
        String ability,
        List<String> moves,
        List<Integer> ivs,
        List<Integer> evs,
        List<Integer> stats,
        int hp,
        String gender,
        String status) {
}
