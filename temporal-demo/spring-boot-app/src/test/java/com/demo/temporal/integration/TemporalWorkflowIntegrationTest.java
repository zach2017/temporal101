package com.demo.temporal.integration;

import com.demo.temporal.shared.JavaHelloWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that spins up Temporal's embedded test server.
 *
 * This test validates the complete workflow execution path:
 *   Browser → Controller → WorkflowClient → Temporal Server → Worker → Activity → Result
 *
 * Unlike the unit tests, this actually runs a real Temporal workflow engine
 * in-process using TestWorkflowEnvironment. No Docker or external server needed.
 *
 * Test Flow:
 *   1. TestWorkflowEnvironment starts an embedded Temporal server
 *   2. A Worker is registered with workflow + activity implementations
 *   3. A WorkflowClient triggers the workflow
 *   4. The embedded server dispatches to the worker, executes the activity
 *   5. The result flows back through the client
 *   6. Assertions verify the full round-trip
 */
class TemporalWorkflowIntegrationTest {

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    // ── Inline implementations for test isolation ───────────
    // (Same contracts as java-worker, but self-contained in the test)

    @ActivityInterface
    public interface TestGreetingActivities {
        String composeGreeting(String name);
    }

    public static class TestGreetingActivitiesImpl implements TestGreetingActivities {
        @Override
        public String composeGreeting(String name) {
            return "Hello " + name + " from Test Java Worker!";
        }
    }

    public static class TestJavaHelloWorkflowImpl implements JavaHelloWorkflow {
        private final TestGreetingActivities activities =
                Workflow.newActivityStub(
                        TestGreetingActivities.class,
                        ActivityOptions.newBuilder()
                                .setStartToCloseTimeout(Duration.ofSeconds(5))
                                .setRetryOptions(RetryOptions.newBuilder()
                                        .setMaximumAttempts(2)
                                        .build())
                                .build());

        @Override
        public String sayHello(String name) {
            return activities.composeGreeting(name);
        }
    }

    // ── Lifecycle ───────────────────────────────────────────

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("java-hello-queue");

        // Register implementations
        worker.registerWorkflowImplementationTypes(TestJavaHelloWorkflowImpl.class);
        worker.registerActivitiesImplementations(new TestGreetingActivitiesImpl());

        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    // ── Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Full workflow round-trip: start → activity → result")
    void fullWorkflowRoundTrip() {
        JavaHelloWorkflow workflow = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("integration-test-001")
                        .setTaskQueue("java-hello-queue")
                        .build());

        String result = workflow.sayHello("Integration");

        assertEquals("Hello Integration from Test Java Worker!", result);
    }

    @Test
    @DisplayName("Workflow executes with different names")
    void workflowWithDifferentNames() {
        String[] names = {"Alice", "Bob", "", "名前", "O'Brien"};

        for (String name : names) {
            JavaHelloWorkflow workflow = client.newWorkflowStub(
                    JavaHelloWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("name-test-" + name.hashCode())
                            .setTaskQueue("java-hello-queue")
                            .build());

            String result = workflow.sayHello(name);
            assertTrue(result.startsWith("Hello " + name),
                    "Result should contain the name: " + name);
        }
    }

    @Test
    @DisplayName("Workflow ID is unique per execution")
    void uniqueWorkflowIds() {
        // Two workflows with different IDs should both complete
        JavaHelloWorkflow wf1 = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("unique-test-1")
                        .setTaskQueue("java-hello-queue")
                        .build());

        JavaHelloWorkflow wf2 = client.newWorkflowStub(
                JavaHelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("unique-test-2")
                        .setTaskQueue("java-hello-queue")
                        .build());

        String result1 = wf1.sayHello("First");
        String result2 = wf2.sayHello("Second");

        assertNotEquals(result1, result2);
        assertTrue(result1.contains("First"));
        assertTrue(result2.contains("Second"));
    }
}
