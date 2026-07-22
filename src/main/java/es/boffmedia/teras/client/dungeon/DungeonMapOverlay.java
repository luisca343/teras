package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.DungeonMapPayload;
import net.minecraft.resources.ResourceLocation;
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
    /** Fill for the room the player is in: the outline alone is easy to lose on a busy map. */
    private static final int COLOUR_CURRENT_FILL = 0xC04A6E9E;
    /** The seam drawn between two different rooms, so a 2×2 does not read as four 1×1s. */
    private static final int COLOUR_EDGE = 0x60000000;
    private static final int COLOUR_ICON = 0xFFFFD700;

    /**
     * The icon font written by {@code tools/author_map_icons.py}. Letters could not do this job:
     * a glyph has to read at nine pixels and be tintable, and the default font has no skull in it
     * — its symbol range is CP437, which stops at card suits.
     */
    private static final ResourceLocation ICON_FONT =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_icons");

    private static final char ICON_START = '\uE000';
    private static final char ICON_SKULL = '\uE001';
    private static final char ICON_SHOP = '\uE002';
    private static final char ICON_TREASURE = '\uE003';
    private static final char ICON_SECRET = '\uE004';
    private static final char ICON_SUPER_SECRET = '\uE005';
    private static final char ICON_CHALLENGE = '\uE006';
    private static final char ICON_CURSE = '\uE007';
    private static final char ICON_SACRIFICE = '\uE008';
    private static final char ICON_ARCADE = '\uE009';
    private static final char ICON_DEVIL = '\uE00A';

    /**
     * {@code RoomType} ordinal → icon. Order matches the server's {@code RoomType} enum — the
     * payload carries ordinals, so new room types are appended at both ends and never inserted.
     * The boss and the mini-boss share the skull and differ only by tint, which is the whole
     * reason the icons are drawn white.
     */
    private static final char[] TYPE_ICONS = {
            ' ', ICON_START, ICON_SKULL, ICON_SKULL, ICON_SHOP, ICON_TREASURE,
            ICON_SECRET, ICON_SUPER_SECRET, ICON_CHALLENGE, ICON_CURSE, ICON_SACRIFICE,
            ICON_ARCADE, ICON_DEVIL};

    /** {@code RoomType} ordinal → tint. */
    private static final int[] TYPE_COLOURS = {
            0xFFFFFFFF,   // NORMAL, never drawn
            0xFF8FD3FF,   // START — cold blue, the way back
            0xFFFF3B30,   // BOSS — red skull
            0xFFB0B0B0,   // MINI_BOSS — the same skull, grey
            0xFF5CE065,   // SHOP — green
            0xFFFFD700,   // TREASURE — gold
            0xFFDDDDDD,   // SECRET
            0xFFFF9CE8,   // SUPER_SECRET — wrong for the piso, wrong on the map too
            0xFFFF9E3D,   // CHALLENGE — orange
            0xFFB05CE0,   // CURSE — violet
            0xFFE05C5C,   // SACRIFICE — blood
            0xFF5CE0DC,   // ARCADE — cyan
            0xFFD03030};  // DEVIL_DEAL — crimson

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
        int purseBottom = drawPurse(graphics, mc, screenWidth);

        if (state.mapHidden()) {
            Component lost = Component.literal("§5Mapa perdido…");
            graphics.drawString(mc.font, lost,
                    screenWidth - MARGIN - mc.font.width(lost), purseBottom, 0xFFFFFF, true);
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
        int top = purseBottom;

        // Which room owns each cell. Every border below is "is my neighbour a different room?",
        // which is what makes a 2×1 or a 2×2 read as one chamber on sight — for every discovered
        // room, not only the one being stood in.
        java.util.Map<Long, Integer> rooms = new java.util.HashMap<>();
        for (DungeonMapPayload.Cell cell : state.cells()) {
            rooms.put(key(cell.x(), cell.y()), cell.room());
        }

        for (DungeonMapPayload.Cell cell : state.cells()) {
            int x = left + (cell.x() - minX) * pitch;
            int y = top + (cell.y() - minY) * pitch;
            int fill = cell.current() ? COLOUR_CURRENT_FILL : fillColour(cell);
            graphics.fill(x, y, x + CELL, y + CELL, fill);

            // An edge is drawn only where the neighbour belongs to another room, so the cells of
            // one chamber merge into a single shape and its outer wall is the only line.
            boolean openNorth = sameRoom(rooms, cell, 0, -1);
            boolean openSouth = sameRoom(rooms, cell, 0, 1);
            boolean openWest = sameRoom(rooms, cell, -1, 0);
            boolean openEast = sameRoom(rooms, cell, 1, 0);

            // Cells are drawn a pixel apart. Removing the border between two cells of one room is
            // not enough on its own — the gap still reads as a seam — so it is filled in too, and
            // the corner where four cells of the same room meet with it.
            if (openEast) {
                graphics.fill(x + CELL, y, x + CELL + GAP, y + CELL, fill);
            }
            if (openSouth) {
                graphics.fill(x, y + CELL, x + CELL, y + CELL + GAP, fill);
            }
            if (openEast && openSouth && sameRoom(rooms, cell, 1, 1)) {
                graphics.fill(x + CELL, y + CELL, x + CELL + GAP, y + CELL + GAP, fill);
            }
            // A border runs the full length of the side, including across the gap it shares with
            // the next cell of the same room — otherwise every side of a 2×2 has a one-pixel hole
            // in its middle where the two cells' borders stop short of each other.
            int edge = cell.current() ? COLOUR_CURRENT : COLOUR_EDGE;
            int spanX = x + CELL + (openEast ? GAP : 0);
            int spanY = y + CELL + (openSouth ? GAP : 0);
            if (!openNorth) {
                graphics.fill(x, y, spanX, y + 1, edge);
            }
            if (!openSouth) {
                graphics.fill(x, y + CELL - 1, spanX, y + CELL, edge);
            }
            if (!openWest) {
                graphics.fill(x, y, x + 1, spanY, edge);
            }
            if (!openEast) {
                graphics.fill(x + CELL - 1, y, x + CELL, spanY, edge);
            }

        }

        // Icons last, so nothing draws over them, and once per room rather than once per cell —
        // a chamber's icon belongs in the middle of the chamber.
        drawIcons(graphics, mc, state, left, top, pitch, minX, minY);
    }

    /**
     * One icon per labelled room, at the room's own centre.
     *
     * <p>A rectangle (1×1, 2×1, 2×2) takes the middle of its bounding box. An L cannot: the middle
     * of its bounding box is the quadrant it does not own, so it takes its <b>elbow</b> — the cell
     * joined to both of the others, which is the only cell of the three that touches the whole
     * room.</p>
     */
    private static void drawIcons(GuiGraphics graphics, Minecraft mc, DungeonMapPayload state,
                                  int left, int top, int pitch, int minX, int minY) {
        java.util.Map<Integer, java.util.List<DungeonMapPayload.Cell>> byRoom =
                new java.util.LinkedHashMap<>();
        for (DungeonMapPayload.Cell cell : state.cells()) {
            if (cell.room() == DungeonMapPayload.ROOM_NONE) {
                // An outline hint is its own room as far as the map knows; it carries its own icon.
                if (glyphFor(cell) != ' ') {
                    drawIcon(graphics, mc, cell, left + (cell.x() - minX) * pitch,
                            top + (cell.y() - minY) * pitch, CELL, CELL);
                }
                continue;
            }
            byRoom.computeIfAbsent(cell.room(), k -> new java.util.ArrayList<>()).add(cell);
        }

        for (java.util.List<DungeonMapPayload.Cell> cells : byRoom.values()) {
            DungeonMapPayload.Cell labelled = cells.stream()
                    .filter(c -> glyphFor(c) != ' ').findFirst().orElse(null);
            if (labelled == null) {
                continue;
            }
            int cellMinX = cells.stream().mapToInt(DungeonMapPayload.Cell::x).min().orElse(0);
            int cellMaxX = cells.stream().mapToInt(DungeonMapPayload.Cell::x).max().orElse(0);
            int cellMinY = cells.stream().mapToInt(DungeonMapPayload.Cell::y).min().orElse(0);
            int cellMaxY = cells.stream().mapToInt(DungeonMapPayload.Cell::y).max().orElse(0);
            int wide = cellMaxX - cellMinX + 1;
            int deep = cellMaxY - cellMinY + 1;

            int originX;
            int originY;
            int width;
            int height;
            if (cells.size() == wide * deep) {
                // A full rectangle: centre it over the whole footprint, gaps included.
                originX = left + (cellMinX - minX) * pitch;
                originY = top + (cellMinY - minY) * pitch;
                width = wide * pitch - GAP;
                height = deep * pitch - GAP;
            } else {
                DungeonMapPayload.Cell elbow = elbowOf(cells);
                originX = left + (elbow.x() - minX) * pitch;
                originY = top + (elbow.y() - minY) * pitch;
                width = CELL;
                height = CELL;
            }
            drawIcon(graphics, mc, labelled, originX, originY, width, height);
        }
    }

    /** The cell joined to both others in an L — the only one that touches the whole room. */
    private static DungeonMapPayload.Cell elbowOf(java.util.List<DungeonMapPayload.Cell> cells) {
        for (DungeonMapPayload.Cell cell : cells) {
            int neighbours = 0;
            for (DungeonMapPayload.Cell other : cells) {
                if (Math.abs(other.x() - cell.x()) + Math.abs(other.y() - cell.y()) == 1) {
                    neighbours++;
                }
            }
            if (neighbours >= 2) {
                return cell;
            }
        }
        return cells.get(0);
    }

    private static void drawIcon(GuiGraphics graphics, Minecraft mc, DungeonMapPayload.Cell cell,
                                 int x, int y, int width, int height) {
        char glyph = glyphFor(cell);
        if (glyph == ' ') {
            return;
        }
        Component icon = Component.literal(String.valueOf(glyph))
                .withStyle(style -> style.withFont(ICON_FONT));
        int drawn = mc.font.width(icon);
        graphics.drawString(mc.font, icon, x + (width - drawn) / 2,
                y + (height - mc.font.lineHeight) / 2 + 1, iconColour(cell), false);
    }

    /**
     * The party's shared purse, under the floor header. Returns the y the map starts at, so the
     * panel sits below whatever was drawn — the charge line only appears when there are charges.
     */
    private static int drawPurse(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        var wallet = ClientDungeonWallet.state();
        int y = MARGIN + 12;
        if (!wallet.active()) {
            return y;
        }
        Component coins = Component.literal("§e⛁ " + wallet.coins());
        graphics.drawString(mc.font, coins,
                screenWidth - MARGIN - mc.font.width(coins), y, 0xFFFFFF, true);
        y += 11;
        if (wallet.charges() > 0) {
            Component charges = Component.literal("§b✦ " + wallet.charges());
            graphics.drawString(mc.font, charges,
                    screenWidth - MARGIN - mc.font.width(charges), y, 0xFFFFFF, true);
            y += 11;
        }
        // What the party sold itself for coins. Listed rather than counted: "3 afflictions" tells
        // you nothing you can play around, and these are chosen drawbacks — you should be able to
        // see which ones you are living with at any moment, or the trade was not an informed one.
        for (String afliccion : wallet.afflictions()) {
            Component line = Component.literal("§d☠ " + afliccion);
            graphics.drawString(mc.font, line,
                    screenWidth - MARGIN - mc.font.width(line), y, 0xFFFFFF, true);
            y += 10;
        }
        return y;
    }

    /** Packs a cell coordinate into one key; grid coordinates are small and may be negative. */
    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /**
     * Whether the neighbour at {@code (dx,dz)} is part of the same room. Outline cells
     * ({@link DungeonMapPayload#ROOM_NONE}) never merge — several of them side by side are hints
     * about different rooms, not one wide chamber.
     */
    private static boolean sameRoom(java.util.Map<Long, Integer> rooms,
                                    DungeonMapPayload.Cell cell, int dx, int dy) {
        if (cell.room() == DungeonMapPayload.ROOM_NONE) {
            return false;
        }
        Integer neighbour = rooms.get(key(cell.x() + dx, cell.y() + dy));
        return neighbour != null && neighbour == cell.room();
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
        // Only the room's labelled cell draws: a 2×2 boss chamber sends four cells and one glyph.
        if (!cell.label() || type <= 0 || type >= TYPE_ICONS.length) {
            return ' ';
        }
        return TYPE_ICONS[type];
    }

    /** The icon's tint. Boss and mini-boss are one skull told apart by this alone. */
    private static int iconColour(DungeonMapPayload.Cell cell) {
        int type = cell.type();
        return type > 0 && type < TYPE_COLOURS.length ? TYPE_COLOURS[type] : COLOUR_ICON;
    }
}
