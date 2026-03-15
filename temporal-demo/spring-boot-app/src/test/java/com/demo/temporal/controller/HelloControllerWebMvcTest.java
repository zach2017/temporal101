package com.demo.temporal.controller;

import com.demo.temporal.shared.JavaHelloWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring WebMvc slice test for HelloController.
 *
 * Loads only the web layer (not the full application context), mocking the
 * WorkflowClient bean. Validates HTTP status codes, content types, and JSON
 * response structure.
 *
 * Test Flow: 1. Spring loads only @RestController + WebMvc auto-config 2.
 * WorkflowClient is a @MockBean (no real Temporal connection) 3. MockMvc sends
 * HTTP requests and asserts responses
 */
@WebMvcTest(HelloController.class)
class HelloControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowClient workflowClient;

    @Test
    @DisplayName("POST /api/hello - returns 200 with JSON response")
    void postHello_returns200() throws Exception {
        // Arrange: set up mocks for both workflow stubs
        JavaHelloWorkflow javaStub = mock(JavaHelloWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaStub);
        when(javaStub.sayHello(anyString())).thenReturn("Hello Test from Java!");

        WorkflowStub pythonStub = mock(WorkflowStub.class);
        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonStub);
        WorkflowExecution exec = WorkflowExecution.newBuilder()
                .setWorkflowId("py-123").setRunId("run-1").build();
        when(pythonStub.start(any())).thenReturn(exec);
        when(pythonStub.getResult(String.class)).thenReturn("Hello Test from Python!");

        // Act & Assert
        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.javaWorker.status").value("SUCCESS"))
                .andExpect(jsonPath("$.javaWorker.message").value("Hello Test from Java!"))
                .andExpect(jsonPath("$.javaWorker.taskQueue").value("java-hello-queue"))
                .andExpect(jsonPath("$.pythonWorker.status").value("SUCCESS"))
                .andExpect(jsonPath("$.pythonWorker.message").value("Hello Test from Python!"))
                .andExpect(jsonPath("$.pythonWorker.taskQueue").value("python-hello-queue"))
                .andExpect(jsonPath("$.temporalUi").value("http://localhost:8080"));
    }

    @Test
    @DisplayName("POST /api/hello - worker errors return ERROR in JSON, not HTTP 500")
    void postHello_workerError_stillReturns200() throws Exception {
        // Both workers throw, but controller catches and returns structured error
        JavaHelloWorkflow javaStub = mock(JavaHelloWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(JavaHelloWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(javaStub);
        when(javaStub.sayHello(anyString())).thenThrow(new RuntimeException("connection refused"));

        WorkflowStub pythonStub = mock(WorkflowStub.class);
        when(workflowClient.newUntypedWorkflowStub(eq("PythonHelloWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(pythonStub);
        when(pythonStub.start(any())).thenThrow(new RuntimeException("timeout"));

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Fail\"}"))
                .andExpect(status().isOk()) // Controller handles errors gracefully
                .andExpect(jsonPath("$.javaWorker.status").value("ERROR"))
                .andExpect(jsonPath("$.pythonWorker.status").value("ERROR"))
                .andExpect(jsonPath("$.temporalUi").value("http://localhost:8080"));
    }

    @Test
    @DisplayName("GET /api/health - returns 200 with UP status")
    void getHealth_returns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("temporal-spring-app"));
    }

    @Test
    @DisplayName("POST /api/hello - missing body returns 400")
    void postHello_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/hello - wrong method returns 405")
    void getHello_returns405() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isMethodNotAllowed());
    }
}
