package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every marker a template may carry, and what — if anything — is supposed to read it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A marker is authored in one place and consumed in another, and nothing connected the two. Both
 * secret templates carried a {@code loot} marker from the day they were written and no code path
 * ever read it, so buying a wall charge and breaking into a secret room paid nothing at all. It was
 * found while authoring a fifth secret, not while playing — nobody notices the absence of a reward
 * they have never seen. That is the third time content has been authored, shipped and never once
 * used (§25, §33, §37).</p>
 *
 * <h2>Why there are two kinds</h2>
 *
 * <p>The obvious check — "every marker must have a consumer" — is wrong, and would have deleted the
 * {@code door} marker. That one is placed so an <b>author</b> can see where the generator will cut
 * doorways and not build across them; rooms are always a whole number of cells, so the bands are
 * always in the same place and marking them is worth doing. Nothing reads it because nothing should.
 * A check that flags correct content teaches people to ignore the audit — the same reason the
 * CLIMBER+RANGED bestiary rule was deleted in §34.</p>
 *
 * <p>So a marker is either {@link Use#RUNTIME}, which something reads, or {@link Use#ANNOTATION},
 * which is a note to whoever is building the room. The invariant that falls out is sharper than the
 * one it replaces: <b>a room key may never be required to carry an annotation</b>, because
 * requiring a marker nothing reads is meaningless by construction.</p>
 *
 * <h2>What is enforced, and what is only written down</h2>
 *
 * <p>Honest about its own limits. {@link #lootSource} is <b>enforced</b>: it is the table the run
 * loop dispatches on, so a room type missing from it genuinely cannot roll loot, and the exhaustive
 * switch over {@link LootSource} means adding a source without a loot table is a compile error. The
 * rest of the vocabulary is a declaration — this class cannot prove that something, somewhere, calls
 * {@code markerPos(room, "boss")}. It makes the one surface that has failed three times checkable,
 * and does not pretend to be a universal "everything declared is read" checker, because there is no
 * such thing.</p>
 */
public final class MarkerContract {
    private MarkerContract() {}

    /** Whether anything is supposed to read a marker. */
    public enum Use {
        /** The run loop or the builder reads it. Missing consumer means broken content. */
        RUNTIME,
        /** A note to the person building the room. Having no consumer is the correct state. */
        ANNOTATION
    }

    /**
     * Which loot table a room type draws at its {@code loot} marker.
     *
     * <p>An enum rather than a config string so the mapping to a table is an exhaustive switch at
     * the one place that reads it: adding a source without giving it a table stops compiling.</p>
     */
    public enum LootSource { TESORO, SECRETA, SUPERSECRETA }

    private static final Map<String, Use> VOCABULARY = new LinkedHashMap<>();
    private static final Map<RoomType, LootSource> LOOT = new LinkedHashMap<>();

    static {
        runtime("spawn");          // EnemySpawner — where a wave stands up
        runtime("loot");           // RunEngine.roomDiscovered, via LOOT below
        runtime("boss");           // EnemySpawner — where the boss or mini-boss lands
        runtime("trapdoor");       // RunEngine — the pit down: in the exit room, or the boss-room
                                   // fallback carve when the piso has no exit template
        runtime("premio");         // RewardPedestals — the boss reward stand in the exit room
        runtime("shopslot");       // DungeonShop — one pedestal each
        runtime("challenge");      // RunEngine — the plate that starts the fight
        runtime("sacrifice");      // SacrificePlate
        runtime("arcade");         // ArcadeMachine
        runtime("deal");           // DevilDeal — the pedestal
        runtime("gracia");         // OrdenGift — the font la Orden stands at
        runtime("oferta");         // CurseMarket — one affliction offered for coins
        runtime("purga");          // CurseMarket — the stand that buys one back
        runtime("nido");           // Nests, when the piso runs the infestation
        runtime("ambiente");       // EnemySpawner, when the piso declares `ambientales`
        runtime("cofre");          // ChestPedestal — a priced reward, in any room that carries one
        runtime("inicio");         // BuiltDungeon.partySpawn — where the party lands
        runtime("decoracion");     // Decorator — filled from the piso's surface tables

        // Placed so an author can see where the generator will cut its doorways and keep clear of
        // them. Rooms are always a whole number of 21-block cells — a 1.5-cell room is refused at
        // load and skipped at build — so the bands never move, and a fixed mark means something.
        annotation("door");

        // An intended parkour route, recorded by whoever built it. ParCool ships in the pack, so a
        // ledge reachable only by wall-run is legitimate content — but nothing can prove a route
        // exists (the room audit models walking, which is the right floor to hold rooms to), so this
        // is a note that says "this was meant to be climbed" and stops the next author flattening it.
        annotation("parkour");

        loot(RoomType.TREASURE, LootSource.TESORO);
        loot(RoomType.SECRET, LootSource.SECRETA);
        loot(RoomType.SUPER_SECRET, LootSource.SUPERSECRETA);
    }

    private static void runtime(String marker) {
        VOCABULARY.put(marker, Use.RUNTIME);
    }

    private static void annotation(String marker) {
        VOCABULARY.put(marker, Use.ANNOTATION);
    }

    private static void loot(RoomType type, LootSource source) {
        LOOT.put(type, source);
    }

    /** Every marker kind an author may place. The one vocabulary — the editor derives its own. */
    public static Set<String> vocabulary() {
        return Set.copyOf(VOCABULARY.keySet());
    }

    /**
     * What is supposed to read {@code marker}, or null when it is not in the vocabulary. A marker
     * carries its qualifier after a colon ({@code decoracion:techo}, {@code shopslot:2}); the base
     * kind is what is declared.
     */
    public static Use useOf(String marker) {
        if (marker == null) {
            return null;
        }
        int colon = marker.indexOf(':');
        return VOCABULARY.get(colon < 0 ? marker : marker.substring(0, colon));
    }

    public static boolean isRuntime(String marker) {
        return useOf(marker) == Use.RUNTIME;
    }

    /**
     * The loot this room type rolls when it is discovered, or null for a room type that rolls none.
     *
     * <p>This is the fix for §37 made structural rather than remembered: the run loop dispatches on
     * this map, so a room type that is not in it cannot pay out, and one that is cannot be
     * forgotten.</p>
     */
    public static LootSource lootSource(RoomType type) {
        return LOOT.get(type);
    }

    /** Room types that pay out at a {@code loot} marker, for tests. */
    public static Set<RoomType> lootRooms() {
        return Set.copyOf(LOOT.keySet());
    }
}
