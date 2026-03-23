package com.pdfextraction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pdfextraction.config.TemporalConfig;
import com.pdfextraction.model.PdfExtractionRequest;
import com.pdfextraction.model.PdfExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point – starts a PDF extraction workflow and prints the result.
 * <p>
 * Usage:
 * <pre>
 *   java -cp ... com.pdfextraction.client.PdfExtractionClient \
 *       --file-name report.pdf \
 *       --file-location /data/report.pdf
 * </pre>
 * Or with the Gradle application plugin:
 * <pre>
 *   ./gradlew run --args="--file-name report.pdf --file-location /data/report.pdf"
 * </pre>
 */
public final class PdfExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionClient.class);

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);

        TemporalConfig config = TemporalConfig.load();
        log.info("Connecting to Temporal at {} (namespace: {}, queue: {})",
                config.host(), config.namespace(), config.taskQueue());

        var request = new PdfExtractionRequest(cli.fileName(), cli.fileLocation());

        try (var service = new PdfExtractionService(config)) {
            PdfExtractionResult result = service.extractSync(request);

            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(result));
        }
    }

    // ── Minimal CLI arg parser ───────────────────────────────

    private record CliArgs(String fileName, String fileLocation) {

        static CliArgs parse(String[] args) {
            String fileName = null;
            String fileLocation = null;

            for (int i = 0; i < args.length - 1; i++) {
                switch (args[i]) {
                    case "--file-name" -> fileName = args[++i];
                    case "--file-location" -> fileLocation = args[++i];
                }
            }

            if (fileName == null || fileLocation == null) {
                System.err.println("""
                        Usage: PdfExtractionClient \\
                            --file-name <name.pdf> \\
                            --file-location </path/to/file.pdf>
                        """);
                System.exit(1);
            }

            return new CliArgs(fileName, fileLocation);
        }
    }
}
