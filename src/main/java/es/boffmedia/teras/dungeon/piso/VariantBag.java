package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Which variant a room draws — a shuffled bag rather than an independent roll per room.
 *
 * <h2>Why not just roll</h2>
 *
 * <p>Independent weighted rolls are memoryless, so with three equally-weighted rooms the same one
 * lands twice in a row about a third of the time and three times in a row about a ninth. On a floor
 * of a dozen rooms that is not a rare accident, it is the normal experience, and it reads as "the
 * generator only has one room" — the exact impression authoring several variants was meant to
 * dispel. Noted as a known flaw in DUNGEONS.md §11 while every key shipped a single template, and
 * unavoidable now that folders make many the normal case.</p>
 *
 * <p>A bag holds each variant in proportion to its weight, is shuffled once per cycle, and is dealt
 * from in order. Over a cycle every variant appears exactly its share of the time, and a repeat can
 * only happen across a cycle boundary.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>Stateless: the cycle's shuffle is derived from the layout seed and the cycle number, so the
 * same seed rebuilds the same floor down to each room's variant, and a room can be re-resolved at
 * any time without replaying the ones before it.</p>
 */
public final class VariantBag {
    private VariantBag() {}

    /** Salt, so bag shuffles never share a sequence with anything else derived from the seed. */
    private static final long SALT = 0x6261675FL;

    /** Copies of one variant in a single cycle. Beyond this a "rare" room is rare enough. */
    private static final int MAX_COPIES = 16;

    /** A cycle longer than this stops being a bag and starts being a roll again. */
    private static final int MAX_BAG = 64;

    /**
     * @param pool    the enabled variants, in a stable order
     * @param seed    the layout's base seed
     * @param keySalt something distinguishing the room key, so two keys do not deal in lockstep
     * @param ordinal this room's position among the rooms of its key, in placement order
     */
    public static RoomVariant draw(List<RoomVariant> pool, long seed, long keySalt, int ordinal) {
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() == 1) {
            return pool.get(0);
        }
        List<RoomVariant> bag = bag(pool);
        int cycle = Math.floorDiv(ordinal, bag.size());
        int position = Math.floorMod(ordinal, bag.size());
        SeededRng rng = new SeededRng(DungeonSeeds.derive(seed, SALT + keySalt * 31L + cycle));
        return rng.shuffled(bag).get(position);
    }

    /**
     * One cycle's worth of draws: each variant repeated in proportion to its weight.
     *
     * <p>Weights are relative, so the scale is set by the smallest of them — with the usual all-1.0
     * pool that yields one of each, which is the shuffle people expect. A rare room at 0.2 among
     * 1.0s yields five of each common room and one of it.</p>
     */
    static List<RoomVariant> bag(List<RoomVariant> pool) {
        double smallest = Double.MAX_VALUE;
        for (RoomVariant variant : pool) {
            smallest = Math.min(smallest, variant.weight());
        }
        int[] copies = new int[pool.size()];
        int total = 0;
        for (int i = 0; i < pool.size(); i++) {
            copies[i] = (int) Math.min(MAX_COPIES,
                    Math.max(1, Math.round(pool.get(i).weight() / smallest)));
            total += copies[i];
        }
        // Scaling down rather than truncating: dropping the tail of the bag would silently delete
        // whichever variants sorted last.
        if (total > MAX_BAG) {
            int divisor = (total + MAX_BAG - 1) / MAX_BAG;
            for (int i = 0; i < copies.length; i++) {
                copies[i] = Math.max(1, copies[i] / divisor);
            }
        }
        List<RoomVariant> bag = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            for (int c = 0; c < copies[i]; c++) {
                bag.add(pool.get(i));
            }
        }
        return bag;
    }
}
