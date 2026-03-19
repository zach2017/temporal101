package demo.temporal.worker;

import demo.temporal.activity.FileDetectionActivities;
import demo.temporal.activity.FileStorageActivities;
import demo.temporal.activity.OcrActivities;
import demo.temporal.activity.TextExtractionActivities;
import demo.temporal.model.*;
import demo.temporal.shared.TaskQueues;
import demo.temporal.workflow.FileProcessingWorkflow;
import demo.temporal.worker.workflow.FileProcessingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link FileProcessingWorkflowImpl}.
 *
 * <p>
 * Uses Temporal's {@link TestWorkflowEnvironment} — an in-memory Temporal
 * server. All Activities are mocked so we test only the Workflow orchestration
 * logic.</p>
 */
class FileProcessingWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    private FileDetectionActivities detectionActivities;
    private TextExtractionActivities textActivities;
    private OcrActivities ocrActivities;
    private FileStorageActivities storageActivities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TaskQueues.FILE_PROCESSING_TASK_QUEUE);
        client = testEnv.getWorkflowClient();

        worker.registerWorkflowImplementationTypes(FileProcessingWorkflowImpl.class);

        detectionActivities = mock(FileDetectionActivities.class, withSettings().withoutAnnotations());
        textActivities = mock(TextExtractionActivities.class, withSettings().withoutAnnotations());
        ocrActivities = mock(OcrActivities.class, withSettings().withoutAnnotations());
        storageActivities = mock(FileStorageActivities.class, withSettings().withoutAnnotations());

        worker.registerActivitiesImplementations(
                detectionActivities,
                textActivities,
                ocrActivities,
                storageActivities);

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private FileProcessingWorkflow createStub() {
        return client.newWorkflowStub(
                FileProcessingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-wf-" + System.nanoTime())
                        .setTaskQueue(TaskQueues.FILE_PROCESSING_TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                        .build());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 1: Plain-text file
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Plain text → extractPlainText → copy to output")
    void testPlainTextFile() {
        // Arrange
        when(storageActivities.createTmpDirectories(anyString(), eq("readme")))
                .thenReturn("/tmp/file-processor/readme");
        when(detectionActivities.detectMimeType("/data/inbox/readme.txt"))
                .thenReturn(new MimeDetectionResult("text/plain", DetectedFileType.PLAIN_TEXT));
        when(textActivities.extractPlainText(eq("/data/inbox/readme.txt"), anyString()))
                .thenReturn("/tmp/file-processor/readme/readme_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), eq("/data/outbox"), eq("readme_extracted.txt")))
                .thenReturn("/data/outbox/readme_extracted.txt");

        // Act
        var request = new FileProcessingRequest(
                "readme.txt", "/data/inbox/readme.txt", "/data/outbox", null);
        FileProcessingResult result = createStub().processFile(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("text/plain", result.getDetectedMimeType());
        assertEquals(DetectedFileType.PLAIN_TEXT, result.getDetectedFileType());
        assertEquals("/data/outbox/readme_extracted.txt", result.getTextOutputPath());
        assertNull(result.getErrorMessage());

        verify(textActivities).extractPlainText(eq("/data/inbox/readme.txt"), anyString());
        verify(ocrActivities, never()).ocrImage(anyString(), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 2: Image file → Tesseract OCR
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("JPEG image → OCR via Tesseract → copy to output")
    void testImageFile() {
        // Arrange
        when(storageActivities.createTmpDirectories(anyString(), eq("scan")))
                .thenReturn("/tmp/file-processor/scan");
        when(detectionActivities.detectMimeType("/data/inbox/scan.jpg"))
                .thenReturn(new MimeDetectionResult("image/jpeg", DetectedFileType.IMAGE));
        when(ocrActivities.ocrImage(eq("/data/inbox/scan.jpg"), anyString()))
                .thenReturn(new ExtractedImageInfo("scan.jpg", "/data/inbox/scan.jpg",
                        "/tmp/file-processor/scan/scan_extracted.txt", 2500));
        when(storageActivities.copyToOutput(anyString(), eq("/data/outbox"), eq("scan_extracted.txt")))
                .thenReturn("/data/outbox/scan_extracted.txt");

        // Act
        var request = new FileProcessingRequest(
                "scan.jpg", "/data/inbox/scan.jpg", "/data/outbox",
                Map.of("source", "scanner"));
        FileProcessingResult result = createStub().processFile(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(DetectedFileType.IMAGE, result.getDetectedFileType());
        assertEquals(1, result.getImageTextOutputs().size());
        assertEquals(2500, result.getImageTextOutputs().getFirst().getCharacterCount());

        verify(ocrActivities).ocrImage(eq("/data/inbox/scan.jpg"), anyString());
        verify(textActivities, never()).extractPdfText(anyString(), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 3: PDF → text extraction + image extraction + OCR + merge
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("PDF → extract text + images → OCR images → merge → output")
    void testPdfFile() {
        // Arrange
        when(storageActivities.createTmpDirectories(anyString(), eq("invoice")))
                .thenReturn("/tmp/file-processor/invoice");
        when(detectionActivities.detectMimeType("/data/inbox/invoice.pdf"))
                .thenReturn(new MimeDetectionResult("application/pdf", DetectedFileType.PDF));
        when(textActivities.extractPdfText(eq("/data/inbox/invoice.pdf"), anyString()))
                .thenReturn("/tmp/file-processor/invoice/invoice_pdf_text.txt");
        when(textActivities.extractPdfImages(eq("/data/inbox/invoice.pdf"), anyString()))
                .thenReturn(List.of(
                        "/tmp/file-processor/invoice/invoice_images/page_1_img_0.png",
                        "/tmp/file-processor/invoice/invoice_images/page_2_img_0.png"));
        when(ocrActivities.ocrImage(contains("page_1_img_0.png"), anyString()))
                .thenReturn(new ExtractedImageInfo("page_1_img_0.png",
                        "/tmp/file-processor/invoice/invoice_images/page_1_img_0.png",
                        "/tmp/file-processor/invoice/invoice_images/page_1_img_0.txt", 800));
        when(ocrActivities.ocrImage(contains("page_2_img_0.png"), anyString()))
                .thenReturn(new ExtractedImageInfo("page_2_img_0.png",
                        "/tmp/file-processor/invoice/invoice_images/page_2_img_0.png",
                        "/tmp/file-processor/invoice/invoice_images/page_2_img_0.txt", 450));
        when(storageActivities.mergeTextFiles(anyList(), anyString()))
                .thenReturn("/tmp/file-processor/invoice/invoice_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), eq("/data/outbox"), eq("invoice_extracted.txt")))
                .thenReturn("/data/outbox/invoice_extracted.txt");

        // Act
        var request = new FileProcessingRequest(
                "invoice.pdf", "/data/inbox/invoice.pdf", "/data/outbox",
                Map.of("department", "finance"));
        FileProcessingResult result = createStub().processFile(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(DetectedFileType.PDF, result.getDetectedFileType());
        assertEquals(2, result.getImageTextOutputs().size());
        assertEquals("/data/outbox/invoice_extracted.txt", result.getTextOutputPath());

        // Verify the full pipeline was called
        verify(textActivities).extractPdfText(eq("/data/inbox/invoice.pdf"), anyString());
        verify(textActivities).extractPdfImages(eq("/data/inbox/invoice.pdf"), anyString());
        verify(ocrActivities, times(2)).ocrImage(anyString(), anyString());
        verify(storageActivities).mergeTextFiles(anyList(), anyString());
        verify(storageActivities).copyToOutput(anyString(), anyString(), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 4: Word document → Office extraction
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("DOCX → extractOfficeText → copy to output")
    void testWordDocument() {
        when(storageActivities.createTmpDirectories(anyString(), eq("report")))
                .thenReturn("/tmp/file-processor/report");
        when(detectionActivities.detectMimeType("/data/inbox/report.docx"))
                .thenReturn(new MimeDetectionResult(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        DetectedFileType.WORD_DOCUMENT));
        when(textActivities.extractOfficeText(eq("/data/inbox/report.docx"), anyString()))
                .thenReturn("/tmp/file-processor/report/report_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), eq("/data/outbox"), eq("report_extracted.txt")))
                .thenReturn("/data/outbox/report_extracted.txt");

        var request = new FileProcessingRequest(
                "report.docx", "/data/inbox/report.docx", "/data/outbox", null);
        FileProcessingResult result = createStub().processFile(request);

        assertTrue(result.isSuccess());
        assertEquals(DetectedFileType.WORD_DOCUMENT, result.getDetectedFileType());
        verify(textActivities).extractOfficeText(eq("/data/inbox/report.docx"), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 5: Unsupported file type
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Unsupported MIME → returns failure result (no exception)")
    void testUnsupportedFileType() {
        when(storageActivities.createTmpDirectories(anyString(), eq("archive")))
                .thenReturn("/tmp/file-processor/archive");
        when(detectionActivities.detectMimeType("/data/inbox/archive.zip"))
                .thenReturn(new MimeDetectionResult("application/zip", DetectedFileType.UNSUPPORTED));

        var request = new FileProcessingRequest(
                "archive.zip", "/data/inbox/archive.zip", "/data/outbox", null);
        FileProcessingResult result = createStub().processFile(request);

        assertFalse(result.isSuccess());
        assertEquals(DetectedFileType.UNSUPPORTED, result.getDetectedFileType());
        assertTrue(result.getErrorMessage().contains("Unsupported"));

        verify(ocrActivities, never()).ocrImage(anyString(), anyString());
        verify(textActivities, never()).extractPdfText(anyString(), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 6: PDF with no embedded images
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("PDF with no images → text extraction only, no OCR")
    void testPdfNoImages() {
        when(storageActivities.createTmpDirectories(anyString(), eq("contract")))
                .thenReturn("/tmp/file-processor/contract");
        when(detectionActivities.detectMimeType("/data/inbox/contract.pdf"))
                .thenReturn(new MimeDetectionResult("application/pdf", DetectedFileType.PDF));
        when(textActivities.extractPdfText(eq("/data/inbox/contract.pdf"), anyString()))
                .thenReturn("/tmp/file-processor/contract/contract_pdf_text.txt");
        when(textActivities.extractPdfImages(eq("/data/inbox/contract.pdf"), anyString()))
                .thenReturn(List.of());  // No images found
        when(storageActivities.mergeTextFiles(anyList(), anyString()))
                .thenReturn("/tmp/file-processor/contract/contract_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), eq("/data/outbox"), eq("contract_extracted.txt")))
                .thenReturn("/data/outbox/contract_extracted.txt");

        var request = new FileProcessingRequest(
                "contract.pdf", "/data/inbox/contract.pdf", "/data/outbox", null);
        FileProcessingResult result = createStub().processFile(request);

        assertTrue(result.isSuccess());
        assertEquals(DetectedFileType.PDF, result.getDetectedFileType());
        assertTrue(result.getImageTextOutputs().isEmpty());

        verify(ocrActivities, never()).ocrImage(anyString(), anyString());
        verify(storageActivities).mergeTextFiles(argThat(list -> list.size() == 1), anyString());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 7: Query status
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("getStatus() query returns current pipeline stage")
    void testQueryStatus() {
        // Use a slow mock to give us time to query
        when(storageActivities.createTmpDirectories(anyString(), anyString()))
                .thenReturn("/tmp/file-processor/slow");
        when(detectionActivities.detectMimeType(anyString()))
                .thenAnswer(inv -> {
                    Thread.sleep(200);
                    return new MimeDetectionResult("text/plain", DetectedFileType.PLAIN_TEXT);
                });
        when(textActivities.extractPlainText(anyString(), anyString()))
                .thenReturn("/tmp/file-processor/slow/slow_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), anyString(), anyString()))
                .thenReturn("/data/outbox/slow_extracted.txt");

        FileProcessingWorkflow workflow = createStub();

        // Start async
        WorkflowClient.start(workflow::processFile,
                new FileProcessingRequest("slow.txt", "/data/inbox/slow.txt", "/data/outbox", null));

        // Query immediately — should be in early stage
        String status = workflow.getStatus();
        assertNotNull(status);
        // Status will be one of: INITIALISING, CREATING_TMP_DIRS, DETECTING_MIME, etc.
    }

    // ═════════════════════════════════════════════════════════════════
    //  Test 8: Metadata pass-through
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("Metadata from request is preserved in result")
    void testMetadataPassthrough() {
        when(storageActivities.createTmpDirectories(anyString(), eq("doc")))
                .thenReturn("/tmp/file-processor/doc");
        when(detectionActivities.detectMimeType("/data/inbox/doc.txt"))
                .thenReturn(new MimeDetectionResult("text/plain", DetectedFileType.PLAIN_TEXT));
        when(textActivities.extractPlainText(anyString(), anyString()))
                .thenReturn("/tmp/file-processor/doc/doc_extracted.txt");
        when(storageActivities.copyToOutput(anyString(), anyString(), anyString()))
                .thenReturn("/data/outbox/doc_extracted.txt");

        Map<String, String> metadata = Map.of(
                "uploadedBy", "jsmith",
                "department", "engineering",
                "priority", "high");

        var request = new FileProcessingRequest(
                "doc.txt", "/data/inbox/doc.txt", "/data/outbox", metadata);
        FileProcessingResult result = createStub().processFile(request);

        assertTrue(result.isSuccess());
        assertEquals(metadata, result.getMetadata());
    }
}
