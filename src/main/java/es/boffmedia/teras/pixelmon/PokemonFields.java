package es.boffmedia.teras.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.EVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.api.pokemon.stats.PermanentStats;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a Pixelmon {@link Pokemon} into the field values the SmartRotom wire uses, shared by
 * {@code battle.model.TeamMember} and {@code storage.model.StoredMon}. Those two records differ in
 * held-item encoding and live HP/status, so only the fiddly parts are shared — stat ordering,
 * Pixelmon's several names for "no form", null-tolerance.
 *
 * <p>Only ever named behind a {@code ModList.isLoaded("pixelmon")} guard.</p>
 */
public final class PokemonFields {
    private PokemonFields() {}

    /** The order the wire packs ivs/evs/stats. */
    private static final BattleStatsType[] STAT_ORDER = {
            BattleStatsType.HP, BattleStatsType.ATTACK, BattleStatsType.DEFENSE,
            BattleStatsType.SPECIAL_ATTACK, BattleStatsType.SPECIAL_DEFENSE, BattleStatsType.SPEED};

    public static String species(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        return species != null ? species.getName() : "";
    }

    /** Nickname, else species — never null; {@code name} is required. */
    public static String displayName(Pokemon pokemon) {
        return pokemon.getNickname() != null ? pokemon.getNickname().getString() : species(pokemon);
    }

    /** {@code ""} for a base form. */
    public static String form(Pokemon pokemon) {
        String form = pokemon.getFormName();
        return isBaseForm(form) ? "" : form;
    }

    public static String palette(Pokemon pokemon) {
        return pokemon.getPalette() != null ? pokemon.getPalette().getName() : "none";
    }

    public static String nature(Pokemon pokemon) {
        return pokemon.getNature() != null ? capitalize(pokemon.getNature().getSerializedName()) : "";
    }

    public static String ability(Pokemon pokemon) {
        return pokemon.getAbility() != null ? pokemon.getAbility().getName() : "";
    }

    public static List<String> moves(Pokemon pokemon) {
        List<String> moves = new ArrayList<>();
        Moveset moveset = pokemon.getMoveset();
        if (moveset != null && moveset.attacks != null) {
            for (Attack attack : moveset.attacks) {
                if (attack != null && attack.getActualMove() != null) {
                    moves.add(attack.getActualMove().getAttackName());
                }
            }
        }
        return moves;
    }

    public static List<Integer> ivs(Pokemon pokemon) {
        IVStore ivs = pokemon.getIVs();
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(ivs != null ? ivs.getStat(stat) : 0);
        }
        return out;
    }

    public static List<Integer> evs(Pokemon pokemon) {
        EVStore evs = pokemon.getEVs();
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(evs != null ? evs.getStat(stat) : 0);
        }
        return out;
    }

    public static List<Integer> stats(Pokemon pokemon) {
        PermanentStats stats = pokemon.getStats();
        List<Integer> out = new ArrayList<>(STAT_ORDER.length);
        for (BattleStatsType stat : STAT_ORDER) {
            out.add(stats != null ? stats.get(stat) : 0);
        }
        return out;
    }

    /** Pixelmon names a base form null, "", "base" or "normal". */
    private static boolean isBaseForm(String form) {
        if (form == null || form.isBlank()) {
            return true;
        }
        String f = form.toLowerCase(Locale.ROOT);
        return f.equals("base") || f.equals("normal");
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
