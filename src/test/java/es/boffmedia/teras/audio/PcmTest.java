package es.boffmedia.teras.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sample maths. Failures here are not crashes — they are a track that plays at the wrong speed, or
 * one that clips, which is exactly the kind of bug nobody finds by reading the code.
 */
class PcmTest {

    @Test
    @DisplayName("stereo folds to mono by averaging each frame")
    void downmixesStereo() {
        short[] stereo = {100, 200, -100, -200, 0, 0};
        assertArrayEquals(new short[]{150, -150, 0}, Pcm.toMono(stereo, 2));
    }

    @Test
    @DisplayName("mono audio is passed straight through, not copied")
    void monoIsUntouched() {
        short[] mono = {1, 2, 3};
        assertSame(mono, Pcm.toMono(mono, 1));
        assertSame(mono, Pcm.toMono(mono, 0));
    }

    @Test
    @DisplayName("averaging rather than summing keeps two loud channels from clipping")
    void doesNotClip() {
        // Summed, these would be 65534 and wrap to a negative sample — an audible crack.
        short[] loud = {Short.MAX_VALUE, Short.MAX_VALUE};
        assertArrayEquals(new short[]{Short.MAX_VALUE}, Pcm.toMono(loud, 2));
    }

    @Test
    @DisplayName("audio already at 48 kHz is returned untouched")
    void passesThroughAtTargetRate() {
        short[] samples = {1, 2, 3, 4};
        assertSame(samples, Pcm.resample(samples, Pcm.SAMPLE_RATE));
    }

    @Test
    @DisplayName("44.1 kHz stretches to 48 kHz, lengthening by the rate ratio")
    void resamplesUpwards() {
        short[] second = new short[44_100];
        short[] resampled = Pcm.resample(second, 44_100);
        // One second in must stay one second out; a wrong length here IS the wrong pitch.
        assertEquals(Pcm.SAMPLE_RATE, resampled.length);
        assertEquals(1.0, Pcm.seconds(resampled), 0.001);
    }

    @Test
    @DisplayName("a higher source rate compresses to 48 kHz")
    void resamplesDownwards() {
        short[] second = new short[96_000];
        assertEquals(Pcm.SAMPLE_RATE, Pcm.resample(second, 96_000).length);
    }

    @Test
    @DisplayName("resampling interpolates rather than repeating samples")
    void interpolates() {
        // A ramp stays a ramp: every value between the endpoints, never a plateau of duplicates.
        short[] ramp = new short[100];
        for (int i = 0; i < ramp.length; i++) {
            ramp[i] = (short) (i * 100);
        }
        short[] resampled = Pcm.resample(ramp, 24_000);
        assertEquals(200, resampled.length);
        assertTrue(resampled[0] <= resampled[1]);
        assertNotEquals(resampled[0], resampled[1]);
        for (int i = 1; i < resampled.length; i++) {
            assertTrue(resampled[i] >= resampled[i - 1], "the ramp must stay monotonic at " + i);
        }
    }

    @Test
    @DisplayName("empty audio survives resampling instead of throwing")
    void handlesEmpty() {
        assertEquals(0, Pcm.resample(new short[0], 44_100).length);
    }

    @Test
    @DisplayName("a nonsensical sample rate is refused loudly")
    void refusesBadRate() {
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[]{1}, 0));
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[]{1}, -44_100));
    }

    @Test
    @DisplayName("duration is reported against the target rate")
    void reportsSeconds() {
        assertEquals(2.0, Pcm.seconds(new short[Pcm.SAMPLE_RATE * 2]), 0.0001);
    }
}
