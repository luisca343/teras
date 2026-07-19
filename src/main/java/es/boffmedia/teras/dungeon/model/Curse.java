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
    LOST
}
