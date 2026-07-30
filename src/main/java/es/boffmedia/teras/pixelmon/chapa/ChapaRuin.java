package es.boffmedia.teras.pixelmon.chapa;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.comm.ChatHandler;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the rusty cap actually does to a Pokémon, and what the selection screen previews.
 *
 * <p>Only ever named behind a {@code ModList.isLoaded("pixelmon")} guard — see {@link ChapaBridge}.</p>
 */
public final class ChapaRuin {
    private ChapaRuin() {}

    /** The IV a ruined stat is left at. The whole point of the item. */
    public static final int RUINED = 0;

    /**
     * Drops {@code type}'s IV to {@link #RUINED}.
     *
     * @return false when there is nothing to ruin — already 0, or hyper trained, which pins the
     *         effective IV at 31 and would make the cap silently do nothing
     */
    public static boolean ruin(ServerPlayer player, Pokemon pokemon, BattleStatsType type) {
        IVStore ivs = pokemon.getIVs();
        if (ivs.isHyperTrained(type) || ivs.getStat(type) == RUINED) {
            return false;
        }
        ivs.setStat(type, RUINED);
        // recalculateStats() over setLevelStats(): it does the same recalculation but restores the
        // health *percentage* afterwards, so ruining the HP IV shrinks the bar instead of leaving a
        // Pokémon on more current HP than its new maximum.
        pokemon.getStats().recalculateStats();
        pokemon.markDirty(EnumUpdateType.HP, EnumUpdateType.Stats);
        // 9.3.16 dropped BattleStatsType.getTranslatedName(); the semi-abbreviated one is the
        // closest survivor ("Ataque", "At. Esp.").
        ChatHandler.sendChat(player, "teras.chapa_oxidada.ruined",
                pokemon.getDisplayName(), type.getSemiAbbreviatedTranslatedName());
        return true;
    }

    /**
     * What {@code type} would read at were it ruined — the number the selection screen shows.
     *
     * <p>{@code calculateStat} has no IV override and reads the live store, so the preview has to
     * write, measure and put back. The 1.16.5 version did the write and skipped the put-back, so
     * <i>opening</i> the screen zeroed all six IVs before the player chose anything; the restore
     * is in a finally so a throw mid-calculation cannot resurrect that.</p>
     *
     * @return 0 for a stat the cap cannot touch, which the screen renders as unavailable
     */
    public static int preview(Pokemon pokemon, BattleStatsType type) {
        IVStore ivs = pokemon.getIVs();
        if (ivs.isHyperTrained(type) || ivs.getStat(type) == RUINED) {
            return 0;
        }
        int original = ivs.getStat(type);
        try {
            ivs.setStat(type, RUINED);
            return pokemon.getStats().calculateStat(type, pokemon.getNature(),
                    pokemon.getForm(), pokemon.getPokemonLevel());
        } finally {
            ivs.setStat(type, original);
        }
    }
}
