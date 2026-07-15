package es.boffmedia.teras.battle.model;

import java.util.List;

/**
 * Engine-neutral snapshot of one battle-team member, shaped to the SmartRotom battle-achievement DTO
 * ({@code dex, nature, species, form, palette, name, level, item, ability, moves, ivs, evs, stats}).
 * Each {@link es.boffmedia.teras.battle.api.BattleProvider} maps its own engine's Pokémon type onto
 * this record and Gson serialises it verbatim to the JSON the backend validates.
 *
 * <p>The three stat arrays are ordered {@code [HP, ATK, DEF, SPA, SPD, SPE]} (Showdown order).</p>
 */
public record TeamMember(
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
        List<Integer> stats) {
}
