package es.boffmedia.teras.client.camera;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Iterator;

/**
 * Turns a captured frame into the {@code data:} URL the SmartRotom web expects.
 *
 * <p>Split by thread: {@link #readPixels} must run on the render thread, where the frame lives, and does
 * nothing but a bulk copy out of it. Everything after — unpacking, downscaling, encoding, Base64 — is
 * pure CPU and belongs off it, being long enough at 4K to stutter the game.</p>
 */
final class CameraImage {
    private CameraImage() {}

    /**
     * Longest edge, in pixels, of the image sent to the page. A framebuffer is measured in physical
     * pixels, so a 1080p window on a 2× display captures at 4K — which as Base64 PNG runs to tens of
     * megabytes through one CEF callback.
     */
    static final int MAX_EDGE = 1920;

    /** A frame lifted out of its {@link NativeImage}, so the image itself can be freed immediately. */
    record Pixels(int[] abgr, int width, int height) {}

    /**
     * Copies a {@link NativeImage}'s pixels out in one bulk read. <b>Render thread only</b>, and the
     * caller still owns the image and must close it.
     *
     * <p>Only the bulk read happens here; unpacking is {@link #toBufferedImage}'s job, off-thread. A
     * per-pixel {@code getPixelRGBA} loop instead of this would put 8.3 million calls at 4K on the
     * render thread — the stutter this split exists to avoid.</p>
     */
    static Pixels readPixels(NativeImage image) {
        return new Pixels(image.getPixelsRGBA(), image.getWidth(), image.getHeight());
    }

    /** Unpacks {@link #readPixels}' output into a {@link BufferedImage}. Any thread. */
    static BufferedImage toBufferedImage(Pixels pixels) {
        int width = pixels.width();
        int height = pixels.height();
        int[] source = pixels.abgr();
        int[] rgb = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            // NativeImage packs the RGBA bytes little-endian, so each int reads back as 0xAABBGGRR —
            // red is the low byte, not the high one. takeScreenshot has already flipped it vertically.
            int abgr = source[i];
            rgb[i] = ((abgr & 0xFF) << 16) | (((abgr >> 8) & 0xFF) << 8) | ((abgr >> 16) & 0xFF);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, rgb, 0, width);
        return image;
    }

    /** Encodes to a {@code data:image/...;base64,...} URL, downscaling past {@link #MAX_EDGE} first. */
    static String toDataUrl(Pixels pixels, ScreenshotQuery query) throws Exception {
        BufferedImage scaled = downscale(toBufferedImage(pixels), MAX_EDGE);
        return "data:image/" + query.mimeSubtype() + ";base64," + encodeBase64(scaled, query);
    }

    /** {@code image} shrunk to fit {@code maxEdge}, preserving aspect; the original if it already fits. */
    static BufferedImage downscale(BufferedImage image, int maxEdge) {
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) {
            return image;
        }
        double factor = (double) maxEdge / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static String encodeBase64(BufferedImage image, ScreenshotQuery query) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (query.isJpeg()) {
            writeJpeg(image, query.quality(), bytes);
        } else {
            ImageIO.write(image, ScreenshotQuery.PNG, bytes);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static void writeJpeg(BufferedImage image, int quality, ByteArrayOutputStream out) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, ScreenshotQuery.PNG, out);
            return;
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality / 100f);
            }
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
