package es.boffmedia.teras.audio;

import de.maxhenkel.voicechat.api.VoicechatApi;
import es.boffmedia.teras.Teras;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decoded tracks, kept so a jukebox does not re-decode a four minute MP3 every time it loops.
 *
 * <p>Bounded by <b>total samples</b> rather than entry count: entries differ in size by two orders
 * of magnitude, so counting them would let a handful of long tracks hold hundreds of megabytes
 * while the limit read as "8 tracks". Eviction is least-recently-used.</p>
 *
 * <p>Every method is {@code synchronized} — decodes happen on worker threads and playback starts on
 * the server thread.</p>
 */
public final class TrackCache {
    private TrackCache() {}

    /**
     * ~40 minutes of audio, about 230 MB. Generous because the alternative to a cache hit is a
     * multi-second decode on a worker while a block sits silent.
     */
    private static final long MAX_SAMPLES = 48_000L * 60 * 40;

    private static final Map<String, short[]> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static long cachedSamples;

    /**
     * The samples for {@code name}, decoding it if this is the first ask.
     *
     * <p>Blocking on a miss. Never call from the server thread.</p>
     *
     * @throws IOException if the track is missing or will not decode
     */
    public static short[] get(String name, VoicechatApi api) throws IOException {
        String track = TrackName.normalize(name);
        if (track == null) {
            throw new IOException("'" + name + "' is not a usable track name");
        }
        synchronized (TrackCache.class) {
            short[] hit = CACHE.get(track);
            if (hit != null) {
                return hit;
            }
        }

        Path file = AudioLibrary.find(track);
        if (file == null) {
            throw new IOException("There is no track called '" + track + "'");
        }
        // Decoded outside the lock: two blocks starting the same unknown track would otherwise
        // serialise, and the cost of the rare duplicate decode is lower than holding the monitor
        // for seconds while every other caller waits.
        short[] samples = TrackDecoder.decode(file, api);

        synchronized (TrackCache.class) {
            short[] raced = CACHE.get(track);
            if (raced != null) {
                return raced;
            }
            CACHE.put(track, samples);
            cachedSamples += samples.length;
            evictWhileOver();
        }
        return samples;
    }

    public static synchronized void invalidate(String name) {
        short[] removed = CACHE.remove(name);
        if (removed != null) {
            cachedSamples -= removed.length;
        }
    }

    public static synchronized void clear() {
        CACHE.clear();
        cachedSamples = 0;
    }

    /** Access-ordered, so the first key is the least recently used. */
    private static void evictWhileOver() {
        while (cachedSamples > MAX_SAMPLES && CACHE.size() > 1) {
            Map.Entry<String, short[]> oldest = CACHE.entrySet().iterator().next();
            CACHE.remove(oldest.getKey());
            cachedSamples -= oldest.getValue().length;
            Teras.LOGGER.debug("Discos: evicted '{}' from the track cache", oldest.getKey());
        }
    }
}
