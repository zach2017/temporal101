package demo.temporal.worker.activity;

import demo.temporal.activity.FileStorageActivities;
import io.temporal.activity.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Handles filesystem operations: temp directory setup, text merging, and
 * copying final results to the caller-specified output location.
 */
public class FileStorageActivitiesImpl implements FileStorageActivities {

    private static final Logger log = LoggerFactory.getLogger(FileStorageActivitiesImpl.class);

    @Override
    public String createTmpDirectories(String tmpBase, String baseName) {
        log.info("Creating tmp directories under {}/{}", tmpBase, baseName);
        try {
            Path mainDir = Path.of(tmpBase, baseName);
            Path imageDir = mainDir.resolve(baseName + "_images");

            Files.createDirectories(mainDir);
            Files.createDirectories(imageDir);

            log.info("Created: {} and {}", mainDir, imageDir);
            return mainDir.toAbsolutePath().toString();

        } catch (IOException e) {
            log.error("Failed to create tmp directories for {}", baseName, e);
            throw Activity.wrap(e);
        }
    }

    @Override
    public String mergeTextFiles(List<String> textFilePaths, String outputPath) {
        log.info("Merging {} text files → {}", textFilePaths.size(), outputPath);
        try {
            StringBuilder combined = new StringBuilder();

            for (int i = 0; i < textFilePaths.size(); i++) {
                String path = textFilePaths.get(i);
                if (!Files.exists(Path.of(path))) {
                    log.warn("Skipping missing file: {}", path);
                    continue;
                }

                String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);

                if (i > 0) {
                    combined.append("\n\n")
                            .append("═".repeat(72))
                            .append("\n")
                            .append("── Source: ").append(Path.of(path).getFileName())
                            .append("\n")
                            .append("═".repeat(72))
                            .append("\n\n");
                }
                combined.append(content);

                // Heartbeat on large merges
                Activity.getExecutionContext().heartbeat(
                        "Merged " + (i + 1) + "/" + textFilePaths.size());
            }

            Path out = Path.of(outputPath);
            Files.createDirectories(out.getParent());
            Files.writeString(out, combined.toString(), StandardCharsets.UTF_8);

            log.info("Merged text: {} total chars → {}", combined.length(), outputPath);
            return outputPath;

        } catch (IOException e) {
            log.error("Failed to merge text files", e);
            throw Activity.wrap(e);
        }
    }

    @Override
    public String copyToOutput(String sourcePath, String outputLocation, String outputFileName) {
        log.info("Copying {} → {}/{}", sourcePath, outputLocation, outputFileName);
        try {
            Path outputDir = Path.of(outputLocation);
            Files.createDirectories(outputDir);

            Path destination = outputDir.resolve(outputFileName);
            Files.copy(Path.of(sourcePath), destination, StandardCopyOption.REPLACE_EXISTING);

            String absPath = destination.toAbsolutePath().toString();
            log.info("Final output written to: {}", absPath);
            return absPath;

        } catch (IOException e) {
            log.error("Failed to copy result to output location", e);
            throw Activity.wrap(e);
        }
    }
}
