package es.boffmedia.teras.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Starting and stopping a track at a point in the world.
 *
 * <p>The guarded face of the audio system: this class names <b>no</b> Simple Voice Chat type, so a
 * Tocadiscos on a server without SVC loads, ticks and simply never makes a sound. Everything that
 * touches the SVC api lives in {@link VoicePlayback}, which is only resolved once the guard below
 * has passed — the same isolation the {@code *Bridge} classes use for Pixelmon and CustomNPCs.</p>
 */
public final class DiscPlayback {
    private DiscPlayback() {}

    /** True when playback is possible at all: SVC installed and its voice server running. */
    public static boolean available() {
        return ModList.get().isLoaded("voicechat") && VoicePlayback.hasApi();
    }

    /**
     * Plays {@code track} at {@code pos}, replacing whatever that position was already playing.
     *
     * <p>Returns immediately: the decode happens on a worker thread and playback begins when it is
     * done, which for an uncached track is a second or two of silence rather than a stalled tick.</p>
     *
     * @param loop restart on reaching the end, until something stops it
     * @return false if playback could not even be attempted (no SVC, no voice server)
     */
    public static boolean start(ServerLevel level, BlockPos pos, String track, float distance, boolean loop) {
        if (!available()) {
            return false;
        }
        VoicePlayback.start(level, pos, track, distance, loop);
        return true;
    }

    /** Stops whatever is playing at {@code pos}. Safe to call when nothing is. */
    public static void stop(ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("voicechat")) {
            return;
        }
        VoicePlayback.stop(level, pos);
    }

    /** True when {@code pos} currently has audio running. */
    public static boolean isPlaying(ServerLevel level, BlockPos pos) {
        return ModList.get().isLoaded("voicechat") && VoicePlayback.isPlaying(level, pos);
    }

    /** Stops every player. Called when the voice server stops and when the game server shuts down. */
    public static void stopAll() {
        if (!ModList.get().isLoaded("voicechat")) {
            return;
        }
        VoicePlayback.stopAll();
    }
}
