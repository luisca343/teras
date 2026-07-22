package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;

/**
 * The version stamp every dungeon config file carries, and the one rule about it: a file written by
 * an older version of the mod is <b>said out loud at boot</b>.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A shipped default only ever seeds a file that does not exist. Every content change since a
 * server's first boot therefore sits in the jar unread, and the failure is always silent — the
 * server keeps working, with the old content, and nothing anywhere connects the two. It has cost
 * real time four times:</p>
 *
 * <ul>
 *   <li>§22 — {@code alturaSala} stayed 8 while templates went to 12, so every floor built with a
 *       four-block gap and a ceiling at the wrong height.</li>
 *   <li>§23 — {@code formas} changed vocabulary and old files silently parsed to {@code SINGLE}
 *       alone, quietly producing a much poorer floor that still validated.</li>
 *   <li>§26 — the ambient bat stayed in tables it had been removed from.</li>
 *   <li>§33 — pisos written before per-piso enemy tables had no {@code enemigos} block, so the
 *       whole first-party bestiary was built, shipped, and <b>never once spawned</b>.</li>
 * </ul>
 *
 * <p>Each was found by someone noticing gameplay was wrong and digging. A stamp turns the next one
 * into a line in the log at startup, which is the entire point: the mod knows its own content
 * changed, and it is the only party that does.</p>
 *
 * <p>It deliberately does <b>not</b> migrate anything. Rewriting a file the operator may have
 * edited is how you lose their work; {@code piso resync} already exists for the pisos and is
 * explicit about what it preserves. This only tells the truth about what is stale.</p>
 */
public final class ConfigVersion {
    private ConfigVersion() {}

    /**
     * Bumped whenever the shipped content of any dungeon config changes in a way an existing file
     * will not pick up. Not the mod version — this moves only when a default does, so a server that
     * upgrades between two releases that changed nothing hears nothing.
     *
     * <p>History, so a bump is never guessed at:</p>
     * <ol>
     *   <li>the config layout as it stood before stamping (implied by a missing field)</li>
     *   <li>per-piso {@code enemigos} and {@code decoracion} tables (§25), the first-party cave
     *       bestiary (§30), and the geo swarm and slimes replacing the clone chaff (§33)</li>
     *   <li>folder-based room pools (§35): {@code salas} is retired in favour of one folder per
     *       room key, with {@code hereda} for shared sets and {@code pesos} for odds</li>
     *   <li>Infestadas declares all four {@code formas} and accepts LABYRINTH (§36), with
     *       {@code pesosFormas} keeping its big shapes rare instead of impossible</li>
     *   <li>{@code mecanica} accepts {@code {id, params}} (§37) so a mechanic's tuning stops being
     *       compiled, and secret rooms roll loot at a marker that had never been read</li>
     *   <li>the infestation's rigs and its boss id (§38): {@code reina_cria} is now
     *       {@code reina_madre}, so a {@code pisos/cuevas_infestadas.json} written before this
     *       names a boss that no longer exists and the floor draws <b>no boss at all</b> — the
     *       trapdoor is behind a fight that never spawns. {@code lepisma_cueva} moved onto its own
     *       silverfish rig, and the arachnids onto per-build ones</li>
     * </ol>
     */
    public static final int CURRENT = 6;

    /** The key every config writes it under. */
    public static final String KEY = "version";

    /**
     * Reports a file older than the shipped content. {@code found} is 0 or less when the file
     * predates stamping entirely, which is treated as the oldest possible version rather than as an
     * error — those files are exactly the ones most likely to be stale.
     *
     * @param file  the config's name, as an operator would find it on disk
     * @param fix   what to type to bring it up to date
     * @return whether the file is behind
     */
    public static boolean warnIfStale(String file, int found, String fix) {
        if (found >= CURRENT) {
            return false;
        }
        Teras.LOGGER.warn("Dungeons: {} was written by an older version of Teras (config version {},"
                        + " current {}). Shipped defaults never overwrite a file that already"
                        + " exists, so its content is whatever it was when the file was created."
                        + " {}",
                file, Math.max(0, found), CURRENT, fix);
        return true;
    }
}
