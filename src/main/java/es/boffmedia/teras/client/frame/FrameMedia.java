package es.boffmedia.teras.client.frame;

import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.GFXEngine;
import org.watermedia.api.media.engines.SFXEngine;
import org.watermedia.api.media.players.MediaPlayer;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * One frame's live media: it resolves the source through WaterMedia's {@link MRL}, then owns the
 * {@link MediaPlayer} that decodes it and uploads frames to a GL texture. Everything here runs on
 * the render thread (driven from {@link FrameRenderer}); WaterMedia does the decode work on its own
 * threads and hands finished frames back via the render-thread executor.
 *
 * <p>The raw url is resolved per-viewer: {@code $(name)}/{@code $(uuid)} expand to this client's
 * player and {@code minecraft://} maps into the game directory, so one shared frame can show each
 * player their own content.</p>
 */
@OnlyIn(Dist.CLIENT)
final class FrameMedia {

    final String rawUrl;
    private MRL mrl;
    private MediaPlayer player;
    private Boolean lastLoop;
    private int lastVolume = -1;
    private long lastSyncTarget = Long.MIN_VALUE;
    /** After a load failure, don't retry until this wall-clock time — a bad URL must not be hammered. */
    private long retryAfterMs = 0L;

    /** Cooldown between load attempts after a failure. */
    private static final long RETRY_COOLDOWN_MS = 10_000L;
    /** Drift (ms) tolerated before re-seeking to the shared clock — avoids constant corrections. */
    private static final long SYNC_THRESHOLD_MS = 750L;

    FrameMedia(String rawUrl) {
        this.rawUrl = rawUrl;
    }

    /**
     * Advances this frame and returns its current GL texture id, or {@code 0} while it is still
     * fetching/decoding or between failed attempts. {@code volume} is the already-attenuated 0-100
     * gain for this viewer. Call only on the render thread.
     */
    long texture(boolean playing, boolean loop, int volume, long expectedMs) {
        if (player == null && !createPlayer(playing, loop)) {
            return 0L;
        }
        syncState(playing, loop, volume);
        syncClock(loop, expectedMs);
        return player.texture();
    }

    /**
     * Nudges this client's player toward the shared cinema clock: {@code expectedMs} is the unbounded
     * position the server's anchor implies, wrapped here with the local duration (only the client knows
     * it). Seeks only past a drift threshold, and not twice to the same target, so it corrects without
     * fighting normal playback.
     */
    private void syncClock(boolean loop, long expectedMs) {
        long duration = player.duration();
        if (duration <= 0L) {
            return; // image or unknown length — nothing to sync
        }
        long target = loop ? Math.floorMod(expectedMs, duration) : Math.min(expectedMs, duration);
        long actual = player.time();
        if (Math.abs(target - actual) > SYNC_THRESHOLD_MS && Math.abs(target - lastSyncTarget) > SYNC_THRESHOLD_MS) {
            player.seek(target);
            lastSyncTarget = target;
        }
    }

    int width() {
        return player == null ? 0 : player.width();
    }

    int height() {
        return player == null ? 0 : player.height();
    }

    /** A seekable video with a known length is live (images and still-loading sources report false). */
    boolean hasVideo() {
        return player != null && player.duration() > 0L;
    }

    long duration() {
        return player == null ? 0L : player.duration();
    }

    long time() {
        return player == null ? 0L : player.time();
    }

    private boolean createPlayer(boolean playing, boolean loop) {
        long now = System.currentTimeMillis();
        if (now < retryAfterMs) {
            return false; // cooling down after a failure
        }
        if (mrl == null) {
            String resolved = resolve(rawUrl);
            if (resolved == null || resolved.isEmpty()) {
                retryAfterMs = now + RETRY_COOLDOWN_MS;
                return false;
            }
            mrl = MediaAPI.getMRL(resolved);
        }
        MRL.Status status = mrl.status();
        if (status.failed()) {
            Throwable e = mrl.exception();
            Teras.LOGGER.warn("Frame media failed to load {}: {}", rawUrl, e == null ? status : e.toString());
            // Drop the MRL and back off; a transient failure (or a later-valid URL) self-heals.
            mrl = null;
            retryAfterMs = now + RETRY_COOLDOWN_MS;
            return false;
        }
        if (status == MRL.Status.EXPIRED) {
            mrl.reload(); // cache stale — regenerate, then pick it up once LOADED again
            return false;
        }
        if (!status.loaded()) {
            return false; // still fetching — try again next frame
        }
        // Capture the render thread here (we are on it) so the engine's deferred uploads target it,
        // even though the supplier WaterMedia calls may run slightly later.
        Thread renderThread = Thread.currentThread();
        Executor renderThreadEx = FrameGLEngine.renderThreadExecutor();
        Supplier<GFXEngine> gfx = () -> FrameGLEngine.create(renderThread, renderThreadEx);
        player = MediaAPI.createPlayer(mrl, gfx, audioSupplier());
        player.repeat(loop);
        lastLoop = loop;
        if (playing) {
            player.start();
        } else {
            player.startPaused();
        }
        return true;
    }

    /**
     * An OpenAL audio engine for sources that carry sound (never for a static image, which would
     * waste an AL source). Built eagerly and guarded: if OpenAL is unavailable the frame just plays
     * silently rather than failing the whole player.
     */
    private Supplier<SFXEngine> audioSupplier() {
        boolean hasSound = mrl.videoSource() != null || mrl.audioSource() != null;
        if (!hasSound) {
            return null;
        }
        ALEngine engine;
        try {
            engine = ALEngine.buildDefault();
        } catch (Throwable t) {
            Teras.LOGGER.warn("Frame audio engine unavailable for {}; playing silent: {}", rawUrl, t.toString());
            return null;
        }
        return () -> engine;
    }

    private void syncState(boolean playing, boolean loop, int volume) {
        if (lastLoop == null || lastLoop != loop) {
            player.repeat(loop);
            lastLoop = loop;
        }
        if (volume != lastVolume) {
            player.volume(volume);
            lastVolume = volume;
        }
        if (playing && player.paused()) {
            player.resume();
        } else if (!playing && player.playing()) {
            player.pause();
        }
    }

    void release() {
        if (player != null) {
            try {
                player.release();
            } catch (Throwable t) {
                Teras.LOGGER.warn("Error releasing frame media for {}", rawUrl, t);
            }
            player = null;
        }
        mrl = null;
    }

    /** Expands the Teras URL variables against the local player and game directory. */
    private static String resolve(String url) {
        if (url == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            url = url.replace("$(name)", mc.player.getName().getString())
                    .replace("$(uuid)", mc.player.getStringUUID());
        }
        if (url.startsWith("minecraft://")) {
            String gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().toString().replace("\\", "/");
            url = "file:///" + gameDir + "/" + url.substring("minecraft://".length());
        }
        return url;
    }
}
