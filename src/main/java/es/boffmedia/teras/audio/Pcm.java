package es.boffmedia.teras.audio;

/**
 * Raw sample maths: whatever a decoder hands back, turned into what Simple Voice Chat accepts.
 *
 * <p>SVC plays <b>48&nbsp;kHz, mono, signed 16-bit</b> and nothing else, while an MP3 or WAV off
 * disk is usually 44.1&nbsp;kHz stereo. Handing it the wrong rate does not fail — it plays at the
 * wrong speed and pitch, which is why this is its own tested layer rather than a helper buried in
 * the decoder.</p>
 *
 * <p>Pure: no files, no API, no state. Everything here is exercised by {@code PcmTest}.</p>
 */
public final class Pcm {
    private Pcm() {}

    /** What SVC wants. */
    public static final int SAMPLE_RATE = 48_000;

    /**
     * Folds interleaved {@code channels}-channel audio down to mono by averaging each frame.
     *
     * <p>Averaged, not summed: summing two correlated channels clips a track that was already
     * mastered near full scale, and clipping is far more audible than the 3&nbsp;dB drop.</p>
     *
     * @return {@code samples} itself when it is already mono — callers must not mutate the result
     */
    public static short[] toMono(short[] samples, int channels) {
        if (channels <= 1) {
            return samples;
        }
        int frames = samples.length / channels;
        short[] mono = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            int base = frame * channels;
            for (int channel = 0; channel < channels; channel++) {
                sum += samples[base + channel];
            }
            mono[frame] = (short) (sum / channels);
        }
        return mono;
    }

    /**
     * Resamples mono {@code samples} from {@code fromRate} to {@link #SAMPLE_RATE}.
     *
     * <p>Linear interpolation. Not the best resampler available, but the ratios in play are small
     * (44.1→48 is 1.088) and the alternative — dropping or repeating whole samples — is audible as
     * a rasp on sustained notes. A track already at 48&nbsp;kHz is returned untouched, which is the
     * common case for anything converted deliberately.</p>
     *
     * @throws IllegalArgumentException if {@code fromRate} is not positive
     */
    public static short[] resample(short[] samples, int fromRate) {
        if (fromRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive, was " + fromRate);
        }
        if (fromRate == SAMPLE_RATE || samples.length == 0) {
            return samples;
        }

        double ratio = (double) SAMPLE_RATE / fromRate;
        int length = (int) Math.floor(samples.length * ratio);
        short[] out = new short[Math.max(length, 1)];
        for (int i = 0; i < out.length; i++) {
            double source = i / ratio;
            int low = (int) Math.floor(source);
            int high = Math.min(low + 1, samples.length - 1);
            double fraction = source - low;
            out[i] = (short) Math.round(samples[low] * (1.0 - fraction) + samples[high] * fraction);
        }
        return out;
    }

    /** How long {@code samples} runs for at {@link #SAMPLE_RATE}, in seconds. */
    public static double seconds(short[] samples) {
        return (double) samples.length / SAMPLE_RATE;
    }
}
