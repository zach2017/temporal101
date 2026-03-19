package com.example.ocr.worker.resolver;

import com.example.ocr.common.activity.OcrActivities.ResolvedFile;
import com.example.ocr.common.enums.FileSourceType;
import com.example.ocr.common.exception.OcrFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves files from the local or shared filesystem.
 * Validates the file exists and is readable.
 */
public class LocalFileResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalFileResolver.class);

    public ResolvedFile resolve(String filePath, String fileName) {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new OcrFileNotFoundException(filePath,
                    "Local file not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new OcrFileNotFoundException(filePath,
                    "Local file is not readable: " + filePath);
        }

        log.info("Local file resolved: {}", path.toAbsolutePath());
        // Local files are not temporary; we don't delete them during cleanup
        return new ResolvedFile(path.toAbsolutePath().toString(), FileSourceType.LOCAL, false);
    }
}
