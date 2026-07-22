package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.instance.DungeonRun;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Where the six afflictions actually bite.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>{@link Afliccion} shipped as a catalog, {@link AfflictionSet} as storage, and both were read by
 * <b>nothing</b>. A party could not acquire one, and if it somehow had, the run would have played
 * identically. That is the same failure as the unread {@code loot} marker and the unread
 * {@code elite} flag: content authored, shipped, and invisible.</p>
 *
 * <p>Every effect is a query against the run's sets, answered where the decision is already being
 * made — the shop asking a price, the spawner sizing a wave, the healer applying a potion. Nothing
 * here pushes; the seams pull. That is what keeps an affliction from needing its own tick loop, and
 * what makes each one a two-line change at the site that already owns the number.</p>
 *
 * <h2>The two shapes</h2>
 *
 * <p><b>Queried</b> — Niebla, Enjambre, Avaricia, Sangría — read at the moment they matter, so they
 * take effect the instant they are accepted and need no bookkeeping.</p>
 *
 * <p><b>Applied</b> — Plomo and Pulso débil — change the player's body, so they are pushed by
 * {@link #apply} whenever the sets change and again on every floor and every respawn. A respawn is a
 * fresh entity with vanilla attributes; anything not re-applied there quietly wears off, which is
 * exactly how a devil deal's missing hearts used to come back.</p>
 */
public final class Afflictions {
    private Afflictions() {}

    /** Half-hearts of maximum health Pulso débil takes. Two hearts, matching the catalog text. */
    private static final int PULSO_DEBIL_HALF_HEARTS = 4;

    private static boolean party(DungeonRun run, Afliccion afliccion) {
        return run.afflictions().has(afliccion);
    }

    private static boolean personal(DungeonRun run, UUID member, Afliccion afliccion) {
        return run.stateOf(member).afflictions().has(afliccion);
    }

    // --- queried ---------------------------------------------------------------------------

    /** Niebla: the minimap goes dark, which is what makes the shop's map and compass worth buying. */
    public static boolean mapHidden(DungeonRun run) {
        return party(run, Afliccion.NIEBLA);
    }

    /**
     * Enjambre: half again as many enemies in a wave.
     *
     * <p>Applied to the count and paid for in the next method, so the fight changes shape — crowd
     * control over single target — rather than simply getting longer. An affliction that only added
     * enemies would fail the catalog's own rule.</p>
     */
    public static double waveCountMultiplier(DungeonRun run) {
        return party(run, Afliccion.ENJAMBRE) ? 1.5 : 1.0;
    }

    /** The other half of Enjambre: each of them is frailer and hits softer. */
    public static double waveStatMultiplier(DungeonRun run) {
        return party(run, Afliccion.ENJAMBRE) ? 0.7 : 1.0;
    }

    /** Avaricia: the shop charges double. The shop is where a run's decisions get made. */
    public static double shopPriceMultiplier(DungeonRun run) {
        return party(run, Afliccion.AVARICIA) ? 2.0 : 1.0;
    }

    /** Sangría: potions heal half, against a lockdown where a potion is the only healing there is. */
    public static float healMultiplier(DungeonRun run) {
        return party(run, Afliccion.SANGRIA) ? 0.5f : 1.0f;
    }

    /** Plomo: no sprinting, and yours alone. */
    public static boolean noSprint(DungeonRun run, UUID member) {
        return personal(run, member, Afliccion.PLOMO);
    }

    // --- applied ---------------------------------------------------------------------------

    /**
     * Pushes the body-changing afflictions onto {@code player}.
     *
     * <p>Called when the sets change, on arriving at a floor, and after a respawn — the three
     * moments at which a player's attributes are not what the run thinks they are.</p>
     */
    public static void apply(DungeonRun run, ServerPlayer player) {
        UUID member = player.getUUID();
        DungeonHealth.applyHpDebt(player, totalHpDebt(run, member));
        if (noSprint(run, member)) {
            player.setSprinting(false);
        }
    }

    /**
     * Every half-heart of maximum health this run has taken from {@code member} — sold to a devil
     * deal, or accepted as Pulso débil.
     *
     * <p><b>The single answer, because three separate places rebuild a player's body from it</b>:
     * arriving on a floor, respawning after a death, and closing a devil deal. Each read
     * {@code hpDebt()} raw, so an affliction added here and nowhere else would have evaporated the
     * next time any of the three ran — which for a floor transition is within a minute of buying it.
     * Rides the devil deal's own debt rather than a separate attribute modifier: it is the same
     * quantity, and two systems writing one attribute by different routes is how one of them
     * silently wins.</p>
     */
    public static int totalHpDebt(DungeonRun run, UUID member) {
        return run.stateOf(member).hpDebt()
                + (personal(run, member, Afliccion.PULSO_DEBIL) ? PULSO_DEBIL_HALF_HEARTS : 0);
    }

    /**
     * The per-tick half of Plomo.
     *
     * <p>Sprinting is client-driven: the client decides it is sprinting and tells the server, so
     * there is no flag to switch off once. Clearing it on the run's existing player scan is what
     * makes it stick, and costs nothing for a party carrying no Plomo.</p>
     */
    public static void tick(DungeonRun run, ServerPlayer player) {
        if (player.isSprinting() && noSprint(run, player.getUUID())) {
            player.setSprinting(false);
        }
    }
}
