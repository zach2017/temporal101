package com.docprocessing.client;

import com.docprocessing.config.TemporalConfig;
import com.docprocessing.model.DocumentProcessingRequest;
import com.docprocessing.model.DocumentProcessingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point — publishes a DocumentIntakeWorkflow to the
 * {@code document-intake-queue}.
 *
 * <p>{@code --file-type} is <strong>not required</strong>.  When
 * omitted the Java Tika worker performs content-based MIME detection
 * as the first step of the workflow.
 *
 * <p>Usage:
 * <pre>
 *   ./gradlew run --args="--file-name report.pdf --file-location /data/docs/report.pdf"
 *   ./gradlew run --args="--file-name photo.jpg --file-location /data/docs/photo.jpg"
 * </pre>
 */
public final class DocumentProcessingClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingClient.class);

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);

        TemporalConfig config = TemporalConfig.load();
        log.info("Connecting to Temporal at {} (namespace: {}, queue: {})",
                config.host(), config.namespace(), config.taskQueue());

        var request = new DocumentProcessingRequest(
                cli.fileName(), cli.fileLocation(), cli.fileType());

        try (var service = new DocumentProcessingService(config)) {

            if (request.fileType().isBlank()) {
                log.info("Publishing workflow for '{}' (MIME will be auto-detected by Tika worker)",
                        request.fileName());
            } else {
                log.info("Publishing workflow for '{}' (MIME hint: '{}')",
                        request.fileName(), request.fileType());
            }

            DocumentProcessingResult result = service.processSync(request);

            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(result));
        }
    }

    // ── Minimal CLI arg parser ───────────────────────────────

    private record CliArgs(String fileName, String fileLocation, String fileType) {

        static CliArgs parse(String[] args) {
            String fileName = null;
            String fileLocation = null;
            String fileType = "";

            for (int i = 0; i < args.length - 1; i++) {
                switch (args[i]) {
                    case "--file-name" -> fileName = args[++i];
                    case "--file-location" -> fileLocation = args[++i];
                    case "--file-type" -> fileType = args[++i];
                }
            }

            if (fileName == null || fileLocation == null) {
                System.err.println("""
                        Usage: DocumentProcessingClient \\
                            --file-name <name.pdf> \\
                            --file-location </path/to/file>
                        
                        Options:
                            --file-type <mime/type>   Optional MIME hint (auto-detected if omitted)
                        
                        The Tika worker auto-detects the file type using content-based
                        analysis (magic bytes). You do NOT need to specify --file-type.
                        
                        Examples:
                            --file-name report.pdf   --file-location /data/docs/report.pdf
                            --file-name photo.jpg    --file-location /data/docs/photo.jpg
                            --file-name data.xlsx    --file-location /data/docs/data.xlsx
                        """);
                System.exit(1);
            }

            return new CliArgs(fileName, fileLocation, fileType);
        }
    }
}
