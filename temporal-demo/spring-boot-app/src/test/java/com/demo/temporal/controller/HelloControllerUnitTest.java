package com.demo.temporal.controller;

import com.demo.temporal.shared.JavaHelloWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.api.common.v1.WorkflowExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HelloController.
 *
 * These tests mock the Temporal WorkflowClient so no Temporal Server
 * is needed. They verify the controller's request handling, response
 * structure, and error handling logic in isolation.
 *
 * Test Flow:
 *   1. Mock WorkflowClient returns stubbed workflow proxies
 *   2. Controller calls sayHello() which invokes both stubs
 *   3. Assertions verify the response map structure and values
 */
@ExtendWith(MockitoExtension.class)
class HelloControllerUnitTest {

    @Mock
    private WorkflowClient workflowClient;

    @Mock
    private JavaHelloWorkflow javaWorkflowStub;

    @Mock
    private WorkflowStub pythonWorkflowStub;

    private HelloController controller;

    @BeforeEach
    void setUp() {
        controller = new HelloController(workflowClient);
    }

    // ────────────────────────────────────────────────────────────
    // Happy Path
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/hello - both workers succeed")
    void sayHello_bothWorkersSucceed() {
        // Arrange: Java worker
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaWorkflowStub);
        when(javaWorkflowStub.sayHello("Alice"))
                .thenReturn("Hello Alice from Java Worker!");

        // Arrange: Python worker
        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonWorkflowStub);
        WorkflowExecution execution = WorkflowExecution.newBuilder()
                .setWorkflowId("python-hello-test-123")
                .setRunId("run-abc")
                .build();
        when(pythonWorkflowStub.start(any())).thenReturn(execution);
        when(pythonWorkflowStub.getResult(String.class))
                .thenReturn("Hello Alice from Python Worker!");

        // Act
        Map<String, Object> response = controller.sayHello(Map.of("name", "Alice"));

        // Assert: top-level structure
        assertNotNull(response);
        assertTrue(response.containsKey("javaWorker"));
        assertTrue(response.containsKey("pythonWorker"));
        assertTrue(response.containsKey("temporalUi"));

        // Assert: Java worker response
        @SuppressWarnings("unchecked")
        Map<String, Object> javaResult = (Map<String, Object>) response.get("javaWorker");
        assertEquals("SUCCESS", javaResult.get("status"));
        assertEquals("Hello Alice from Java Worker!", javaResult.get("message"));
        assertEquals("java-hello-queue", javaResult.get("taskQueue"));

        // Assert: Python worker response
        @SuppressWarnings("unchecked")
        Map<String, Object> pythonResult = (Map<String, Object>) response.get("pythonWorker");
        assertEquals("SUCCESS", pythonResult.get("status"));
        assertEquals("Hello Alice from Python Worker!", pythonResult.get("message"));
        assertEquals("python-hello-queue", pythonResult.get("taskQueue"));
    }

    @Test
    @DisplayName("POST /api/hello - uses default name 'World' when not provided")
    void sayHello_defaultName() {
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaWorkflowStub);
        when(javaWorkflowStub.sayHello("World"))
                .thenReturn("Hello World from Java Worker!");

        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonWorkflowStub);
        WorkflowExecution execution = WorkflowExecution.newBuilder()
                .setWorkflowId("python-hello-default")
                .setRunId("run-def")
                .build();
        when(pythonWorkflowStub.start(any())).thenReturn(execution);
        when(pythonWorkflowStub.getResult(String.class))
                .thenReturn("Hello World from Python Worker!");

        // Act: empty map (no "name" key)
        Map<String, Object> response = controller.sayHello(Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> javaResult = (Map<String, Object>) response.get("javaWorker");
        assertEquals("SUCCESS", javaResult.get("status"));
        assertTrue(((String) javaResult.get("message")).contains("World"));
    }

    // ────────────────────────────────────────────────────────────
    // Error Handling
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/hello - Java worker fails, Python succeeds")
    void sayHello_javaWorkerFails() {
        // Java worker throws exception
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaWorkflowStub);
        when(javaWorkflowStub.sayHello(anyString()))
                .thenThrow(new RuntimeException("Java worker connection timeout"));

        // Python worker succeeds
        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonWorkflowStub);
        WorkflowExecution execution = WorkflowExecution.newBuilder()
                .setWorkflowId("python-hello-ok")
                .setRunId("run-ok")
                .build();
        when(pythonWorkflowStub.start(any())).thenReturn(execution);
        when(pythonWorkflowStub.getResult(String.class))
                .thenReturn("Hello World from Python Worker!");

        Map<String, Object> response = controller.sayHello(Map.of("name", "World"));

        // Java should be ERROR
        @SuppressWarnings("unchecked")
        Map<String, Object> javaResult = (Map<String, Object>) response.get("javaWorker");
        assertEquals("ERROR", javaResult.get("status"));
        assertTrue(((String) javaResult.get("message")).contains("timeout"));

        // Python should be SUCCESS
        @SuppressWarnings("unchecked")
        Map<String, Object> pythonResult = (Map<String, Object>) response.get("pythonWorker");
        assertEquals("SUCCESS", pythonResult.get("status"));
    }

    @Test
    @DisplayName("POST /api/hello - both workers fail gracefully")
    void sayHello_bothWorkersFail() {
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaWorkflowStub);
        when(javaWorkflowStub.sayHello(anyString()))
                .thenThrow(new RuntimeException("Java error"));

        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonWorkflowStub);
        when(pythonWorkflowStub.start(any()))
                .thenThrow(new RuntimeException("Python error"));

        Map<String, Object> response = controller.sayHello(Map.of("name", "Test"));

        // Both should be ERROR but response should still be well-formed
        assertNotNull(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> javaResult = (Map<String, Object>) response.get("javaWorker");
        assertEquals("ERROR", javaResult.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pythonResult = (Map<String, Object>) response.get("pythonWorker");
        assertEquals("ERROR", pythonResult.get("status"));

        // temporalUi link should still be present
        assertEquals("http://localhost:8080", response.get("temporalUi"));
    }

    // ────────────────────────────────────────────────────────────
    // Health Endpoint
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/health - returns UP status")
    void health_returnsUp() {
        Map<String, String> result = controller.health();

        assertEquals("UP", result.get("status"));
        assertEquals("temporal-spring-app", result.get("service"));
    }
}
