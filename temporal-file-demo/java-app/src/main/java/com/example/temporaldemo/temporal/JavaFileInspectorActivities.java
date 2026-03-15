package com.example.temporaldemo.temporal;

import com.example.temporaldemo.model.FileResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaFileInspectorActivities implements FileInspectorActivities {
    private final AppProperties properties;

    public JavaFileInspectorActivities(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public FileResult inspect(String filename) {
        Path filePath = Path.of(properties.getSharedFilesDir(), filename).normalize();

        if (!Files.exists(filePath) || !filePath.startsWith(Path.of(properties.getSharedFilesDir()))) {
            return new FileResult("java", filename, "unknown", 0, "File not found in /shared-files");
        }

        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = guessFromExtension(filename);
            }
            long size = Files.size(filePath);
            return new FileResult("java", filename, contentType, size, "Processed by Java worker");
        } catch (IOException e) {
            return new FileResult("java", filename, "error", 0, "Java worker error: " + e.getMessage());
        }
    }

    private String guessFromExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
