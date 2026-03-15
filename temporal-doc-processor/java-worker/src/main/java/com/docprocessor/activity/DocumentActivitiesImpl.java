package com.docprocessor.activity;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DocumentActivitiesImpl implements DocumentActivities {

    private static final Logger log = LoggerFactory.getLogger(DocumentActivitiesImpl.class);

    @Value("${storage.path}")
    private String storagePath;

    @Override
    public String extractTextFromPdf(String pdfFilePath) {
        log.info("Extracting text from PDF: {}", pdfFilePath);
        try {
            File pdfFile = new File(pdfFilePath);
            if (!pdfFile.exists()) {
                throw new RuntimeException("PDF file not found: " + pdfFilePath);
            }

            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Extracted {} characters from PDF ({} pages)",
                        text.length(), document.getNumberOfPages());
                return text;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public String saveTextToFile(String documentId, String originalFileName, String textContent) {
        log.info("Saving extracted text for document: {} ({})", documentId, originalFileName);

        Path docDir = Paths.get(storagePath, "documents", documentId);
        try {
            Files.createDirectories(docDir);

            // Save the extracted text
            String textFileName = originalFileName.replaceAll("\\.[^.]+$", "") + ".txt";
            Path textFilePath = docDir.resolve(textFileName);
            Files.writeString(textFilePath, textContent, StandardCharsets.UTF_8);

            // Save metadata
            String metadata = String.format(
                    "{\"documentId\":\"%s\",\"originalFileName\":\"%s\",\"textFileName\":\"%s\",\"charCount\":%d}",
                    documentId, originalFileName, textFileName, textContent.length()
            );
            Files.writeString(docDir.resolve("metadata.json"), metadata, StandardCharsets.UTF_8);

            log.info("Text saved to: {}", textFilePath);
            return textFilePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save text file: " + e.getMessage(), e);
        }
    }

    @Override
    public String readTextFile(String documentId) {
        log.info("Reading text file for document: {}", documentId);

        Path docDir = Paths.get(storagePath, "documents", documentId);
        try {
            // Read metadata to find the text file name
            Path metadataPath = docDir.resolve("metadata.json");
            if (!Files.exists(metadataPath)) {
                throw new RuntimeException("Document not found: " + documentId);
            }

            String metadata = Files.readString(metadataPath, StandardCharsets.UTF_8);
            // Simple JSON parsing for textFileName
            String textFileName = metadata.split("\"textFileName\":\"")[1].split("\"")[0];

            Path textFilePath = docDir.resolve(textFileName);
            return Files.readString(textFilePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read text file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean documentExists(String documentId) {
        Path metadataPath = Paths.get(storagePath, "documents", documentId, "metadata.json");
        return Files.exists(metadataPath);
    }
}
