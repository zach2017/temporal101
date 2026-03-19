package com.fileprocessor.worker.activity;

import com.fileprocessor.activity.TextExtractionActivities;
import io.temporal.activity.Activity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements text extraction for plain text, PDF (text layer + image extraction),
 * and Office documents.
 */
public class TextExtractionActivitiesImpl implements TextExtractionActivities {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionActivitiesImpl.class);
    private final Tika tika = new Tika();

    // ─── Plain Text ──────────────────────────────────────────────────

    @Override
    public String extractPlainText(String filePath, String outputPath) {
        log.info("Extracting plain text: {} → {}", filePath, outputPath);
        try {
            String content = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            writeText(outputPath, content);
            log.info("Plain text extracted: {} chars", content.length());
            return outputPath;
        } catch (IOException e) {
            log.error("Failed to read plain text from {}", filePath, e);
            throw Activity.wrap(e);
        }
    }

    // ─── PDF Text ────────────────────────────────────────────────────

    @Override
    public String extractPdfText(String filePath, String outputPath) {
        log.info("Extracting PDF text layer: {} → {}", filePath, outputPath);
        try (PDDocument doc = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            writeText(outputPath, text);
            log.info("PDF text extracted: {} chars from {} pages",
                    text.length(), doc.getNumberOfPages());
            return outputPath;
        } catch (IOException e) {
            log.error("Failed to extract PDF text from {}", filePath, e);
            throw Activity.wrap(e);
        }
    }

    // ─── PDF Image Extraction ────────────────────────────────────────

    @Override
    public List<String> extractPdfImages(String filePath, String imageOutputDir) {
        log.info("Extracting images from PDF: {} → {}", filePath, imageOutputDir);
        List<String> imagePaths = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(new File(filePath))) {
            Files.createDirectories(Path.of(imageOutputDir));

            int pageIndex = 0;
            for (PDPage page : doc.getPages()) {
                pageIndex++;
                PDResources resources = page.getResources();
                if (resources == null) continue;

                int imgIndex = 0;
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.isImageXObject(name)) {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                        BufferedImage buffered = image.getImage();

                        String suffix = image.getSuffix();
                        if (suffix == null || suffix.isBlank()) suffix = "png";

                        String imgFileName = String.format("page_%d_img_%d.%s",
                                pageIndex, imgIndex, suffix);
                        String imgPath = Path.of(imageOutputDir, imgFileName).toString();

                        ImageIO.write(buffered, suffix, new File(imgPath));
                        imagePaths.add(imgPath);

                        imgIndex++;
                        log.debug("Extracted image: {}", imgPath);
                    }
                }

                // Heartbeat after each page so Temporal knows we're alive
                Activity.getExecutionContext().heartbeat(
                        "Processed page " + pageIndex + "/" + doc.getNumberOfPages());
            }

            log.info("Extracted {} images from {} pages in {}",
                    imagePaths.size(), doc.getNumberOfPages(), filePath);
            return imagePaths;

        } catch (IOException e) {
            log.error("Failed to extract images from PDF {}", filePath, e);
            throw Activity.wrap(e);
        }
    }

    // ─── Office Documents ────────────────────────────────────────────

    @Override
    public String extractOfficeText(String filePath, String outputPath) {
        log.info("Extracting Office document text: {} → {}", filePath, outputPath);
        try {
            // Tika handles .docx, .xlsx, .pptx, .doc, .xls, .ppt, .rtf
            String text = tika.parseToString(Path.of(filePath));
            writeText(outputPath, text);
            log.info("Office text extracted: {} chars", text.length());
            return outputPath;
        } catch (IOException | TikaException e) {
            log.error("Failed to extract Office text from {}", filePath, e);
            throw Activity.wrap(e);
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private void writeText(String outputPath, String content) throws IOException {
        Path out = Path.of(outputPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content, StandardCharsets.UTF_8);
    }
}
