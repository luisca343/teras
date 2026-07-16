package es.boffmedia.teras.battle.model;

import java.util.List;

/**
 * Engine-neutral snapshot of one battle-team member, shaped to the SmartRotom battle-achievement
 * payload. Each {@link es.boffmedia.teras.battle.api.BattleProvider} maps its own engine's Pokémon
 * type onto this record; Gson serialises it verbatim.
 *
 * <p>The stat arrays are ordered {@code [HP, ATK, DEF, SPA, SPD, SPE]}.</p>
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
