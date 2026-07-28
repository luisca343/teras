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
     *   <li>the treasure room becomes a choice and the shop is reworked (PISOS §66): treasure now
     *       stands up <b>three</b> picks — arma, vitalidad, provisión, each player takes one — read
     *       from {@code loot:<archetype>} markers, and the shop rolls one <b>floor-wise</b> planogram
     *       (a guaranteed spread, not a raw roll), with a face-down gamble and an occasional
     *       discounted ganga. New shipped templates carry the three treasure stands and a
     *       north-wall shop counter (turned onto a doorless wall at build). Folder discovery puts the
     *       new {@code start}/{@code shop}/{@code treasure} variants on old worlds by themselves;
     *       what an existing {@code config.yml} misses is the new {@code loot.*} tables
     *       ({@code lootTesoroArma}, {@code lootTesoroVitalidad}, {@code lootCaja}), the
     *       {@code tesoroProvision*} numbers and the {@code tienda} deal/gamble knobs
     *       ({@code gangaProbabilidad}, {@code gangaDescuentoPct}, {@code pisoPremium}) — all
     *       defaulted when absent, so an old file plays correctly and only misses the tuning.</li>
     *   <li>floors become absolute (PISOS §67): a floor's cell budget is read from a table indexed
     *       by its <b>canonical</b> floor number rather than derived from the length of whatever
     *       dungeon is using it. An existing {@code config.yml} has no {@code generacion:} block, so
     *       it falls back to the compiled-in curve — which is the shipped one, so nothing is lost
     *       and only the tuning is out of reach. An existing {@code mazmorras.json} has no
     *       {@code primerPiso}, which defaults to 1: correct for a dungeon played from the top, and
     *       the only thing to add for a challenge that starts partway down.
     *
     *       <p>What genuinely changes on an old world: floor sizes. A two-floor mazmorra used to
     *       generate its floors as the sixth and twelfth of a twelve-floor descent (22 and 52
     *       cells) and now generates them as floors one and two (10-12 and 13-15). Recorded seeds
     *       no longer reproduce their floors either, because the seed folds in the canonical floor
     *       so that one floor is one layout however it was reached.</p></li>
     *   <li>chests and el plomo (PISOS §69). Chests are a new {@code cofre} marker any template may
     *       carry, so an old world gets them the moment it takes the new room templates — there is
     *       nothing to migrate, and a piso whose rooms carry none simply has no chests. What an
     *       existing {@code config.yml} misses is the {@code cofres:} block (loot tables and the
     *       spike/trap numbers) and the {@code parcool:} block, both defaulted when absent.
     *
     *       <p>{@code plomo} is a new curse, so an existing file's {@code maldiciones:} has no entry
     *       for it and it therefore <b>never occurs</b> — the fallback for an absent key is zero, not
     *       the shipped 6 %. That is the safe direction (a curse nobody asked for should not appear
     *       on an upgrade) but it does mean the curse is invisible until the key is added by hand.
     *       Shipped pisos accept it; a {@code pisos/*.json} on disk keeps whatever
     *       {@code maldiciones} list it already had.</p></li>
     *   <li>doors stop being holes (PISOS §70). A doorway is now framed — a ring in the wall plane
     *       and, with {@code puertas.relieve}, two jambs and a lintel standing one block proud of it
     *       — and wears the frame of the more special of the two rooms it joins. The opening itself
     *       did not move, so nothing authored needs re-authoring.
     *
     *       <p>An existing {@code config.yml} misses the whole {@code puertas:} block, which is
     *       defaulted when absent and therefore only costs the operator the ability to tune it. The
     *       one key that genuinely changes behaviour is {@code bloqueSello}: it ships as
     *       {@code teras:reja} now, and a file on disk keeps {@code minecraft:iron_bars}, so an
     *       upgraded server seals its rooms with the old bars until the key is changed by hand.</p>
     *
     *       <p>A piso gains an optional {@code puertas} block of its own — the frame an ordinary
     *       door wears on <i>that</i> floor. Absent, it falls back to {@code puertas.normal} in
     *       config.yml, which is Cuevas' andesite: correct for Cuevas, wrong for Infestadas, and
     *       worth a {@code piso resync} for anything else.</p></li>
     *   <li>{@code config.yml} starts being stamped at all. It was the one dungeon config that never
     *       wrote a version, so every default added to its template since a server's first boot had
     *       been invisible on it — silently, which is the exact failure this class exists to announce,
     *       and it had happened four times before anyone noticed the fifth. Nothing migrates: the
     *       template is still only written when the file is <b>absent</b>, so an existing
     *       {@code config.yml} reports as unstamped (the missing key reads as 0, which is older than
     *       current) and the warning tells the operator to copy the blocks it is missing or delete the
     *       file to regenerate it. A stamp reports staleness; it has never repaired content.</li>
     *   <li>the rebuilt combat loop (ROGUELIKE §4). An existing {@code config.yml} has no
     *       {@code combate:} block, so it takes the shipped default, which is <b>true</b> — the one
     *       place in this list where an absent key does not mean "keep the old behaviour". That is
     *       deliberate: the flag first shipped false out of caution and cost two playtests reporting
     *       "nothing is set up", because a shut gate and an unbuilt feature look identical from inside
     *       the game. What changes on an old world is therefore the whole of combat, on the first boot
     *       after the update: Teras computes damage for players and enemies both, the esquiva answers
     *       {@code V}, and light attacks chain into a stagger. Right-click is untouched. Set
     *       {@code combate.activado: false} to hand fights back to Minecraft. Diagnose in game with
     *       {@code /teras dungeon combate}, which reports each gate rather than failing silently.
     *
     *       <p>Gear gains first-party stat lines in the same pass ({@code critico},
     *       {@code penetracion}, {@code alcance}, {@code enfriamiento}, {@code aplomo}, {@code suerte},
     *       {@code codicia}, {@code escudo}, {@code contundencia}). An existing {@code gear.json} names
     *       none of them, so every piece on disk keeps exactly the stat line it had — which does mean a
     *       server that has customised its catalogue has no crit anywhere until it adds some.</p></li>
     *   <li><b>18</b> — {@code contenedoresPorMuerte: 2}. The dungeon gains a loss condition: a
     *       death costs two heart containers, a player out of containers is out of the expedition,
     *       and a party with nobody left loses the run — the bag and the ₽ conversion, never the
     *       gear. An existing file has no key, so it inherits the shipped 2 like any other default;
     *       set it to 0 to keep the old behaviour, in which a run could only take longer, never be
     *       lost. Stated because this changes what players are agreeing to when they enter, which
     *       no other config default in this file does.
     *
     *       <p>{@code codicia} is removed from the stat catalogue in the same pass — the purse is
     *       shared, so a personal pickup dial was a stat about somebody else's money. A
     *       {@code gear.json} naming it now warns at boot and the line is ignored.</p></li>
     * </ol>
     */
    public static final int CURRENT = 18;

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
