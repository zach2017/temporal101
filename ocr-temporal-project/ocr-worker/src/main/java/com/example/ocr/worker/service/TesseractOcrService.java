package com.example.ocr.worker.service;

import com.example.ocr.common.constants.OcrConstants;
import com.example.ocr.common.enums.OutputFormat;
import com.example.ocr.common.exception.OcrLanguageNotAvailableException;
import com.example.ocr.common.exception.OcrProcessingException;
import com.example.ocr.common.exception.OcrUnsupportedFormatException;
import com.example.ocr.common.model.OcrRequest;
import com.example.ocr.common.model.OcrResult;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that wraps Tess4J / Tesseract OCR operations.
 * Handles language validation, Tesseract configuration, and result building.
 */
public class TesseractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    private final String tessdataPath;
    private final ImagePreprocessor preprocessor;
    private final float minConfidenceThreshold;

    public TesseractOcrService(String tessdataPath) {
        this(tessdataPath, OcrConstants.DEFAULT_MIN_CONFIDENCE);
    }

    public TesseractOcrService(String tessdataPath, float minConfidenceThreshold) {
        this.tessdataPath = tessdataPath;
        this.preprocessor = new ImagePreprocessor();
        this.minConfidenceThreshold = minConfidenceThreshold;
        log.info("TesseractOcrService initialized with tessdata: {}, minConfidence: {}",
                tessdataPath, minConfidenceThreshold);
    }

    /**
     * Perform OCR on the given local file with the specified request configuration.
     *
     * @param localFilePath path to the image file
     * @param request       OCR configuration
     * @return the OCR result
     */
    public OcrResult performOcr(String localFilePath, OcrRequest request) {
        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        File imageFile = new File(localFilePath);

        // Validate file exists
        if (!imageFile.exists() || !imageFile.canRead()) {
            throw new OcrProcessingException("Cannot read image file: " + localFilePath);
        }

        // Validate languages
        validateLanguages(request.getLanguage());

        try {
            // Preprocess the image
            BufferedImage processedImage = preprocessor.preprocess(
                    imageFile, request.getPreprocessingOptions(), request.getDpi());

            // Configure Tesseract
            ITesseract tesseract = createTesseractInstance(request);

            // Perform OCR - plain text
            String text = tesseract.doOCR(processedImage);

            // Calculate confidence by running again with TSV to get word confidences
            float meanConfidence = calculateMeanConfidence(tesseract, processedImage);

            // Count words
            String trimmedText = text != null ? text.trim() : "";
            int wordCount = trimmedText.isEmpty() ? 0 :
                    trimmedText.split("\\s+").length;

            // Detect no-text-found
            boolean textFound = isTextFound(trimmedText, meanConfidence, wordCount, warnings);

            // Build result
            OcrResult.OcrResultBuilder builder = OcrResult.builder()
                    .text(trimmedText)
                    .meanConfidence(meanConfidence)
                    .wordCount(wordCount)
                    .textFound(textFound)
                    .language(request.getLanguage())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .sourceFileName(request.getFileName());

            // Optional HOCR output
            if (request.getOutputFormats().contains(OutputFormat.HOCR)) {
                String hocr = tesseract.doOCR(processedImage, "hocr");
                builder.hocrOutput(hocr);
            }

            // Optional TSV output
            if (request.getOutputFormats().contains(OutputFormat.TSV)) {
                String tsv = tesseract.doOCR(processedImage, "tsv");
                builder.tsvOutput(tsv);
            }

            // Add confidence warning
            if (meanConfidence > 0 && meanConfidence < 50) {
                warnings.add("Low mean confidence: " + String.format("%.1f", meanConfidence)
                        + "%. OCR results may be unreliable.");
            }

            builder.warnings(warnings);

            OcrResult result = builder.build();
            log.info("OCR completed: {}", result);
            return result;

        } catch (TesseractException e) {
            throw new OcrProcessingException("Tesseract OCR failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new OcrUnsupportedFormatException(request.getFileName(),
                    "Cannot read image: " + e.getMessage());
        }
    }

    /**
     * Create and configure a Tesseract instance based on the request.
     */
    private ITesseract createTesseractInstance(OcrRequest request) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(request.getLanguage());
        tesseract.setOcrEngineMode(request.getOcrEngineMode().getValue());
        tesseract.setPageSegMode(request.getPageSegMode().getValue());

        // Set optional Tesseract variables
        if (request.getCharWhitelist() != null && !request.getCharWhitelist().isBlank()) {
            tesseract.setTessVariable("tessedit_char_whitelist", request.getCharWhitelist());
        }

        if (request.isPreserveInterwordSpaces()) {
            tesseract.setTessVariable("preserve_interword_spaces", "1");
        }

        if (request.getDpi() > 0) {
            tesseract.setTessVariable("user_defined_dpi", String.valueOf(request.getDpi()));
        }

        return tesseract;
    }

    /**
     * Validate that all requested language traineddata files exist.
     */
    private void validateLanguages(String languageSpec) {
        String[] languages = languageSpec.split("\\+");
        List<String> missing = new ArrayList<>();

        for (String lang : languages) {
            String trimmedLang = lang.trim();
            Path trainedDataFile = Paths.get(tessdataPath, trimmedLang + ".traineddata");
            if (!Files.exists(trainedDataFile)) {
                missing.add(trimmedLang);
            }
        }

        if (!missing.isEmpty()) {
            throw new OcrLanguageNotAvailableException(missing);
        }
    }

    /**
     * Calculate mean word confidence using the Tesseract API.
     * Returns 0 if no words are detected.
     */
    private float calculateMeanConfidence(ITesseract tesseract, BufferedImage image) {
        try {
            // Use the Tesseract API to get word confidences
            // Tess4J provides getWords() which returns word-level results
            // For simplicity, we use the overall mean confidence from Tesseract
            String tsvOutput = tesseract.doOCR(image, "tsv");
            if (tsvOutput == null || tsvOutput.isBlank()) {
                return 0;
            }

            String[] lines = tsvOutput.split("\n");
            float totalConfidence = 0;
            int wordLines = 0;

            for (int i = 1; i < lines.length; i++) { // Skip header
                String[] cols = lines[i].split("\t");
                if (cols.length >= 12) {
                    try {
                        float conf = Float.parseFloat(cols[10].trim());
                        if (conf >= 0) { // -1 means no confidence (block/paragraph level)
                            totalConfidence += conf;
                            wordLines++;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            return wordLines > 0 ? totalConfidence / wordLines : 0;
        } catch (Exception e) {
            log.warn("Could not calculate mean confidence: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Determine if meaningful text was found using multiple heuristics.
     */
    private boolean isTextFound(String text, float confidence, int wordCount, List<String> warnings) {
        if (text == null || text.isBlank()) {
            warnings.add("No text detected in the image.");
            return false;
        }

        if (wordCount == 0) {
            warnings.add("No words detected after trimming.");
            return false;
        }

        if (confidence > 0 && confidence < minConfidenceThreshold) {
            warnings.add("Mean confidence (" + String.format("%.1f", confidence)
                    + "%) is below minimum threshold (" + minConfidenceThreshold + "%).");
            return false;
        }

        // Garbage detection: check if mostly non-printable or symbol characters
        long printableChars = text.chars()
                .filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))
                .count();
        double printableRatio = (double) printableChars / text.length();
        if (printableRatio < 0.3) {
            warnings.add("Text appears to be mostly garbage characters (printable ratio: "
                    + String.format("%.1f%%", printableRatio * 100) + ").");
            return false;
        }

        return true;
    }

    /**
     * List all available languages in the tessdata directory.
     */
    public List<String> getAvailableLanguages() {
        try {
            return Files.list(Paths.get(tessdataPath))
                    .filter(p -> p.toString().endsWith(".traineddata"))
                    .map(p -> p.getFileName().toString().replace(".traineddata", ""))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Could not list tessdata directory: {}", e.getMessage());
            return List.of();
        }
    }
}
