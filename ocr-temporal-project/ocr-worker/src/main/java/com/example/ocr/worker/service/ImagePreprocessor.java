package com.example.ocr.worker.service;

import com.example.ocr.common.model.PreprocessingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;

/**
 * Applies image preprocessing steps to improve OCR accuracy.
 * Each step is optional and controlled via {@link PreprocessingOptions}.
 */
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);

    /**
     * Apply all configured preprocessing steps to the image.
     *
     * @param imageFile the source image file
     * @param options   preprocessing configuration
     * @param targetDpi target DPI for upscaling
     * @return the preprocessed image as a BufferedImage
     * @throws IOException if the image cannot be read
     */
    public BufferedImage preprocess(File imageFile, PreprocessingOptions options, int targetDpi)
            throws IOException {

        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("ImageIO could not read file: " + imageFile.getName()
                    + ". Format may be unsupported.");
        }

        log.debug("Original image: {}x{}, type={}", image.getWidth(), image.getHeight(), image.getType());

        // Step 1: Remove alpha channel
        if (options.isRemoveAlpha() && image.getColorModel().hasAlpha()) {
            image = removeAlphaChannel(image);
            log.debug("Alpha channel removed");
        }

        // Step 2: Convert to grayscale
        if (options.isConvertToGrayscale() && image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            image = convertToGrayscale(image);
            log.debug("Converted to grayscale");
        }

        // Step 3: Noise removal (Gaussian blur)
        if (options.isRemoveNoise()) {
            image = applyGaussianBlur(image);
            log.debug("Gaussian blur applied for noise removal");
        }

        // Step 4: Add border
        if (options.isAddBorder()) {
            image = addWhiteBorder(image, options.getBorderSizePx());
            log.debug("Added {}px white border", options.getBorderSizePx());
        }

        return image;
    }

    /**
     * Remove the alpha channel by painting onto a white background.
     */
    private BufferedImage removeAlphaChannel(BufferedImage image) {
        BufferedImage newImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    /**
     * Convert image to grayscale.
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage grayImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return grayImage;
    }

    /**
     * Apply a 3x3 Gaussian blur for noise reduction.
     */
    private BufferedImage applyGaussianBlur(BufferedImage image) {
        float[] matrix = {
                1 / 16f, 2 / 16f, 1 / 16f,
                2 / 16f, 4 / 16f, 2 / 16f,
                1 / 16f, 2 / 16f, 1 / 16f
        };
        Kernel kernel = new Kernel(3, 3, matrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }

    /**
     * Add a white border around the image.
     */
    private BufferedImage addWhiteBorder(BufferedImage image, int borderSize) {
        int newWidth = image.getWidth() + 2 * borderSize;
        int newHeight = image.getHeight() + 2 * borderSize;
        BufferedImage bordered = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g = bordered.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(image, borderSize, borderSize, null);
        g.dispose();
        return bordered;
    }
}
