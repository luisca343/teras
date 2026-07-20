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
    /** Flying navigation. Nothing uses it yet; it exists so the field is not a boolean. */
    FLYER
}
