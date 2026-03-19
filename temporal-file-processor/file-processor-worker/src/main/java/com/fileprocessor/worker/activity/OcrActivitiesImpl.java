package com.fileprocessor.worker.activity;

import com.fileprocessor.activity.OcrActivities;
import com.fileprocessor.model.ExtractedImageInfo;
import io.temporal.activity.Activity;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR Activity implementation using Tesseract (via Tess4J).
 *
 * <p>Expects the Tesseract trained data to be available at the path
 * specified by the {@code TESSDATA_PREFIX} environment variable, or
 * defaults to {@code /usr/share/tesseract-ocr/5/tessdata}.</p>
 */
public class OcrActivitiesImpl implements OcrActivities {

    private static final Logger log = LoggerFactory.getLogger(OcrActivitiesImpl.class);

    /** Lazily-initialised, thread-safe Tesseract instance. */
    private final Tesseract tesseract;

    public OcrActivitiesImpl() {
        tesseract = new Tesseract();

        // Resolve tessdata path from env or fall back to common Linux paths
        String tessDataPath = System.getenv("TESSDATA_PREFIX");
        if (tessDataPath == null || tessDataPath.isBlank()) {
            // Try standard locations
            for (String candidate : new String[]{
                    "/usr/share/tesseract-ocr/5/tessdata",
                    "/usr/share/tesseract-ocr/4.00/tessdata",
                    "/usr/share/tessdata",
                    "/usr/local/share/tessdata"
            }) {
                if (new File(candidate).isDirectory()) {
                    tessDataPath = candidate;
                    break;
                }
            }
        }

        if (tessDataPath != null) {
            tesseract.setDatapath(tessDataPath);
        }

        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1);  // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // LSTM neural net mode

        log.info("Tesseract initialised with datapath={}", tessDataPath);
    }

    /**
     * Allow injection of a custom tessdata path (useful for testing / non-standard installs).
     */
    public OcrActivitiesImpl(String tessDataPath) {
        tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);
    }

    @Override
    public ExtractedImageInfo ocrImage(String imagePath, String textOutputPath) {
        log.info("Running OCR on image: {} → {}", imagePath, textOutputPath);

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            throw Activity.wrap(new IOException("Image not found: " + imagePath));
        }

        try {
            // Run Tesseract OCR
            String ocrText = tesseract.doOCR(imageFile);

            // Write OCR text to output
            Path outPath = Path.of(textOutputPath);
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, ocrText, StandardCharsets.UTF_8);

            log.info("OCR completed: {} chars from {}", ocrText.length(), imagePath);

            return new ExtractedImageInfo(
                    imageFile.getName(),
                    imagePath,
                    textOutputPath,
                    ocrText.length()
            );

        } catch (TesseractException e) {
            log.error("Tesseract OCR failed for {}", imagePath, e);
            throw Activity.wrap(new RuntimeException("OCR failed for " + imagePath, e));
        } catch (IOException e) {
            log.error("Failed to write OCR output for {}", imagePath, e);
            throw Activity.wrap(e);
        }
    }
}
