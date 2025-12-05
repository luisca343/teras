package es.boffmedia.teras.util;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.nio.IntBuffer;

/**
 * Utility class for capturing screenshots from Minecraft
 */
public class ScreenshotCapture {
    
    /**
     * Captures a screenshot of the current Minecraft screen
     * 
     * @param includeUI Whether to include the game UI in the screenshot
     * @return BufferedImage containing the screenshot
     */
    public static BufferedImage captureMinecraftScreen(boolean includeUI) {
        Minecraft mc = Minecraft.getInstance();
        Framebuffer framebuffer = mc.getMainRenderTarget();
        
        int width = framebuffer.width;
        int height = framebuffer.height;
        int size = width * height;
        
        // If we don't want UI, we need to temporarily hide it
        boolean wasHidden = mc.options.hideGui;
        if (!includeUI && !wasHidden) {
            mc.options.hideGui = true;
            // Force a frame render
            try {
                mc.gameRenderer.renderLevel(mc.getFrameTime(), System.nanoTime(), new com.mojang.blaze3d.matrix.MatrixStack());
            } catch (Exception e) {
                // If rendering fails, continue with current frame
            }
        }
        
        // Create buffer for pixel data
        IntBuffer pixelBuffer = BufferUtils.createIntBuffer(size);
        int[] pixelData = new int[size];
        
        // Read pixels from the framebuffer
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        
        framebuffer.bindRead();
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, 
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        pixelBuffer.get(pixelData);
        framebuffer.unbindRead();
        
        // Restore UI visibility if we changed it
        if (!includeUI && !wasHidden) {
            mc.options.hideGui = wasHidden;
        }
        
        // Convert to BufferedImage
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Process pixels (convert BGRA to RGB and flip vertically)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcIndex = x + (height - 1 - y) * width; // Flip Y
                int pixel = pixelData[srcIndex];
                
                // Convert BGRA to RGB
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int rgb = (r << 16) | (g << 8) | b;
                
                image.setRGB(x, y, rgb);
            }
        }
        
        return image;
    }
}
