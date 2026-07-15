package es.boffmedia.teras.battle.config;

import java.util.Locale;

/**
 * Engine-neutral battle format. Each provider maps this to its own type
 * (Pixelmon {@code BattleType}, Cobblemon {@code BattleFormat}).
 *
 * <p>The two counts are how many Pokémon each side sends out at once — e.g. doubles is 2v2, a horde
 * is 1 player vs 5, a raid is 4 players vs 1 boss.</p>
 */
public enum BattleMode {
    SINGLE(1, 1),
    DOUBLE(2, 2),
    TRIPLE(3, 3),
    ROTATION(3, 3),
    HORDE(1, 5),
    RAID(4, 1);

    private final int playerActive;
    private final int rivalActive;

    BattleMode(int playerActive, int rivalActive) {
        this.playerActive = playerActive;
        this.rivalActive = rivalActive;
    }

    /** Pokémon the player controls at once (1 singles, 2 doubles, …). */
    public int playerActive() {
        return playerActive;
    }

    /** Pokémon the rival controls at once (1 singles, 2 doubles, 5 horde, 1 raid boss). */
    public int rivalActive() {
        return rivalActive;
    }

    /** Maps the Spanish {@code modalidad} label from the config JSON; unknown → {@link #SINGLE}. */
    public static BattleMode fromLabel(String modalidad) {
        switch (modalidad == null ? "" : modalidad.toLowerCase(Locale.ROOT)) {
            case "doble":
                return DOUBLE;
            case "triple":
                return TRIPLE;
            case "rotatorio":
                return ROTATION;
            case "horda":
                return HORDE;
            case "raid":
                return RAID;
            default:
                return SINGLE;
        }
    }
}
