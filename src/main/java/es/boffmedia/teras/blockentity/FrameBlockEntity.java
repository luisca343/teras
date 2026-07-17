package es.boffmedia.teras.blockentity;

import es.boffmedia.teras.init.BlockEntityInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The state of one in-world media frame: the source {@code url} plus how it is displayed. The raw
 * url is kept verbatim — the client resolves {@code $(name)}/{@code $(uuid)}/{@code minecraft://}
 * variables per-player when it opens the media, so a shared frame shows each viewer their own name.
 *
 * <p>Server-authoritative: only the server mutates these fields, and every change re-syncs to
 * watching clients through the block-entity update packet (see {@link #getUpdatePacket()}), exactly
 * like {@link FunkoBlockEntity}. The client never writes here; its live {@code MediaPlayer} lives in
 * {@code FrameMediaManager}, keyed by position, so no WaterMedia type is referenced from this
 * common class (it must load on dedicated servers, which have no WaterMedia).</p>
 */
public class FrameBlockEntity extends BlockEntity {

    // Display rectangle on the facing face, in face-local units. Defaults cover the whole block face;
    // values outside [0,1] make the display span into neighbouring space (a bigger screen).
    public static final float DEFAULT_MIN = 0.0F;
    public static final float DEFAULT_MAX = 1.0F;
    public static final int DEFAULT_RENDER_DISTANCE = 96;

    private String url = "";
    private float minX = DEFAULT_MIN;
    private float minY = DEFAULT_MIN;
    private float maxX = DEFAULT_MAX;
    private float maxY = DEFAULT_MAX;
    private float rotation = 0.0F;
    private boolean flipX = false;
    private boolean flipY = false;
    // When true the display renders on both faces; otherwise it is single-sided (culled from behind).
    private boolean bothSides = false;
    private float brightness = 1.0F;
    private float alpha = 1.0F;
    private int renderDistance = DEFAULT_RENDER_DISTANCE;
    // Audio (used from Phase 5): volume 0..1, and the distance band over which it fades to silence.
    private float volume = 1.0F;
    private float minAudioDistance = 5.0F;
    private float maxAudioDistance = 20.0F;
    private boolean loop = true;
    private boolean playing = true;
    // Server-authoritative playback clock for the shared cinema: while playing, the media position is
    // anchorMediaMs + (currentGameTime - anchorGameTime)*50ms. Game time is shared by all clients, so
    // every viewer computes the same timestamp and seeks its local player to it. Loop-wrap happens
    // client-side (only the client knows the media duration). Re-anchored on play/pause/stop/seek/url.
    private long anchorGameTime = 0L;
    private long anchorMediaMs = 0L;
    private boolean muted = false;
    // Fullbright by default (a screen glows); off = modulated by the block's world light.
    private boolean lit = true;
    // Whether the bezel/screen backing is drawn — turn off for a borderless display.
    private boolean showFrame = true;
    // Anchor the display grows from when resized: 0=left/bottom, 1=centre, 2=right/top. Stored only so
    // the editor reopens on the right cell; the rectangle itself is min/max, which is what renders.
    private byte anchorH = ANCHOR_CENTER;
    private byte anchorV = ANCHOR_CENTER;

    public static final byte ANCHOR_MIN = 0;
    public static final byte ANCHOR_CENTER = 1;
    public static final byte ANCHOR_MAX = 2;

    public FrameBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityInit.FRAME.get(), pos, state);
    }

    public String getUrl() { return url; }
    public float getMinX() { return minX; }
    public float getMinY() { return minY; }
    public float getMaxX() { return maxX; }
    public float getMaxY() { return maxY; }
    public float getRotation() { return rotation; }
    public boolean isFlipX() { return flipX; }
    public boolean isFlipY() { return flipY; }
    public boolean isBothSides() { return bothSides; }
    public float getBrightness() { return brightness; }
    public float getAlpha() { return alpha; }
    public int getRenderDistance() { return renderDistance; }
    public float getVolume() { return volume; }
    public float getMinAudioDistance() { return minAudioDistance; }
    public float getMaxAudioDistance() { return maxAudioDistance; }
    public boolean isLoop() { return loop; }
    public boolean isPlaying() { return playing; }
    public boolean isMuted() { return muted; }
    public boolean isLit() { return lit; }
    public boolean isShowFrame() { return showFrame; }
    public byte getAnchorH() { return anchorH; }
    public byte getAnchorV() { return anchorV; }

    public float getSizeX() { return maxX - minX; }
    public float getSizeY() { return maxY - minY; }

    /**
     * The media position (ms, unbounded — the client loop-wraps with its own duration) that {@code
     * gameTime} implies from the current anchor. Shared game time makes every viewer agree.
     */
    public long currentMediaMs(long gameTime) {
        if (!playing) {
            return anchorMediaMs;
        }
        long elapsedTicks = Math.max(0L, gameTime - anchorGameTime);
        return anchorMediaMs + elapsedTicks * 50L;
    }

    // ---- Server-side playback controls (mutate anchors + play state, then re-sync) ----

    /** Resume from where it was frozen. */
    public void playbackPlay() {
        if (level == null || playing) return;
        anchorGameTime = level.getGameTime();
        playing = true;
        setChanged();
        syncToClients();
    }

    /** Freeze at the current position. */
    public void playbackPause() {
        if (level == null || !playing) return;
        long now = level.getGameTime();
        anchorMediaMs = currentMediaMs(now);
        anchorGameTime = now;
        playing = false;
        setChanged();
        syncToClients();
    }

    /** Stop and rewind to the start (still synced — everyone jumps to 0). */
    public void playbackStop() {
        if (level == null) return;
        anchorMediaMs = 0L;
        anchorGameTime = level.getGameTime();
        playing = false;
        setChanged();
        syncToClients();
    }

    /** Jump to {@code ms}, keeping the current play/pause state. */
    public void playbackSeek(long ms) {
        if (level == null) return;
        anchorMediaMs = Math.max(0L, ms);
        anchorGameTime = level.getGameTime();
        setChanged();
        syncToClients();
    }

    /**
     * Overwrites every configurable field and re-syncs. Called only server-side (from the config
     * payload handler). Ranges are clamped so a malformed packet cannot produce a NaN quad or an
     * unbounded render distance.
     */
    public void applyConfig(String url, float minX, float minY, float maxX, float maxY, float rotation,
                            boolean flipX, boolean flipY, boolean bothSides,
                            float brightness, float alpha, int renderDistance,
                            float volume, float minAudioDistance, float maxAudioDistance,
                            boolean loop, boolean playing, boolean muted, boolean lit, boolean showFrame,
                            byte anchorH, byte anchorV) {
        // Capture the playback state before overwriting it, so we can re-anchor the shared clock below.
        long gameTime = level != null ? level.getGameTime() : 0L;
        long posBefore = currentMediaMs(gameTime);
        String oldUrl = this.url;
        boolean wasPlaying = this.playing;

        this.url = url == null ? "" : url;
        // Clamp the display rectangle so a bad packet can't produce a NaN or absurdly large quad, and
        // keep max >= min on each axis.
        this.minX = clampCoord(minX);
        this.minY = clampCoord(minY);
        this.maxX = Math.max(this.minX, clampCoord(maxX));
        this.maxY = Math.max(this.minY, clampCoord(maxY));
        this.rotation = rotation;
        this.flipX = flipX;
        this.flipY = flipY;
        this.bothSides = bothSides;
        this.brightness = clamp01(brightness);
        this.alpha = clamp01(alpha);
        this.renderDistance = Math.max(16, Math.min(512, renderDistance));
        this.volume = clamp01(volume);
        this.minAudioDistance = Math.max(0, minAudioDistance);
        this.maxAudioDistance = Math.max(this.minAudioDistance, maxAudioDistance);
        this.loop = loop;
        this.playing = playing;
        this.muted = muted;
        this.lit = lit;
        this.showFrame = showFrame;
        this.anchorH = clampAnchor(anchorH);
        this.anchorV = clampAnchor(anchorV);

        // Re-anchor the shared clock: a new url restarts at 0; a play/pause flip resumes/freezes; a
        // pure visual edit leaves playback untouched so a running movie is not interrupted.
        if (!this.url.equals(oldUrl)) {
            this.anchorMediaMs = 0L;
            this.anchorGameTime = gameTime;
        } else if (this.playing != wasPlaying) {
            this.anchorGameTime = gameTime;
            if (!this.playing) {
                this.anchorMediaMs = posBefore;
            }
        }
        setChanged();
        syncToClients();
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static byte clampAnchor(byte a) {
        return (a < ANCHOR_MIN || a > ANCHOR_MAX) ? ANCHOR_CENTER : a;
    }

    /** Display-rectangle bound (blocks). Generous, but finite — a frame can span a wall, not a world. */
    public static final float MAX_COORD = 32.0F;

    private static float clampCoord(float v) {
        if (Float.isNaN(v)) {
            return 0.0F;
        }
        return v < -MAX_COORD ? -MAX_COORD : (v > MAX_COORD ? MAX_COORD : v);
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        url = tag.getString("url");
        minX = readFloat(tag, "minX", DEFAULT_MIN);
        minY = readFloat(tag, "minY", DEFAULT_MIN);
        maxX = readFloat(tag, "maxX", DEFAULT_MAX);
        maxY = readFloat(tag, "maxY", DEFAULT_MAX);
        rotation = readFloat(tag, "rotation", 0.0F);
        flipX = tag.getBoolean("flipX");
        flipY = tag.getBoolean("flipY");
        bothSides = tag.getBoolean("bothSides");
        brightness = readFloat(tag, "brightness", 1.0F);
        alpha = readFloat(tag, "alpha", 1.0F);
        renderDistance = tag.contains("renderDistance") ? tag.getInt("renderDistance") : DEFAULT_RENDER_DISTANCE;
        volume = readFloat(tag, "volume", 1.0F);
        minAudioDistance = readFloat(tag, "minAudioDistance", 5.0F);
        maxAudioDistance = readFloat(tag, "maxAudioDistance", 20.0F);
        loop = readBool(tag, "loop", true);
        playing = readBool(tag, "playing", true);
        anchorGameTime = tag.getLong("anchorGameTime");
        anchorMediaMs = tag.getLong("anchorMediaMs");
        muted = tag.getBoolean("muted");
        lit = readBool(tag, "lit", true);
        showFrame = readBool(tag, "showFrame", true);
        anchorH = tag.contains("anchorH") ? clampAnchor(tag.getByte("anchorH")) : ANCHOR_CENTER;
        anchorV = tag.contains("anchorV") ? clampAnchor(tag.getByte("anchorV")) : ANCHOR_CENTER;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("url", url);
        tag.putFloat("minX", minX);
        tag.putFloat("minY", minY);
        tag.putFloat("maxX", maxX);
        tag.putFloat("maxY", maxY);
        tag.putFloat("rotation", rotation);
        tag.putBoolean("flipX", flipX);
        tag.putBoolean("flipY", flipY);
        tag.putBoolean("bothSides", bothSides);
        tag.putFloat("brightness", brightness);
        tag.putFloat("alpha", alpha);
        tag.putInt("renderDistance", renderDistance);
        tag.putFloat("volume", volume);
        tag.putFloat("minAudioDistance", minAudioDistance);
        tag.putFloat("maxAudioDistance", maxAudioDistance);
        tag.putBoolean("loop", loop);
        tag.putBoolean("playing", playing);
        tag.putLong("anchorGameTime", anchorGameTime);
        tag.putLong("anchorMediaMs", anchorMediaMs);
        tag.putBoolean("muted", muted);
        tag.putBoolean("lit", lit);
        tag.putBoolean("showFrame", showFrame);
        tag.putByte("anchorH", anchorH);
        tag.putByte("anchorV", anchorV);
    }

    private static float readFloat(CompoundTag tag, String key, float fallback) {
        return tag.contains(key) ? tag.getFloat(key) : fallback;
    }

    private static boolean readBool(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client releases its live media when the frame leaves the world; server side is a no-op. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            es.boffmedia.teras.client.frame.FrameMediaManager.release(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null && this.level.isClientSide) {
            es.boffmedia.teras.client.frame.FrameMediaManager.release(worldPosition);
        }
    }
}
