package es.boffmedia.teras.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.voice.TerasVoicechatPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Locational track playback over Simple Voice Chat.
 *
 * <p>Only ever reached through {@link DiscPlayback}, which checks SVC is installed first — this
 * class names SVC types freely.</p>
 *
 * <p>One {@link AudioPlayer} per world position, keyed by a UUID derived from the level and block,
 * so a position is always its own channel and restarting is idempotent. SVC's player takes the
 * whole track as one {@code short[]} and paces it out itself; looping is a fresh player started
 * from {@code setOnStopped}, since the api has no repeat flag.</p>
 */
final class VoicePlayback {
    private VoicePlayback() {}

    /** Blocks the music carries by default. Roughly a large room; SVC attenuates within it. */
    static final float DEFAULT_DISTANCE = 24.0f;

    /** SVC volume category, so players can turn music down without turning voices down. */
    private static final String CATEGORY = "teras_discos";

    /** Decoding is seconds of work; it must never touch the server thread. */
    private static final ExecutorService DECODERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "teras-disc-decoder");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();

    /**
     * Per-channel start counter. Bumped by every start and stop <b>of that channel only</b>: a
     * global counter would mean stopping one jukebox cancelled the in-flight decode of every other
     * one on the server.
     */
    private static final Map<UUID, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();

    /** A live player plus what is needed to loop it and to recognise a superseded callback. */
    private record Playing(AudioPlayer player, String track, float distance, boolean loop, long generation) {}

    static boolean hasApi() {
        return TerasVoicechatPlugin.api() != null;
    }

    static void start(ServerLevel level, BlockPos pos, String track, float distance, boolean loop) {
        UUID id = channelId(level, pos);
        stopChannel(id);
        long generation = generationOf(id).incrementAndGet();

        DECODERS.submit(() -> {
            VoicechatServerApi api = TerasVoicechatPlugin.api();
            if (api == null) {
                return;
            }
            short[] samples;
            try {
                samples = TrackCache.get(track, api);
            } catch (Exception e) {
                Teras.LOGGER.warn("Discos: cannot play '{}' at {}: {}", track, pos, e.getMessage());
                return;
            }
            // Back to the server thread: the channel is built from a ServerLevel, and SVC's api is
            // not safe to drive from a pool thread.
            level.getServer().execute(() ->
                    begin(api, level, pos, id, track, samples, distance, loop, generation));
        });
    }

    private static void begin(VoicechatServerApi api, ServerLevel level, BlockPos pos, UUID id,
                              String track, short[] samples, float distance, boolean loop, long generation) {
        // The world may have moved on while we decoded — the block broken, the disc ejected, a
        // different track started. Any of those bumped this channel's generation.
        if (generation != generationOf(id).get()) {
            return;
        }
        try {
            LocationalAudioChannel channel = api.createLocationalAudioChannel(
                    id, api.fromServerLevel(level),
                    api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (channel == null) {
                Teras.LOGGER.warn("Discos: SimpleVoiceChat refused a channel at {}", pos);
                return;
            }
            channel.setCategory(CATEGORY);
            channel.setDistance(distance);

            AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), samples);
            player.setOnStopped(() -> onStopped(level, pos, id, generation));
            PLAYING.put(id, new Playing(player, track, distance, loop, generation));
            player.startPlaying();
        } catch (Exception e) {
            Teras.LOGGER.error("Discos: failed to start '{}' at {}", track, pos, e);
        }
    }

    /** SVC calls this both when a track ends naturally and when we stop it ourselves. */
    private static void onStopped(ServerLevel level, BlockPos pos, UUID id, long generation) {
        Playing finished = PLAYING.get(id);
        if (finished == null || finished.generation() != generation) {
            return;
        }
        PLAYING.remove(id, finished);
        if (!finished.loop()) {
            return;
        }
        // Re-entered through start() rather than restarting the same player: SVC's players are
        // single-use, and going back through start() re-reads the cache, so a replaced track file
        // is picked up on the next lap.
        level.getServer().execute(() -> {
            if (level.isLoaded(pos)) {
                start(level, pos, finished.track(), finished.distance(), true);
            }
        });
    }

    static void stop(ServerLevel level, BlockPos pos) {
        UUID id = channelId(level, pos);
        generationOf(id).incrementAndGet();
        stopChannel(id);
    }

    static boolean isPlaying(ServerLevel level, BlockPos pos) {
        Playing playing = PLAYING.get(channelId(level, pos));
        return playing != null && playing.player().isPlaying();
    }

    static void stopAll() {
        for (UUID id : Map.copyOf(PLAYING).keySet()) {
            generationOf(id).incrementAndGet();
            stopChannel(id);
        }
    }

    private static void stopChannel(UUID id) {
        Playing playing = PLAYING.remove(id);
        if (playing == null) {
            return;
        }
        try {
            playing.player().stopPlaying();
        } catch (Exception e) {
            Teras.LOGGER.debug("Discos: error stopping a player: {}", e.getMessage());
        }
    }

    private static AtomicLong generationOf(UUID id) {
        return GENERATIONS.computeIfAbsent(id, key -> new AtomicLong());
    }

    /**
     * A stable id for a block in a world. Derived rather than random so a restart of the same
     * position reuses the same channel, and two positions can never collide.
     */
    private static UUID channelId(ServerLevel level, BlockPos pos) {
        ResourceLocation dimension = level.dimension().location();
        return UUID.nameUUIDFromBytes(
                ("teras-disco:" + dimension + ":" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }
}
