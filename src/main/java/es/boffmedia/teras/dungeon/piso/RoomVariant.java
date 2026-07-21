package es.boffmedia.teras.dungeon.piso;

/**
 * One authored template a room key may resolve to, as discovered on disk.
 *
 * <p>Variants are no longer declared anywhere: a room key is a <b>folder</b>, and every
 * {@code .nbt} in it is a peer. That is the whole point of {@link RoomPoolIndex} — there is exactly
 * one answer to "which rooms exist", and it is the folder listing. A piso may only adjust the odds
 * afterwards, never the membership, so the two can never disagree about what a room can be.</p>
 *
 * <p>Held as strings rather than {@code ResourceLocation} so the piso model stays free of Minecraft
 * and can be validated in tests; the build layer resolves them.</p>
 *
 * @param name     what an admin types and what {@code pesos} keys on — {@code repisa} for the
 *                 piso's own, {@code comun/altar} for one it inherits. The set prefix is part of
 *                 the identity, which is why a local {@code altar} and an inherited one are two
 *                 distinct entries rather than a shadowing rule to remember
 * @param template structure id, e.g. {@code teras:dungeon/cuevas/normal/repisa}
 * @param weight   relative draw weight; 1.0 unless {@code pesos} says otherwise, and 0 means the
 *                 piso has switched this one off
 */
public record RoomVariant(String name, String template, double weight) {

    public RoomVariant {
        weight = Math.max(0.0, weight);
    }

    /** Whether it can be drawn at all. A zero weight is how a piso declines an inherited room. */
    public boolean enabled() {
        return weight > 0;
    }

    /** The shared set it came from, or "" when it is the piso's own. */
    public String set() {
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(0, slash);
    }

    /** The file name without its set prefix. */
    public String file() {
        int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }
}
