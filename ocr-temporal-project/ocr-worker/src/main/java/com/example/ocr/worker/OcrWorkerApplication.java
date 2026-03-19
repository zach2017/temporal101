package com.example.ocr.worker;

import com.example.ocr.common.constants.OcrConstants;
import com.example.ocr.worker.activity.OcrActivitiesImpl;
import com.example.ocr.worker.resolver.FileResolver;
import com.example.ocr.worker.resolver.LocalFileResolver;
import com.example.ocr.worker.resolver.S3FileResolver;
import com.example.ocr.worker.resolver.UrlFileResolver;
import com.example.ocr.worker.service.TesseractOcrService;
import com.example.ocr.worker.workflow.OcrWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main application class that bootstraps the Temporal OCR Worker.
 *
 * <p>Initializes all dependencies, registers workflows and activities,
 * and starts the worker listening on the OCR task queue.
 *
 * <h3>Configuration via environment variables:</h3>
 * <ul>
 *   <li>{@code TEMPORAL_ADDRESS} - Temporal server address (default: localhost:7233)</li>
 *   <li>{@code TESSDATA_PATH} - Path to tessdata directory (default: /usr/share/tessdata)</li>
 *   <li>{@code OCR_TEMP_DIR} - Temporary file directory (default: /tmp/ocr-worker)</li>
 *   <li>{@code AWS_REGION} - AWS region for S3 (default: us-east-1)</li>
 *   <li>{@code OCR_MIN_CONFIDENCE} - Minimum confidence threshold (default: 15.0)</li>
 *   <li>{@code OCR_TASK_QUEUE} - Temporal task queue name (default: OCR_TASK_QUEUE)</li>
 * </ul>
 */
public class OcrWorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(OcrWorkerApplication.class);

    public static void main(String[] args) {
        log.info("====================================");
        log.info("  OCR Temporal Worker Starting...");
        log.info("====================================");

        // Read configuration from environment
        String temporalAddress = getEnv("TEMPORAL_ADDRESS", "localhost:7233");
        String tessdataPath = getEnv("TESSDATA_PATH", OcrConstants.DEFAULT_TESSDATA_PATH);
        String tempDirPath = getEnv("OCR_TEMP_DIR", OcrConstants.DEFAULT_TEMP_DIR);
        String awsRegion = getEnv("AWS_REGION", "us-east-1");
        float minConfidence = Float.parseFloat(getEnv("OCR_MIN_CONFIDENCE",
                String.valueOf(OcrConstants.DEFAULT_MIN_CONFIDENCE)));
        String taskQueue = getEnv("OCR_TASK_QUEUE", OcrConstants.TASK_QUEUE);

        // Create temp directory
        Path tempDir = Paths.get(tempDirPath);
        try {
            Files.createDirectories(tempDir);
        } catch (Exception e) {
            log.error("Failed to create temp directory: {}", tempDirPath, e);
            System.exit(1);
        }

        // Initialize OCR service and log available languages
        TesseractOcrService ocrService = new TesseractOcrService(tessdataPath, minConfidence);
        List<String> availableLanguages = ocrService.getAvailableLanguages();
        log.info("Available languages ({}): {}", availableLanguages.size(), availableLanguages);

        // Initialize file resolvers
        S3Client s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .build();

        S3FileResolver s3Resolver = new S3FileResolver(s3Client, tempDir);
        LocalFileResolver localResolver = new LocalFileResolver();
        UrlFileResolver urlResolver = new UrlFileResolver(tempDir);
        FileResolver fileResolver = new FileResolver(s3Resolver, localResolver, urlResolver);

        // Create activity implementation
        OcrActivitiesImpl activities = new OcrActivitiesImpl(fileResolver, ocrService);

        // Connect to Temporal
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalAddress)
                        .build());

        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        // Create worker and register workflow + activities
        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(OcrWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);

        // Start the worker
        factory.start();

        log.info("====================================");
        log.info("  OCR Worker RUNNING");
        log.info("  Task Queue: {}", taskQueue);
        log.info("  Temporal:   {}", temporalAddress);
        log.info("  Tessdata:   {}", tessdataPath);
        log.info("  Temp Dir:   {}", tempDirPath);
        log.info("  Languages:  {}", availableLanguages.size());
        log.info("====================================");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OCR Worker...");
            factory.shutdown();
            s3Client.close();
            service.shutdown();
            log.info("OCR Worker stopped.");
        }));
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
