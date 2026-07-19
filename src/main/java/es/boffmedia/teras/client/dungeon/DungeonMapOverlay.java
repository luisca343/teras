package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.net.DungeonMapPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The Isaac-style minimap, top-right: one square per grid cell, room-state colors, single-glyph
 * icons for special rooms, the player's cell highlighted. Bounds come from the cells actually
 * sent, so the panel hugs the discovered area instead of reserving a 13×13 block of screen.
 */
@OnlyIn(Dist.CLIENT)
public final class DungeonMapOverlay {
    private DungeonMapOverlay() {}

    private static final int MARGIN = 8;
    private static final int CELL = 9;
    private static final int GAP = 1;

    private static final int COLOUR_UNKNOWN = 0x50FFFFFF;
    private static final int COLOUR_DISCOVERED = 0xB0707070;
    private static final int COLOUR_COMBAT = 0xB0A03030;
    private static final int COLOUR_CLEARED = 0xB0404040;
    private static final int COLOUR_CURRENT = 0xFFFFFFFF;
    private static final int COLOUR_ICON = 0xFFFFD700;

    /**
     * {@code RoomType} ordinal → map glyph, the legacy ASCII symbols. Order must match the
     * server's {@code RoomType} enum — the payload carries ordinals.
     */
    private static final char[] TYPE_GLYPHS = {' ', 'S', 'B', 'M', '$', 'T', '?', 'X', 'C', '!'};

    /** State ordinals, matching the server's {@code RoomState}. */
    private static final int STATE_IN_COMBAT = 2;
    private static final int STATE_CLEARED = 3;

    /** Registered as a GUI layer by {@link DungeonClientSetup}. */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientDungeonMap.isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Layers added with registerAboveAll sit outside the manager's hideGui gate, so honour it here.
        if (mc.options.hideGui) {
            return;
        }
        DungeonMapPayload state = ClientDungeonMap.state();
        int screenWidth = graphics.guiWidth();

        Component header = Component.literal("Piso " + state.stage());
        graphics.drawString(mc.font, header,
                screenWidth - MARGIN - mc.font.width(header), MARGIN, 0xFFFFFF, true);

        if (state.mapHidden()) {
            Component lost = Component.literal("§5Mapa perdido…");
            graphics.drawString(mc.font, lost,
                    screenWidth - MARGIN - mc.font.width(lost), MARGIN + 12, 0xFFFFFF, true);
            return;
        }
        if (state.cells().isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (DungeonMapPayload.Cell cell : state.cells()) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }

        int pitch = CELL + GAP;
        int panelWidth = (maxX - minX + 1) * pitch;
        int left = screenWidth - MARGIN - panelWidth;
        int top = MARGIN + 12;

        for (DungeonMapPayload.Cell cell : state.cells()) {
            int x = left + (cell.x() - minX) * pitch;
            int y = top + (cell.y() - minY) * pitch;
            graphics.fill(x, y, x + CELL, y + CELL, fillColour(cell));

            boolean current = cell.x() == state.currentX() && cell.y() == state.currentY();
            if (current) {
                graphics.fill(x, y, x + CELL, y + 1, COLOUR_CURRENT);
                graphics.fill(x, y + CELL - 1, x + CELL, y + CELL, COLOUR_CURRENT);
                graphics.fill(x, y, x + 1, y + CELL, COLOUR_CURRENT);
                graphics.fill(x + CELL - 1, y, x + CELL, y + CELL, COLOUR_CURRENT);
            }

            char glyph = glyphFor(cell);
            if (glyph != ' ') {
                graphics.drawString(mc.font, String.valueOf(glyph),
                        x + (CELL - mc.font.width(String.valueOf(glyph))) / 2, y + 1,
                        COLOUR_ICON, false);
            }
        }
    }

    private static int fillColour(DungeonMapPayload.Cell cell) {
        if (cell.type() == DungeonMapPayload.TYPE_UNKNOWN) {
            return COLOUR_UNKNOWN;
        }
        if (cell.state() == STATE_IN_COMBAT) {
            return COLOUR_COMBAT;
        }
        if (cell.state() == STATE_CLEARED) {
            return COLOUR_CLEARED;
        }
        return COLOUR_DISCOVERED;
    }

    private static char glyphFor(DungeonMapPayload.Cell cell) {
        int type = cell.type();
        if (type <= 0 || type >= TYPE_GLYPHS.length) {
            return ' ';
        }
        return TYPE_GLYPHS[type];
    }
}
