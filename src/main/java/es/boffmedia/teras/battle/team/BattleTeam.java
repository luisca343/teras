package es.boffmedia.teras.battle.team;

import es.boffmedia.teras.storage.model.StoredMon;

import java.util.List;

/**
 * One saved battle team, as {@code POST /getallbattleteams} returns it.
 *
 * <p>{@code pokemon} is always six entries, {@code null} for an empty slot, and each is the same
 * {@link StoredMon} the PC routes send — the web panel matches a slot back to the Pokémon in storage
 * by hashing dex/palette/nature/ability/IVs, which only works if both come from one mapper.</p>
 *
 * <p>{@code id} is the team's name: teams are files named after themselves, so the name is already
 * the only stable identifier a player's team has.</p>
 */
public record BattleTeam(String id, String name, List<StoredMon> pokemon) {

    /** Slots per team, matching a Pixelmon party. */
    public static final int SIZE = 6;
}
