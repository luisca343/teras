package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.combat.Stat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * The sheet, down the left edge — one icon and one number per stat.
 *
 * <h2>Why icons and not labels</h2>
 *
 * <p>A text label ("Daño", "Cadencia") costs forty pixels of width and is in one language. An icon
 * costs eight and is readable at a glance once learned — which an always-on panel is what teaches,
 * because the number moving beside the icon is the teaching moment. The left edge is a glance, not a
 * read; the minimap and the purse own the right.</p>
 *
 * <h2>Why it is always up rather than behind a key</h2>
 *
 * <p>Behind a key the only people who would open it are the ones who already know what to look for,
 * which is backwards. The number moving as you absorb a reliquia only teaches if it was on screen
 * before you picked the thing up.</p>
 *
 * <h2>Why it is drawn small</h2>
 *
 * <p>Twelve rows at eighteen pixels came to 260 and did not fit a 720p window at GUI scale 4 — it
 * simply ran off the bottom, because the only guard was a {@code max} against the <i>top</i> margin.
 * Icons are blitted at half their sheet size into ten-pixel rows, which halves the column, and
 * {@link #rowHeight} gives up the group gaps and then tightens the rows themselves when even that
 * does not fit. A panel that overflows is worse than a cramped one: the stats that fall off the bottom
 * are the ones nobody knows are missing.</p>
 */
public final class CombatStatsOverlay {
    private CombatStatsOverlay() {}

    /** One 16x16 cell per displayed stat, in {@link #DISPLAY} order. */
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "textures/gui/dungeon_stat_icons.png");

    private static final int SHEET_CELL = 16;
    private static final int SHEET_HEIGHT = 16;

    /** Drawn size of an icon. Half the sheet, which is a legible glyph beside 8px text. */
    private static final int ICON = 8;

    private static final int MARGIN = 4;
    private static final int ICON_GAP = 3;
    private static final int ROW_HEIGHT = 10;
    /** Even, so the separator sits exactly between two rows rather than one pixel into either. */
    private static final int GROUP_GAP = 4;
    private static final int PAD = 2;

    private static final int BACKDROP = 0x90000000;
    private static final int SEPARATOR = 0x40FFFFFF;

    private static final int ATAQUE = 0xFFFF8A6B;
    private static final int DEFENSA = 0xFF8ABEFF;
    private static final int UTILIDAD = 0xFFFFE08A;

    /**
     * The stats shown, in sheet order — six ataque, three defensa, three utilidad.
     *
     * <p>{@code aplomo} is in {@link Stat} and not here: poise is already drawn where it matters, on
     * the enemy that breaks. Twelve is also exactly what the icon sheet holds.</p>
     */
    private static final Stat[] DISPLAY = {
            Stat.DANO, Stat.CADENCIA, Stat.CRITICO, Stat.CONTUNDENCIA,
            Stat.PENETRACION, Stat.ALCANCE,
            Stat.ARMADURA, Stat.CONTENEDORES, Stat.ESCUDO,
            Stat.VELOCIDAD, Stat.ENFRIAMIENTO, Stat.SUERTE,
    };

    /** Where each group starts in {@link #DISPLAY}; a separator is drawn before every one but the first. */
    private static final int[] GROUP_STARTS = {0, 6, 9};

    /** Registered as a GUI layer by {@link DungeonClientSetup}. */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientCombatStats.visible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        int available = graphics.guiHeight() - MARGIN * 2;
        int gap = GROUP_GAP * (GROUP_STARTS.length - 1);
        int row = rowHeight(available, gap);
        // Gaps are the first thing to go: a separator is a nicety, a row that fits is not.
        if (row * DISPLAY.length + gap > available) {
            gap = 0;
        }
        int panelHeight = row * DISPLAY.length + gap;

        int valueCol = ICON + ICON_GAP;
        int widest = 0;
        for (Stat stat : DISPLAY) {
            widest = Math.max(widest, mc.font.width(format(stat, ClientCombatStats.get(stat))));
        }
        int panelWidth = valueCol + widest;
        int top = Math.max(MARGIN, (graphics.guiHeight() - panelHeight) / 2);

        graphics.fill(0, top - PAD, MARGIN + panelWidth + PAD, top + panelHeight + PAD, BACKDROP);

        int y = top;
        for (int group = 0; group < GROUP_STARTS.length; group++) {
            if (group > 0 && gap > 0) {
                // GROUP_GAP, not gap: the latter is the total across every separator, so halving it
                // put the line 3px down into the first row of the next group instead of 1px above it.
                int line = y + GROUP_GAP / 2;
                graphics.fill(MARGIN, line, MARGIN + panelWidth, line + 1, SEPARATOR);
                y += GROUP_GAP;
            }
            int to = group + 1 < GROUP_STARTS.length ? GROUP_STARTS[group + 1] : DISPLAY.length;
            for (int i = GROUP_STARTS[group]; i < to; i++) {
                Stat stat = DISPLAY[i];
                int tint = tint(stat);
                // Text is 8 tall and the icon is too, so they share a baseline; the row's slack goes
                // below both rather than between them.
                graphics.blit(ICONS, MARGIN, y, ICON, ICON,
                        i * SHEET_CELL, 0, SHEET_CELL, SHEET_HEIGHT,
                        SHEET_CELL * DISPLAY.length, SHEET_HEIGHT);
                graphics.drawString(mc.font, format(stat, ClientCombatStats.get(stat)),
                        MARGIN + valueCol, y, tint, true);
                y += row;
            }
        }
    }

    /**
     * The tallest row that fits, never below the height of the text it has to hold.
     *
     * <p>Eight is the floor because that is a font line: tighter than that and rows overlap, which
     * looks like corruption rather than like a small panel.</p>
     */
    private static int rowHeight(int available, int gap) {
        int fits = (available - gap) / DISPLAY.length;
        return Math.max(ICON, Math.min(ROW_HEIGHT, fits));
    }

    /**
     * Counts read as counts and rates read as rates.
     *
     * <p>Contenedores at "6.0" would invite the question of what half a heart container is, and
     * crítico at "0.15" answers a question nobody asked — a chance is a percentage everywhere else a
     * player has ever seen one.</p>
     */
    private static String format(Stat stat, float value) {
        if (stat.integral()) {
            return String.valueOf(Math.round(value));
        }
        if (stat == Stat.CRITICO || stat == Stat.PENETRACION) {
            return Math.round(value * 100) + "%";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int tint(Stat stat) {
        return switch (stat) {
            case DANO, CADENCIA, CRITICO, CONTUNDENCIA, PENETRACION, ALCANCE -> ATAQUE;
            case ARMADURA, CONTENEDORES, ESCUDO -> DEFENSA;
            case VELOCIDAD, ENFRIAMIENTO, SUERTE -> UTILIDAD;
            // Not displayed; see DISPLAY.
            case APLOMO -> 0xFFFFFFFF;
        };
    }
}
