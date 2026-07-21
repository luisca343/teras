package es.boffmedia.teras.dungeon.entity;

/**
 * How an enemy gets around. Deliberately <b>not</b> a behaviour: behaviours are goals — what it
 * chooses to do — and navigation is the ground rules underneath them. Putting "climbs walls" in the
 * same list as "shoots webs" is how a vocabulary stops meaning anything.
 */
public enum Movement {
    /** Ordinary ground pathfinding. */
    GROUND,
    /**
     * Wall-climbing, as vanilla spiders use. In a 21×21×12 room this is what makes the ledges
     * contested: an archer perches there, and a climber follows it up.
     */
    CLIMBER,
    /**
     * Bounces; cannot walk. The distinction is the whole reason this is movement and not a
     * behaviour: a hop bolted on as a goal leaves the ordinary walk running underneath it, so the
     * enemy slides along the floor and occasionally jumps — which is exactly what a slime must not
     * do. Replacing the move control instead means there is no walking left to show through.
     */
    HOPPER,
    /** Flying navigation. */
    FLYER;

    /**
     * Whether the entity implements this mode. A mode nothing honours is worse than a missing one:
     * a variant declaring it looks configured and behaves as {@link #GROUND}, silently. Held by
     * {@code BestiaryAudit} so an unimplemented mode cannot reach a shipped variant.
     */
    public boolean isImplemented() {
        return true;
    }
}
