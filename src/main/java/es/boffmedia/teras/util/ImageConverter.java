package es.boffmedia.teras.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Utility class for converting images to Base64 encoded strings
 */
public class ImageConverter {
    
    /**
     * Converts a BufferedImage to a Base64 encoded string
     * 
     * @param image The image to convert
     * @param format The image format (png, jpeg, jpg)
     * @param quality The quality for JPEG compression (0-100), ignored for PNG
     * @return Base64 encoded string of the image
     * @throws Exception if conversion fails
     */
    public static String imageToBase64(BufferedImage image, String format, int quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        if (format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")) {
            // For JPEG with quality control
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality / 100f);
            }
            
            writer.setOutput(new MemoryCacheImageOutputStream(baos));
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
        } else {
            // PNG or other formats
            ImageIO.write(image, format, baos);
        }
        
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}
