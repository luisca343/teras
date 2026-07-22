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
     *
     * <p>It takes <b>two</b> halves, and having only one is indistinguishable from having both
     * until you watch it: a {@code WallClimberNavigation}, which decides the mob wants to be up
     * there and drives it into the wall, and an {@code onClimbable()} that returns true, which is
     * what {@code LivingEntity} reads before forcing the mob upward. For several releases this had
     * the first and not the second, so every spider walked into the wall, played its climb loop —
     * the animation flag described the attempt, not the result — and stayed on the floor.</p>
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
     *
     * <p>"Implemented" means the mob <b>moves</b> that way, not that something in the code mentions
     * the mode. {@link #CLIMBER} returned true here throughout the release in which climbing did not
     * work at all: the navigation existed, so the declaration looked honest, and the half that
     * actually lifts the mob off the floor was missing. Adding a mode is not finished when this
     * returns true — it is finished when someone has watched the mob do it.</p>
     */
    public boolean isImplemented() {
        return true;
    }
}
