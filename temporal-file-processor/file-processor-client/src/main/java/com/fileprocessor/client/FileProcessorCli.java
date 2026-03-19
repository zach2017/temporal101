package com.fileprocessor.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fileprocessor.model.FileProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Command-line interface for submitting file-processing Workflows.
 *
 * <h3>Usage</h3>
 * <pre>
 * java -jar file-processor-client.jar \
 *   --file-name report.pdf \
 *   --file-location /data/inbox/report.pdf \
 *   --output-location /data/outbox \
 *   --metadata '{"department":"finance","uploadedBy":"jsmith"}' \
 *   --temporal-address localhost:7233 \
 *   --namespace default \
 *   --async
 * </pre>
 */
public class FileProcessorCli {

    private static final Logger log = LoggerFactory.getLogger(FileProcessorCli.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {
        try {
            CliArgs parsed = parseArgs(args);

            if (parsed.showHelp) {
                printUsage();
                return;
            }

            log.info("═══════════════════════════════════════════════════════");
            log.info("  File Processor Client");
            log.info("═══════════════════════════════════════════════════════");
            log.info("File           : {}", parsed.fileName);
            log.info("Location       : {}", parsed.fileLocation);
            log.info("Output         : {}", parsed.outputLocation);
            log.info("Metadata       : {}", parsed.metadata);
            log.info("Temporal       : {}", parsed.temporalAddress);
            log.info("Async          : {}", parsed.async);
            log.info("═══════════════════════════════════════════════════════");

            try (FileProcessorClient client = FileProcessorClient.builder()
                    .temporalAddress(parsed.temporalAddress)
                    .namespace(parsed.namespace)
                    .build()) {

                if (parsed.async) {
                    // ── Async: fire and print Workflow ID ────────────
                    FileProcessorClient.AsyncHandle handle = client.processFileAsync(
                            parsed.fileName,
                            parsed.fileLocation,
                            parsed.outputLocation,
                            parsed.metadata);

                    System.out.println("\n✓ Workflow started asynchronously");
                    System.out.println("  Workflow ID: " + handle.getWorkflowId());
                    System.out.println("  Track at:    http://localhost:8080/namespaces/"
                            + parsed.namespace + "/workflows/" + handle.getWorkflowId());

                } else {
                    // ── Sync: block until complete ───────────────────
                    System.out.println("\n⏳ Processing file (blocking)…\n");
                    FileProcessingResult result = client.processFileSync(
                            parsed.fileName,
                            parsed.fileLocation,
                            parsed.outputLocation,
                            parsed.metadata);

                    System.out.println("════════════════════════════════════════");
                    System.out.println("  RESULT");
                    System.out.println("════════════════════════════════════════");
                    System.out.println(MAPPER.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(result));

                    System.exit(result.isSuccess() ? 0 : 1);
                }
            }

        } catch (Exception e) {
            log.error("Client failed", e);
            System.err.println("ERROR: " + e.getMessage());
            printUsage();
            System.exit(2);
        }
    }

    // ═════════════════════════════════════════════════════════════════

    private static CliArgs parseArgs(String[] args) throws Exception {
        CliArgs cli = new CliArgs();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file-name":
                case "-f":
                    cli.fileName = args[++i];
                    break;
                case "--file-location":
                case "-l":
                    cli.fileLocation = args[++i];
                    break;
                case "--output-location":
                case "-o":
                    cli.outputLocation = args[++i];
                    break;
                case "--metadata":
                case "-m":
                    cli.metadata = MAPPER.readValue(args[++i],
                            new TypeReference<Map<String, String>>() {});
                    break;
                case "--temporal-address":
                case "-t":
                    cli.temporalAddress = args[++i];
                    break;
                case "--namespace":
                case "-n":
                    cli.namespace = args[++i];
                    break;
                case "--async":
                case "-a":
                    cli.async = true;
                    break;
                case "--help":
                case "-h":
                    cli.showHelp = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (!cli.showHelp) {
            if (cli.fileName == null)       throw new IllegalArgumentException("--file-name is required");
            if (cli.fileLocation == null)   throw new IllegalArgumentException("--file-location is required");
            if (cli.outputLocation == null) throw new IllegalArgumentException("--output-location is required");
        }

        return cli;
    }

    private static void printUsage() {
        System.out.println("""
                
                Usage: file-processor-client [OPTIONS]
                
                Required:
                  -f, --file-name <name>           Original file name (e.g. report.pdf)
                  -l, --file-location <path>       Path to the source file
                  -o, --output-location <dir>      Directory for extracted text output
                
                Optional:
                  -m, --metadata <json>            JSON metadata (e.g. '{"key":"value"}')
                  -t, --temporal-address <addr>    Temporal gRPC address (default: localhost:7233)
                  -n, --namespace <ns>             Temporal namespace (default: default)
                  -a, --async                      Start workflow async (don't wait for result)
                  -h, --help                       Show this help
                
                Examples:
                  # Synchronous processing
                  java -jar file-processor-client.jar \\
                    -f invoice.pdf -l /data/inbox/invoice.pdf -o /data/outbox
                
                  # Async with metadata
                  java -jar file-processor-client.jar \\
                    -f scan.tiff -l /data/inbox/scan.tiff -o /data/outbox \\
                    -m '{"dept":"legal"}' --async
                """);
    }

    private static class CliArgs {
        String fileName;
        String fileLocation;
        String outputLocation;
        Map<String, String> metadata;
        String temporalAddress = "localhost:7233";
        String namespace = "default";
        boolean async = false;
        boolean showHelp = false;
    }
}
