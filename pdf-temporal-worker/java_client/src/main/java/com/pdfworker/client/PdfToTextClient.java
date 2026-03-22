package com.pdfworker.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.pdfworker.model.PdfProcessingRequest;
import com.pdfworker.model.PdfProcessingResult;
import com.pdfworker.model.StorageType;
import com.pdfworker.workflow.PdfToTextWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.time.Duration;
import java.util.UUID;

/**
 * CLI client for the PDF-to-Text Temporal service.
 *
 * Usage:
 *   java -jar pdf-client.jar <file_name> <S3|NFS|URL> <location> [--no-images] [--host host:port]
 *
 * Examples:
 *   java -jar pdf-client.jar report.pdf S3  s3://my-bucket/docs
 *   java -jar pdf-client.jar spec.pdf   NFS /mnt/shared/pdfs
 *   java -jar pdf-client.jar paper.pdf  URL https://files.example.com/incoming
 */
public class PdfToTextClient {

    private static final String DEFAULT_HOST =
            System.getenv("TEMPORAL_HOST") != null
                    ? System.getenv("TEMPORAL_HOST")
                    : "127.0.0.1:7233";

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        // ── parse CLI args ──
        String  fileName      = args[0];
        String  storageArg    = args[1];
        String  location      = args[2];
        boolean extractImages = true;
        String  host          = DEFAULT_HOST;

        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--no-images" -> extractImages = false;
                case "--host"      -> { if (i + 1 < args.length) host = args[++i]; }
                default            -> System.err.println("Unknown flag: " + args[i]);
            }
        }

        StorageType storageType;
        try {
            storageType = StorageType.fromString(storageArg);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid storage type: " + storageArg);
            System.err.println("Must be one of: S3, NFS, URL");
            System.exit(1);
            return;
        }

        PdfProcessingRequest request = new PdfProcessingRequest(
                fileName, storageType, location, extractImages
        );

        System.out.println("┌─────────────────────────────────────────────┐");
        System.out.println("│  PDF-to-Text Temporal Client                │");
        System.out.println("├─────────────────────────────────────────────┤");
        System.out.printf ("│  File    : %-32s │%n", fileName);
        System.out.printf ("│  Storage : %-32s │%n", storageType);
        System.out.printf ("│  Location: %-32s │%n", location);
        System.out.printf ("│  Images  : %-32s │%n", extractImages);
        System.out.printf ("│  Host    : %-32s │%n", host);
        System.out.println("└─────────────────────────────────────────────┘");

        // ── Jackson mapper with snake_case to match Python models ──
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // ── Temporal connection ──
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(host)
                        .build()
        );

        WorkflowClient client = WorkflowClient.newInstance(
                service,
                io.temporal.client.WorkflowClientOptions.newBuilder()
                        .setDataConverter(
                                DefaultDataConverter.newDefaultInstance()
                                        .withPayloadConverterOverrides(
                                                new JacksonJsonPayloadConverter(mapper)
                                        )
                        )
                        .build()
        );

        // ── Start workflow ──
        String workflowId = "pdf-process-" + UUID.randomUUID();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(PdfToTextWorkflow.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(60))
                .build();

        PdfToTextWorkflow workflow = client.newWorkflowStub(
                PdfToTextWorkflow.class, options
        );

        System.out.printf("%nStarting workflow [%s] …%n", workflowId);

        PdfProcessingResult result = workflow.processPdf(request);

        // ── Print result ──
        System.out.println();
        System.out.println(result.toReport());

        System.exit(result.isSuccess() ? 0 : 1);
    }

    private static void printUsage() {
        System.err.println("""
            Usage: pdf-client <file_name> <S3|NFS|URL> <location> [options]

            Arguments:
              file_name     Name of the PDF file
              storage_type  S3, NFS, or URL
              location      S3 URI (s3://bucket/prefix), NFS path, or base URL

            Options:
              --no-images   Skip image extraction
              --host h:p    Temporal server (default: 127.0.0.1:7233)

            Examples:
              pdf-client report.pdf  S3  s3://my-bucket/docs
              pdf-client spec.pdf    NFS /mnt/shared/pdfs
              pdf-client paper.pdf   URL https://files.example.com/in --no-images
            """);
    }
}
