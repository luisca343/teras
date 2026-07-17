package es.boffmedia.teras.client.camera;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the downscale that bounds what a capture sends to the page. Pure Java2D — no Minecraft.
 *
 * <p>The cap is the whole reason this step exists: a framebuffer is physical pixels, so a 4K capture as
 * Base64 PNG is tens of megabytes through a single CEF callback.</p>
 */
class CameraImageTest {

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void capsTheLongEdgeAndKeepsAspect() {
        BufferedImage scaled = CameraImage.downscale(image(3840, 2160), 1920);
        assertEquals(1920, scaled.getWidth());
        assertEquals(1080, scaled.getHeight());
    }

    /** Portrait: the cap applies to whichever edge is longest, not always the width. */
    @Test
    void capsTheHeightWhenTallerThanWide() {
        BufferedImage scaled = CameraImage.downscale(image(1080, 3840), 1920);
        assertEquals(1920, scaled.getHeight());
        assertEquals(540, scaled.getWidth());
    }

    /** Never upscale — a small window's capture should be sent as-is, not blown up. */
    @Test
    void leavesSmallerImagesUntouched() {
        BufferedImage original = image(1280, 720);
        assertSame(original, CameraImage.downscale(original, 1920));
    }

    @Test
    void leavesAnExactlyCappedImageUntouched() {
        BufferedImage original = image(1920, 1080);
        assertSame(original, CameraImage.downscale(original, 1920));
    }

    /** An extreme aspect ratio must not round the short edge down to a zero-sized image. */
    @Test
    void neverProducesAZeroSizedEdge() {
        BufferedImage scaled = CameraImage.downscale(image(4000, 3), 1920);
        assertEquals(1920, scaled.getWidth());
        assertTrue(scaled.getHeight() >= 1, "height collapsed to " + scaled.getHeight());
    }

    /**
     * NativeImage packs RGBA bytes little-endian, so a pixel reads back as 0xAABBGGRR. Getting this
     * backwards swaps red and blue in every photo and nothing else would catch it — it compiles, and a
     * screenshot still arrives, just with the sky orange.
     */
    @Test
    void unpacksNativeImageAbgrIntoRgb() {
        int opaqueRed = 0xFF0000FF;   // A=FF B=00 G=00 R=FF
        int opaqueBlue = 0xFFFF0000;  // A=FF B=FF G=00 R=00
        int opaqueGreen = 0xFF00FF00; // A=FF B=00 G=FF R=00

        BufferedImage image = CameraImage.toBufferedImage(
                new CameraImage.Pixels(new int[] {opaqueRed, opaqueBlue, opaqueGreen, 0xFFFFFFFF}, 2, 2));

        assertEquals(0xFF0000, image.getRGB(0, 0) & 0xFFFFFF, "red channel");
        assertEquals(0x0000FF, image.getRGB(1, 0) & 0xFFFFFF, "blue channel");
        assertEquals(0x00FF00, image.getRGB(0, 1) & 0xFFFFFF, "green channel");
        assertEquals(0xFFFFFF, image.getRGB(1, 1) & 0xFFFFFF, "white");
    }

    /** Row-major order: the second pixel is (1,0), not (0,1) — a stride slip would shear the image. */
    @Test
    void unpacksInRowMajorOrder() {
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        BufferedImage image = CameraImage.toBufferedImage(
                new CameraImage.Pixels(new int[] {black, white, white, black}, 2, 2));

        assertEquals(0x000000, image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0xFFFFFF, image.getRGB(1, 0) & 0xFFFFFF);
        assertEquals(0xFFFFFF, image.getRGB(0, 1) & 0xFFFFFF);
        assertEquals(0x000000, image.getRGB(1, 1) & 0xFFFFFF);
    }
}
