package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomType;

/**
 * The chat/console floor printout, kept from the legacy command — one symbol per grid cell, legacy
 * glyphs. Also the canonical structural fingerprint the determinism tests compare.
 */
public final class LayoutAscii {

    private LayoutAscii() {}

    public static String render(DungeonLayout layout) {
        return render(layout.grid()) + "Seed: " + layout.seedString()
                + " (stage " + layout.stage() + ", attempt " + layout.attempt() + ")\n";
    }

    public static String render(RoomGrid grid) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                Room room = grid.roomAt(new GridPos(x, y));
                out.append(room == null ? '█' : symbol(room.type()));
                if (x < grid.size() - 1) {
                    out.append(' ');
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    public static char symbol(RoomType type) {
        return switch (type) {
            case NORMAL -> '□';
            case START -> 'S';
            case BOSS -> 'B';
            case MINI_BOSS -> 'M';
            case SHOP -> '$';
            case TREASURE -> 'T';
            case SECRET -> '?';
            case SUPER_SECRET -> 'X';
            case CHALLENGE -> 'C';
            case CURSE -> '!';
            case SACRIFICE -> '+';
            case ARCADE -> 'A';
            case DEVIL_DEAL -> 'D';
            case EXIT -> 'V';
            case ORDEN -> 'O';
        };
    }
}
