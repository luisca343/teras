package es.boffmedia.teras.client.frame;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the live {@link FrameMedia} for every visible frame, keyed by position. All access is on the
 * client render/main thread (from {@link FrameRenderer} and the frame block entity's unload hooks),
 * so a plain map is enough.
 *
 * <p>Deliberately not {@code @OnlyIn(Dist.CLIENT)}: the frame block entity — common code that also
 * loads on dedicated servers — names this class in its unload hooks. Those calls are guarded by
 * {@code level.isClientSide}, so the class is only ever <em>loaded</em> on the client; keeping it a
 * plain class avoids any sided-verification surprise while the guards keep the server from touching
 * WaterMedia.</p>
 */
public final class FrameMediaManager {
    private FrameMediaManager() {}

    private static final Map<BlockPos, FrameMedia> ACTIVE = new HashMap<>();

    /**
     * The media for {@code pos} playing {@code url}, creating it on first use and rebuilding it when
     * the url changes. Returns {@code null} (and drops any existing player) for an empty url.
     */
    static FrameMedia getOrCreate(BlockPos pos, String url) {
        FrameMedia existing = ACTIVE.get(pos);
        if (existing != null && existing.rawUrl.equals(url)) {
            return existing;
        }
        if (existing != null) {
            existing.release();
            ACTIVE.remove(pos);
        }
        if (url == null || url.isEmpty()) {
            return null;
        }
        FrameMedia created = new FrameMedia(url);
        ACTIVE.put(pos.immutable(), created);
        return created;
    }

    /** The live media at {@code pos} without creating it — for the editor's playback controls. */
    static FrameMedia peek(BlockPos pos) {
        return ACTIVE.get(pos);
    }

    /** Releases and forgets the media at {@code pos} (frame broken or chunk unloaded). */
    public static void release(BlockPos pos) {
        FrameMedia removed = ACTIVE.remove(pos);
        if (removed != null) {
            removed.release();
        }
    }

    /** Releases every live frame (e.g. leaving a world). */
    public static void releaseAll() {
        for (FrameMedia media : ACTIVE.values()) {
            media.release();
        }
        ACTIVE.clear();
    }
}
