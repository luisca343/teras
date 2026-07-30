package es.boffmedia.teras.audio;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;
import es.boffmedia.teras.Teras;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A file on disk, turned into the 48&nbsp;kHz mono samples Simple Voice Chat plays.
 *
 * <p>MP3 goes through SVC's own decoder. That used to be the risk in this whole subsystem — 1.16.5
 * reached into {@code plugins.impl.mp3.Mp3DecoderImpl}, an internal — but 2.6.20 exposes a public
 * {@link Mp3Decoder} via {@code VoicechatApi.createMp3Decoder}, so there is no reflection and
 * nothing version-fragile left here. WAV goes through {@code javax.sound}, which is in the JDK.</p>
 *
 * <p>Blocking and allocation-heavy: a four minute track is ~23&nbsp;MB of {@code short[]}. Call it
 * off the server thread and let {@link TrackCache} keep the result.</p>
 */
public final class TrackDecoder {
    private TrackDecoder() {}

    /**
     * Decodes {@code file}, returning mono samples at {@link Pcm#SAMPLE_RATE}.
     *
     * @param api the SVC api, needed only for MP3 — may be {@code null} for a WAV
     * @throws IOException if the file cannot be read or is not a format we decode
     */
    public static short[] decode(Path file, VoicechatApi api) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return decodeMp3(file, api);
        }
        if (name.endsWith(".wav")) {
            return decodeWav(file);
        }
        throw new IOException("Unsupported audio format: " + name);
    }

    private static short[] decodeMp3(Path file, VoicechatApi api) throws IOException {
        if (api == null) {
            throw new IOException("MP3 decoding needs Simple Voice Chat, which is not running");
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Mp3Decoder decoder = api.createMp3Decoder(in);
            if (decoder == null) {
                throw new IOException("Simple Voice Chat could not open that MP3");
            }
            AudioFormat format = decoder.getAudioFormat();
            short[] samples = decoder.decode();
            if (samples == null || samples.length == 0) {
                throw new IOException("That MP3 decoded to nothing");
            }
            return conform(samples, format.getChannels(), (int) format.getSampleRate());
        }
    }

    private static short[] decodeWav(Path file) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            AudioFormat source = in.getFormat();
            // Decoded to plain signed 16-bit PCM first: a WAV may be 8-bit, 24-bit, unsigned or
            // A-law, and only the 16-bit signed case can be read straight as shorts. The rate and
            // channel count are deliberately left alone here — Pcm does those, tested.
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(),
                    source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                byte[] bytes = pcm.readAllBytes();
                if (bytes.length == 0) {
                    throw new IOException("That WAV is empty");
                }
                short[] samples = new short[bytes.length / 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                return conform(samples, target.getChannels(), (int) target.getSampleRate());
            }
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("That WAV is in a format Java cannot read: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // AudioSystem throws this, not UnsupportedAudioFileException, when no converter exists.
            throw new IOException("That WAV cannot be converted to 16-bit PCM: " + e.getMessage());
        }
    }

    private static short[] conform(short[] samples, int channels, int sampleRate) {
        short[] mono = Pcm.toMono(samples, channels);
        short[] resampled = Pcm.resample(mono, sampleRate);
        Teras.LOGGER.debug("Discos: decoded {} samples ({} ch @ {} Hz) into {} mono samples ({}s)",
                samples.length, channels, sampleRate, resampled.length, Pcm.seconds(resampled));
        return resampled;
    }
}
