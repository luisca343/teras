package es.boffmedia.teras.client.frame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

/**
 * A scrub bar for the frame editor: shows the local player's position and lets you drag to seek. The
 * seek is viewer-local (this client's playback only) — a synchronized cinema would need server tick
 * sync, which the frame does not yet do. Inert unless a seekable video with a known length is live.
 */
@OnlyIn(Dist.CLIENT)
class FrameTimeline extends AbstractWidget {

    private final Supplier<FrameMedia> media;

    FrameTimeline(int x, int y, int width, int height, Supplier<FrameMedia> media) {
        super(x, y, width, height, Component.translatable("gui.teras.frame_timeline"));
        this.media = media;
    }

    private FrameMedia video() {
        FrameMedia m = media.get();
        return (m != null && m.hasVideo()) ? m : null;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FrameMedia m = video();
        long duration = m == null ? 0L : m.duration();
        long time = m == null ? 0L : m.time();
        float frac = duration > 0L ? Mth.clamp((float) time / duration, 0.0F, 1.0F) : 0.0F;

        int trackY = getY() + getHeight() / 2 - 1;
        graphics.fill(getX(), trackY, getX() + getWidth(), trackY + 2, 0xFF555555);
        int fillW = Math.round(getWidth() * frac);
        graphics.fill(getX(), trackY, getX() + fillW, trackY + 2, 0xFFFFFFFF);
        int knobX = getX() + fillW;
        graphics.fill(knobX - 1, getY(), knobX + 1, getY() + getHeight(), 0xFFFFFFFF);

        var font = Minecraft.getInstance().font;
        String label = m == null ? "--:-- / --:--" : format(time) + " / " + format(duration);
        graphics.drawString(font, label, getX(), getY() - 10, 0xA0A0A0);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        seek(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        seek(mouseX);
    }

    private void seek(double mouseX) {
        FrameMedia m = video();
        if (m == null) {
            return;
        }
        float frac = (float) Mth.clamp((mouseX - getX()) / getWidth(), 0.0, 1.0);
        m.seekTo((long) (frac * m.duration()));
    }

    private static String format(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
