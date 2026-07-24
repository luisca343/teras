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
     *       {@code reina_madre}. A {@code pisos/cuevas_infestadas.json} written before this names a
     *       boss that no longer exists, which cost a floor its boss until
     *       {@code GeoEnemyVariant.current} started healing renamed ids at the spawn boundary — a
     *       version bump only reports staleness, and reporting is not a fix for content that is
     *       already broken. {@code lepisma_cueva} moved onto its own silverfish rig, and the
     *       arachnids onto per-build ones</li>
     *   <li>the cave bestiary stops being one body (§49): {@code golem_geoda} joins Cuevas' roster
     *       as an elite and {@code cazadora} becomes Infestadas' declared mini-boss. Both are
     *       additions to a piso's tables, so an older file simply never fields them — no fallback,
     *       nothing in a log, just two enemies that exist in the jar and never appear. Unlike the
     *       rename above there is no boundary that can heal this: an absent line carries no id to
     *       correct, which is why the promote-your-own-elite rule in {@code FloorSelector} exists
     *       for the mini-boss half and why this one is worth a resync</li>
     *   <li>Cuevas becomes a floor-1 floor (§50): the bone-crypt elites leave its roster for the
     *       tramo-2 pool they were always part of, it declares {@code gran_limo} and
     *       {@code golem_geoda} as its own set-pieces, and the tramo's own default pools stop being
     *       crypt wardens. An existing {@code pisos/cuevas.json} keeps the old roster <b>and</b> the
     *       old empty pools, so it still fields husks and still inherits whatever the tramo says —
     *       which is now a boss it has never met</li>
     *   <li>the cave gains eight inhabitants and an ambient one (§51): {@code mastin},
     *       {@code vigia}, {@code carronero}, {@code escarabajo}, {@code cristal_rastrero},
     *       {@code hongo_bombardero} and {@code musgo_agarrador} join Cuevas' roster, and
     *       {@code murcielago} becomes its first {@code ambientales} entry. All of it is new lines
     *       in the piso's tables, so an older file fields none of them — and the ambient block in
     *       particular has never existed on any config written before this</li>
     *   <li>la sala del sello (§10.6 of the production draft; PISOS §59–60): every piso gains an
     *       optional {@code exit} room key — a 2×2 chamber appended <i>after</i> generation
     *       behind the boss, holding the pit, the seal glyph and the boss reward pedestal — and
     *       the shipped Cuevas/Infestadas sets ship one. Room pools are folder-discovered from the
     *       jar, so the new room appears on old worlds by itself; what an existing file misses is
     *       {@code config.yml}'s new knobs ({@code bloqueRunaSello},
     *       {@code bloqueRunaSelloEncendida}, {@code segundosDescenso}, two sound cues — all
     *       defaulted when absent) and a piso file's say over the new key's {@code pesos}. The
     *       {@code trapdoor} marker moved from the boss keys to {@code exit}; boss templates keep
     *       carrying one for the fallback carve on a piso without an exit template</li>
     *   <li>the allegiance arc goes live and Cuevas' roster is corrected (PISOS §63f–§65): the
     *       optional {@code orden} room key joins {@code exit} — la sala de la Orden, the grace
     *       chamber on the sello's far flank — and it ships in the <b>shared</b> {@code comun} set
     *       beside the pacto, so every piso that inherits the default draws it and folder discovery
     *       puts it on old worlds by itself. What an existing file misses is {@code config.yml}'s
     *       new keys ({@code lootOrden} and the three {@code trato} debt knobs — all defaulted when
     *       absent) and a piso file's say over the new key's {@code pesos}.
     *
     *       <p>The roster half is what actually needs a resync. {@code golem_geoda} leaves Cuevas'
     *       table and its mini-boss slot (retired game-wide, CONTENIDO §0 — still registered, still
     *       animated, spawning nowhere), {@code ballestero_gruta} and {@code cristalero} join it so
     *       the floor fields all three projectile shapes, and {@code cristalero_mayor} becomes the
     *       declared mini-boss. An older {@code pisos/cuevas.json} keeps the old table: it still
     *       fields the gólem, still names it as its mini-boss, and never sees any of the three —
     *       the same shape as the §51 entry below, and invisible for the same reason.</p></li>
     * </ol>
     */
    public static final int CURRENT = 11;

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
