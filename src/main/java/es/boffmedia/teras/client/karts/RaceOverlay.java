package es.boffmedia.teras.client.karts;

import es.boffmedia.teras.net.RaceHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The race HUD: position, lap, running time and the countdown.
 *
 * <p>Drawn as text rather than blitting the 1.16.5 position sprites
 * ({@code textures/posiciones/<n>.png}). Those only covered a handful of positions, could not show
 * a lap counter or a clock, and would have needed re-authoring for every field size; drawn text
 * scales to any grid and carries the rest of the state for free.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RaceOverlay {
    private RaceOverlay() {}

    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOUR_PRIMARY = 0xFFFFFF;
    private static final int COLOUR_MUTED = 0xB0B0B0;
    private static final int COLOUR_WARNING = 0xFF5555;
    private static final int COLOUR_COUNTDOWN = 0xFFD700;

    /** Registered as a GUI layer by {@link KartsClientSetup}. */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientRaceHud.isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Layers added with registerAboveAll sit outside the manager's hideGui gate, so honour it here.
        if (mc.options.hideGui) {
            return;
        }
        RaceHudPayload state = ClientRaceHud.state();

        drawCountdown(graphics, mc.font, state);
        drawPanel(graphics, mc.font, state);
    }

    /** The big central 3-2-1, mirroring the titles the server sends. */
    private static void drawCountdown(GuiGraphics graphics, Font font, RaceHudPayload state) {
        if (state.countdown() < 0) {
            return;
        }
        String text = state.countdown() == 0 ? "¡YA!" : String.valueOf(state.countdown());
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2.0, graphics.guiHeight() / 3.0, 0);
        graphics.pose().scale(4.0f, 4.0f, 1.0f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, COLOUR_COUNTDOWN, true);
        graphics.pose().popPose();
    }

    /** Position, lap and clock, bottom left — clear of the hotbar and the vehicle's own gauges. */
    private static void drawPanel(GuiGraphics graphics, Font font, RaceHudPayload state) {
        if (state.totalLaps() <= 0) {
            return;
        }
        int y = graphics.guiHeight() - MARGIN - LINE_HEIGHT * 3;

        if (state.position() > 0) {
            String position = ordinal(state.position())
                    + (state.totalRacers() > 0 ? " / " + state.totalRacers() : "");
            graphics.drawString(font, Component.literal(position), MARGIN, y, COLOUR_PRIMARY, true);
        }
        y += LINE_HEIGHT;

        graphics.drawString(font,
                Component.literal("Vuelta " + state.lap() + "/" + state.totalLaps()),
                MARGIN, y, COLOUR_PRIMARY, true);
        y += LINE_HEIGHT;

        StringBuilder clock = new StringBuilder(formatTime(state.elapsedMs()));
        if (state.bestLapMs() > 0) {
            clock.append("  (mejor ").append(formatTime(state.bestLapMs())).append(')');
        }
        graphics.drawString(font, Component.literal(clock.toString()), MARGIN, y, COLOUR_MUTED, true);

        if (state.wrongWay()) {
            String warning = "¡SENTIDO CONTRARIO!";
            graphics.drawString(font, Component.literal(warning),
                    (graphics.guiWidth() - font.width(warning)) / 2,
                    graphics.guiHeight() / 2 + 20, COLOUR_WARNING, true);
        }
    }

    private static String ordinal(int position) {
        return position + "º";
    }

    private static String formatTime(int millis) {
        int minutes = millis / 60_000;
        int seconds = (millis % 60_000) / 1000;
        int hundredths = (millis % 1000) / 10;
        return String.format("%d:%02d.%02d", minutes, seconds, hundredths);
    }
}
