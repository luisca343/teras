package es.boffmedia.teras.client.frame;

import es.boffmedia.teras.blockentity.FrameBlockEntity;
import es.boffmedia.teras.net.FrameConfigPayload;
import es.boffmedia.teras.net.FramePlaybackPayload;
import es.boffmedia.teras.net.TerasNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame editor. <b>Save</b> commits the whole form as a {@link FrameConfigPayload} the server
 * validates and re-syncs; <b>Play/Pause/Stop</b> flip only the play state on the committed config for
 * quick control; the <b>timeline</b> scrubs this client's own playback (viewer-local).
 *
 * <p>Size + the 3×3 anchor decide the display rectangle: {@code min = frac·(1−size)} where {@code frac}
 * is 0/0.5/1 for the anchor, so a resize grows from the chosen corner/edge/centre. Everything the
 * renderer needs is still {@code min}/{@code max}; the anchor is stored only so the editor reopens on
 * the same cell.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FrameConfigScreen extends Screen {

    private record Label(int x, int y, Component text) {}

    private final FrameBlockEntity frame;
    private final List<Label> labels = new ArrayList<>();

    private EditBox urlBox;
    private EditBox widthBox;
    private EditBox heightBox;
    private EditBox opacityBox;
    private EditBox brightnessBox;
    private EditBox rotationBox;
    private EditBox renderDistBox;
    private EditBox volumeBox;
    private EditBox audioMinBox;
    private EditBox audioMaxBox;
    private Button playButton;

    private boolean loop;
    private boolean playing;
    private boolean bothSides;
    private boolean muted;
    private boolean lit;
    private boolean showFrame;
    private boolean flipX;
    private boolean flipY;
    private byte anchorH;
    private byte anchorV;

    public FrameConfigScreen(FrameBlockEntity frame) {
        super(Component.translatable("gui.teras.frame_config"));
        this.frame = frame;
        this.loop = frame.isLoop();
        this.playing = frame.isPlaying();
        this.bothSides = frame.isBothSides();
        this.muted = frame.isMuted();
        this.lit = frame.isLit();
        this.showFrame = frame.isShowFrame();
        this.flipX = frame.isFlipX();
        this.flipY = frame.isFlipY();
        this.anchorH = frame.getAnchorH();
        this.anchorV = frame.getAnchorV();
    }

    public static void open(FrameBlockEntity frame) {
        Minecraft.getInstance().setScreen(new FrameConfigScreen(frame));
    }

    @Override
    protected void init() {
        labels.clear();
        int colW = 150;
        int gap = 30;
        int fullW = colW * 2 + gap;
        int cx = this.width / 2;
        int leftX = cx - fullW / 2;
        int rightX = leftX + colW + gap;
        int top = this.height / 2 - 118;

        label(leftX, top - 10, "gui.teras.frame_url");
        urlBox = new EditBox(this.font, leftX, top, fullW, 18, Component.translatable("gui.teras.frame_url"));
        urlBox.setMaxLength(FrameConfigPayload.MAX_URL_LENGTH);
        urlBox.setValue(frame.getUrl());
        addRenderableWidget(urlBox);

        // --- Left column: playback + audio ---
        int ly = top + 30;
        playButton = Button.builder(playLabel(), b -> togglePlay()).bounds(leftX, ly, 72, 20).build();
        addRenderableWidget(playButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.teras.frame_stop"), b -> stop())
                .bounds(leftX + 78, ly, 72, 20).build());

        ly += 24;
        addRenderableWidget(toggle(leftX, ly, 72, this::loopLabel, () -> loop = !loop));
        addRenderableWidget(toggle(leftX + 78, ly, 72, this::mutedLabel, () -> muted = !muted));

        ly += 24;
        volumeBox = labeledBox(leftX, ly, colW, "gui.teras.frame_volume", Math.round(frame.getVolume() * 100.0F));
        ly += 24;
        audioMinBox = labeledBox(leftX, ly, colW, "gui.teras.frame_audio_min", frame.getMinAudioDistance());
        ly += 24;
        audioMaxBox = labeledBox(leftX, ly, colW, "gui.teras.frame_audio_max", frame.getMaxAudioDistance());

        ly += 26;
        label(leftX, ly - 10, "gui.teras.frame_timeline");
        addRenderableWidget(new FrameTimeline(leftX, ly, colW, 12,
                () -> FrameMediaManager.peek(frame.getBlockPos()),
                ms -> sendPlayback(FramePlaybackPayload.SEEK, ms)));

        // --- Right column: display ---
        int ry = top + 30;
        widthBox = twoBox(rightX, ry, colW, "gui.teras.frame_size", frame.getSizeX(), true);
        heightBox = twoBox(rightX, ry, colW, null, frame.getSizeY(), false);

        ry += 24;
        label(rightX, ry + 4, "gui.teras.frame_anchor");
        addRenderableWidget(new FrameAnchorGrid(rightX + colW - 54, ry, 54, anchorH, anchorV, (h, v) -> {
            anchorH = h;
            anchorV = v;
        }));

        ry += 60;
        opacityBox = twoBox(rightX, ry, colW, "gui.teras.frame_alpha", frame.getAlpha() * 100.0F, true);
        brightnessBox = twoBox(rightX, ry, colW, null, frame.getBrightness() * 100.0F, false);

        ry += 24;
        rotationBox = twoBox(rightX, ry, colW, "gui.teras.frame_rotation", frame.getRotation(), true);
        renderDistBox = twoBox(rightX, ry, colW, null, frame.getRenderDistance(), false);

        ry += 24;
        addRenderableWidget(toggle(rightX, ry, 72, this::flipXLabel, () -> flipX = !flipX));
        addRenderableWidget(toggle(rightX + 78, ry, 72, this::flipYLabel, () -> flipY = !flipY));

        ry += 24;
        addRenderableWidget(toggle(rightX, ry, 72, this::bothLabel, () -> bothSides = !bothSides));
        addRenderableWidget(toggle(rightX + 78, ry, 72, this::litLabel, () -> lit = !lit));

        ry += 24;
        addRenderableWidget(toggle(rightX, ry, colW, this::showFrameLabel, () -> showFrame = !showFrame));

        int bottom = Math.max(ly, ry) + 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.teras.frame_save"), b -> save())
                .bounds(cx - 102, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 2, bottom, 100, 20).build());
    }

    private void label(int x, int y, String key) {
        labels.add(new Label(x, y, Component.translatable(key)));
    }

    /** A number field with a left label spanning the column; the box is right-aligned. */
    private EditBox labeledBox(int x, int y, int colW, String labelKey, float value) {
        label(x, y + 5, labelKey);
        EditBox box = numBox(x + colW - 54, y, 54, value);
        addRenderableWidget(box);
        return box;
    }

    /** One of a left/right pair of number fields (label drawn only for the left one). */
    private EditBox twoBox(int x, int y, int colW, String labelKey, float value, boolean left) {
        if (labelKey != null) {
            label(x, y + 5, labelKey);
        }
        int bx = left ? x + colW - 108 : x + colW - 52;
        EditBox box = numBox(bx, y, 50, value);
        addRenderableWidget(box);
        return box;
    }

    private EditBox numBox(int x, int y, int w, float value) {
        EditBox box = new EditBox(this.font, x, y, w, 18, Component.empty());
        box.setValue(trim(value));
        return box;
    }

    private Button toggle(int x, int y, int w, java.util.function.Supplier<Component> label, Runnable flip) {
        return Button.builder(label.get(), b -> {
            flip.run();
            b.setMessage(label.get());
        }).bounds(x, y, w, 20).build();
    }

    private Component loopLabel() { return onOff("gui.teras.frame_loop", loop); }
    private Component mutedLabel() { return onOff("gui.teras.frame_muted", muted); }
    private Component flipXLabel() { return onOff("gui.teras.frame_flip_x", flipX); }
    private Component flipYLabel() { return onOff("gui.teras.frame_flip_y", flipY); }
    private Component bothLabel() { return onOff("gui.teras.frame_both", bothSides); }
    private Component litLabel() { return onOff("gui.teras.frame_lit", lit); }
    private Component showFrameLabel() { return onOff("gui.teras.frame_border", showFrame); }

    private Component playLabel() {
        return Component.translatable(playing ? "gui.teras.frame_playing" : "gui.teras.frame_paused");
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key).append(": ").append(
                Component.translatable(on ? "gui.teras.frame_on" : "gui.teras.frame_off"));
    }

    private void togglePlay() {
        playing = !playing;
        playButton.setMessage(playLabel());
        sendPlayback(playing ? FramePlaybackPayload.PLAY : FramePlaybackPayload.PAUSE, 0L);
    }

    private void stop() {
        playing = false;
        playButton.setMessage(playLabel());
        sendPlayback(FramePlaybackPayload.STOP, 0L);
    }

    /** A live, synced playback command (all viewers follow), separate from the config Save. */
    private void sendPlayback(byte action, long arg) {
        TerasNet.sendFramePlayback(new FramePlaybackPayload(frame.getBlockPos(), action, arg));
    }

    private void save() {
        float w = Math.max(0.0F, parse(widthBox.getValue(), frame.getSizeX()));
        float h = Math.max(0.0F, parse(heightBox.getValue(), frame.getSizeY()));
        // Grow the rectangle from the chosen anchor: LEFT/BOTTOM keep 0, RIGHT/TOP keep 1, CENTER splits.
        float fx = anchorFrac(anchorH);
        float fy = anchorFrac(anchorV);
        float minX = fx * (1.0F - w);
        float minY = fy * (1.0F - h);

        float alpha = clamp01(parse(opacityBox.getValue(), frame.getAlpha() * 100.0F) / 100.0F);
        float bright = clamp01(parse(brightnessBox.getValue(), frame.getBrightness() * 100.0F) / 100.0F);
        float rotation = parse(rotationBox.getValue(), frame.getRotation());
        int renderDist = Math.round(parse(renderDistBox.getValue(), frame.getRenderDistance()));
        float volume = clamp01(parse(volumeBox.getValue(), frame.getVolume() * 100.0F) / 100.0F);
        float audioMin = Math.max(0.0F, parse(audioMinBox.getValue(), frame.getMinAudioDistance()));
        float audioMax = Math.max(audioMin, parse(audioMaxBox.getValue(), frame.getMaxAudioDistance()));

        TerasNet.sendFrameConfig(new FrameConfigPayload(
                frame.getBlockPos(), urlBox.getValue().trim(),
                minX, minY, minX + w, minY + h,
                rotation, flipX, flipY, bothSides,
                bright, alpha, renderDist,
                volume, audioMin, audioMax,
                loop, playing, muted, lit, showFrame,
                anchorH, anchorV));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 138, 0xFFFFFF);
        for (Label l : labels) {
            graphics.drawString(this.font, l.text(), l.x(), l.y(), 0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float anchorFrac(byte anchor) {
        return anchor == FrameBlockEntity.ANCHOR_MIN ? 0.0F : (anchor == FrameBlockEntity.ANCHOR_MAX ? 1.0F : 0.5F);
    }

    private static float parse(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Float.toString(value);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
