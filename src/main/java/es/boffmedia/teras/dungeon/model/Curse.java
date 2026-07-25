package es.boffmedia.teras.dungeon.model;

/**
 * Run modifiers rolled per stage. The legacy generator took two booleans; a set keeps the list
 * extensible without touching every signature. When both floor-size curses are present, LABYRINTH
 * wins and LOST's room bonus is ignored, preserving the legacy else-if.
 */
public enum Curse {
    /** XL floor: room count ×1.8, capped. */
    LABYRINTH,
    /** +4 rooms; at runtime, the minimap is disabled. */
    LOST,
    /**
     * El plomo: the floor takes your parkour away.
     *
     * <p>Costs no rooms and changes no geometry — it takes the moveset. ParCool is in the pack, so
     * wall-run and cat-leap are how everybody moves by the time they are two floors in; a floor that
     * withdraws them is the only modifier in the game that makes a veteran play like a beginner,
     * which is more interesting than a bigger maze. Applied per player through ParCool's own
     * limitation commands, so Teras needs no dependency on it and a server without it simply plays
     * the floor unmodified (PISOS §69).</p>
     */
    PLOMO
}
