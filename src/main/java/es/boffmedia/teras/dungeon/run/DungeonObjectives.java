package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every vanilla scoreboard objective the dungeon writes, created in one place and at one moment.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Objectives are created once, early, from one list. Every player's score in them is
 * {@linkplain #seed seeded} at login, after CustomNPCs has told the client they exist. Scores are
 * written whenever.</b> A missing objective is a warning and a no-op here, never a lazy creation:
 * the feature that wanted it degrades to "the condition reads as unset" and the server keeps
 * running.</p>
 *
 * <h2>What the seeding is for</h2>
 *
 * <p>CustomNPCs {@code 1.21.1.20251230} passes two {@code @Nullable} score fields through {@code
 * Optional.of}, so it throws on any conditioned objective whose scores were written plainly. What
 * actually keeps it from throwing is {@code CnpcScoreSyncMixin}, which fixes the call; seeding
 * merely makes these scores well-formed, and {@link #set} catches regardless, because Teras cannot
 * make another mod's listener correct but can refuse to be the frame holding its exception.</p>
 */
public final class DungeonObjectives {
    private DungeonObjectives() {}

    /** Warned once per objective, so a missing one does not fill the log from a tick loop. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Creates any of {@code names} the world does not already have. Call only from server start.
     *
     * <p>Each is attempted on its own and failures are swallowed with a line in the log: another
     * mod's listener throwing must not stop the rest from being created, and must never stop the
     * server from finishing its start.</p>
     */
    public static void ensure(MinecraftServer server, Collection<String> names) {
        Scoreboard scoreboard = server.getScoreboard();
        for (String name : names) {
            if (scoreboard.getObjective(name) != null) {
                continue;
            }
            try {
                scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                        ObjectiveCriteria.RenderType.INTEGER, false, null);
                Teras.LOGGER.info("Dungeons: created the '{}' scoreboard objective.", name);
            } catch (Throwable t) {
                Teras.LOGGER.error("Dungeons: could not create the '{}' scoreboard objective. The "
                        + "dialogue conditions that read it will behave as if it were unset; create "
                        + "it by hand with '/scoreboard objectives add {} dummy'.", name, name, t);
            }
        }
    }

    /**
     * Gives this player a score in each of {@code names} that carries a display name and a number
     * format, the two fields CustomNPCs' scoreboard code reads. <b>Call after CustomNPCs' own login
     * handler</b>, which is what tells the client the objective exists.
     *
     * <h2>The null that made the whole channel look unusable</h2>
     *
     * <p>CustomNPCs {@code 1.21.1.20251230} builds a {@code ClientboundSetScorePacket} in two
     * places — {@code ServerTickHandler.playerLogin} and the scoreboard dirty-listener it registers
     * at its own server start — and in both it writes</p>
     *
     * <pre>Optional.of(access.display()), Optional.of(info.numberFormat())</pre>
     *
     * <p>Both of those are {@code @Nullable} in vanilla and both are null on any score that was
     * written with a plain {@code set(int)}, which is every score anything writes. {@code
     * Optional.of} then throws {@code NullPointerException} — inside a player's login, so the
     * player is kicked with "Invalid player data" and the server is unjoinable.</p>
     *
     * <p>It only touches objectives named by some dialogue's <b>availability</b> ({@code
     * Availability.scores}), which is why no objective that nothing conditions on has ever shown
     * it, and why {@code teras_ascensor} broke the moment an availability pointed at it. Nothing
     * about that name or about when it is created was ever the problem: it was simply the first
     * objective this pack put behind a scoreboard condition.</p>
     *
     * <p>Seeding both fields removes the null and CustomNPCs' own code then works as written. The
     * values are the ones vanilla would use anyway — the player's name and {@link
     * StyledFormat#NO_STYLE} — so a sidebar showing one of these renders identically.</p>
     *
     * <h2>Why each name is caught separately</h2>
     *
     * <p>Setting the display fires the dirty-listener, which walks <i>every</i> conditioned
     * objective for <i>every</i> online player, so seeding the first name can throw on the second
     * one, which has not been seeded yet. That throw is harmless: vanilla assigns the field before
     * it notifies, so the seed has already landed and the next name can proceed. One pass leaves
     * every name seeded; the exceptions on the way there are noise.</p>
     */
    public static void seed(ServerPlayer player, Collection<String> names) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        Component display = Component.literal(player.getScoreboardName());
        for (String name : names) {
            Objective objective = scoreboard.getObjective(name);
            if (objective == null) {
                continue;
            }
            try {
                ScoreAccess access = scoreboard.getOrCreatePlayerScore(player, objective);
                access.numberFormatOverride(StyledFormat.NO_STYLE);
                access.display(display);
            } catch (Throwable t) {
                if (WARNED.add(name + "!seed")) {
                    Teras.LOGGER.debug("Dungeons: seeding '{}' threw inside another mod's "
                            + "scoreboard listener; the seed itself landed.", name, t);
                }
            }
        }
    }

    /**
     * Writes one score. Never creates the objective — see the class note.
     *
     * <h2>Why a scoreboard write is wrapped in a catch</h2>
     *
     * <p><b>Because another mod's listener can throw, and this runs inside a player's login.</b>
     * Every scoreboard mutation — a score changing, not only an objective being added — ends in
     * {@code ServerScoreboard.setDirty}, which runs every registered dirty-listener. CustomNPCs
     * registers one at its own server start that throws {@code NullPointerException} inside
     * {@code Optional.of} (build {@code 1.21.1.20251230}).</p>
     *
     * <p>Uncaught, that exception leaves {@code PlayerLoggedInEvent}, escapes
     * {@code PlayerList.placeNewPlayer}, and the player is kicked with <i>"Invalid player data"</i>
     * — <b>on every attempt, so the server becomes unjoinable</b>. Catching it costs a dialogue
     * condition that reads as unavailable; not catching it costs the server. There is no version of
     * this where a HUD number is worth a login.</p>
     *
     * @return whether it was written
     */
    public static boolean set(ServerPlayer player, String name, int value) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(name);
        if (objective == null) {
            if (WARNED.add(name)) {
                Teras.LOGGER.warn("Dungeons: the '{}' objective does not exist, so nothing can be "
                        + "written to it and any dialogue asking about it reads as unavailable. "
                        + "Restart the server, or create it with "
                        + "'/scoreboard objectives add {} dummy'.", name, name);
            }
            return false;
        }
        try {
            scoreboard.getOrCreatePlayerScore(player, objective).set(value);
            return true;
        } catch (Throwable t) {
            if (WARNED.add(name + "!write")) {
                Teras.LOGGER.error("Dungeons: writing '{}' threw inside another mod's scoreboard "
                        + "listener, so the value was not stored and every dialogue condition "
                        + "reading it will behave as unset. This is not a Teras fault and Teras "
                        + "cannot fix it — the listener belongs to whichever mod appears in the "
                        + "trace below.", name, t);
            }
            return false;
        }
    }
}
