package com.demo.temporal.javaworker.workflow;

import com.demo.temporal.javaworker.activity.GreetingActivities;
import com.demo.temporal.javaworker.activity.GreetingActivitiesImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Workflow tests using Temporal's TestWorkflowEnvironment.
 *
 * TestWorkflowEnvironment provides an embedded, in-process Temporal server
 * that supports full workflow execution including activity dispatch, retries,
 * and timeouts — without needing Docker or an external server.
 *
 * Two test approaches are demonstrated:
 *   1. Real activity: Full end-to-end with actual GreetingActivitiesImpl
 *   2. Mocked activity: Isolated workflow logic with Mockito-stubbed activities
 *
 * Test Flow (real activity):
 *   1. TestWorkflowEnvironment starts embedded Temporal
 *   2. Worker registers workflow + real activity impl
 *   3. Client starts workflow → dispatched to worker → executes activity
 *   4. Result validated
 *
 * Test Flow (mocked activity):
 *   1. Same setup, but activity is a Mockito mock
 *   2. Workflow logic executes, calls mock instead of real impl
 *   3. Verifies workflow orchestration independent of activity code
 */
class JavaHelloWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    private static final String TASK_QUEUE = "java-hello-queue-test";

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(JavaHelloWorkflowImpl.class);
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    // ────────────────────────────────────────────────────────
    // Tests with REAL activity implementation
    // ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Workflow completes with real activity")
    void workflow_realActivity_completes() {
        worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-real-001")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = workflow.sayHello("World");

        assertNotNull(result);
        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("Java Worker"));
        assertTrue(result.contains("temporal-sdk=1.32.0"));
    }

    @Test
    @DisplayName("Workflow output contains input name")
    void workflow_realActivity_containsName() {
        worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-real-002")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = workflow.sayHello("TemporalUser");
        assertTrue(result.contains("TemporalUser"));
    }

    // ────────────────────────────────────────────────────────
    // Tests with MOCKED activity (isolate workflow logic)
    // ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Workflow delegates to activity correctly (mocked)")
    void workflow_mockedActivity_delegates() {
        GreetingActivities mockActivities = mock(GreetingActivities.class);
        when(mockActivities.composeGreeting("MockUser"))
                .thenReturn("Mocked greeting for MockUser");

        worker.registerActivitiesImplementations(mockActivities);
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-mock-001")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = workflow.sayHello("MockUser");

        assertEquals("Mocked greeting for MockUser", result);
        verify(mockActivities).composeGreeting("MockUser");
    }

    @Test
    @DisplayName("Workflow handles activity returning empty string")
    void workflow_mockedActivity_emptyResult() {
        GreetingActivities mockActivities = mock(GreetingActivities.class);
        when(mockActivities.composeGreeting(anyString())).thenReturn("");

        worker.registerActivitiesImplementations(mockActivities);
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-mock-002")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = workflow.sayHello("Test");
        assertEquals("", result);
    }

    @Test
    @DisplayName("Workflow retries on activity failure up to 3 attempts")
    void workflow_mockedActivity_retriesOnFailure() {
        GreetingActivities mockActivities = mock(GreetingActivities.class);
        when(mockActivities.composeGreeting("RetryUser"))
                .thenThrow(new RuntimeException("Transient error"))       // 1st attempt fails
                .thenThrow(new RuntimeException("Transient error again")) // 2nd attempt fails
                .thenReturn("Success after retries!");                     // 3rd attempt succeeds

        worker.registerActivitiesImplementations(mockActivities);
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-retry-001")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = workflow.sayHello("RetryUser");

        assertEquals("Success after retries!", result);
        // Activity was called 3 times (2 failures + 1 success)
        verify(mockActivities, times(3)).composeGreeting("RetryUser");
    }

    @Test
    @DisplayName("Workflow fails after exhausting all retry attempts")
    void workflow_mockedActivity_failsAfterMaxRetries() {
        GreetingActivities mockActivities = mock(GreetingActivities.class);
        when(mockActivities.composeGreeting(anyString()))
                .thenThrow(new RuntimeException("Persistent failure"));

        worker.registerActivitiesImplementations(mockActivities);
        testEnv.start();

        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("test-exhaust-001")
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        // Workflow should fail after 3 attempts (maxAttempts=3 in workflow impl)
        assertThrows(WorkflowFailedException.class, () -> workflow.sayHello("FailUser"));
        // Activity should have been called exactly 3 times
        verify(mockActivities, times(3)).composeGreeting("FailUser");
    }
}
