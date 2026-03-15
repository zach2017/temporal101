# Testing Guide — Temporal Multi-Language Demo

This document covers every test in the project: what it tests, how it works,
how data flows through the system, and how to run everything.

---

## Table of Contents

1. [System Architecture & Data Flow](#1-system-architecture--data-flow)
2. [Test Pyramid Overview](#2-test-pyramid-overview)
3. [Spring Boot App Tests](#3-spring-boot-app-tests)
4. [Java Worker Tests](#4-java-worker-tests)
5. [Python Worker Tests](#5-python-worker-tests)
6. [End-to-End Integration Tests](#6-end-to-end-integration-tests)
7. [Running All Tests](#7-running-all-tests)
8. [How Temporal Workflow Execution Works](#8-how-temporal-workflow-execution-works)
9. [Test Infrastructure Deep Dive](#9-test-infrastructure-deep-dive)

---

## 1. System Architecture & Data Flow

### Complete Request Flow

When a user clicks "Send Hello" in the browser, this is exactly what happens:

```
Step 1:  Browser sends POST /api/hello { "name": "Alice" }
              │
Step 2:  Spring Boot HelloController receives the request
              │
              ├── Step 3a: Creates a TYPED workflow stub for Java
              │             WorkflowClient.newWorkflowStub(JavaHelloWorkflow.class)
              │             with taskQueue = "java-hello-queue"
              │
              │   Step 4a: Calls javaWorkflow.sayHello("Alice")
              │             → This is a BLOCKING call via gRPC to Temporal Server
              │
              │   Step 5a: Temporal Server persists WorkflowExecutionStarted event
              │             in PostgreSQL, then puts a WorkflowTask on "java-hello-queue"
              │
              │   Step 6a: Java Worker (long-polling "java-hello-queue") picks up the task
              │             → Executes JavaHelloWorkflowImpl.sayHello("Alice")
              │             → Workflow creates ActivityStub and calls composeGreeting("Alice")
              │
              │   Step 7a: Temporal Server records ActivityTaskScheduled event,
              │             dispatches activity task to Java Worker
              │
              │   Step 8a: Java Worker executes GreetingActivitiesImpl.composeGreeting("Alice")
              │             → Returns "Hello Alice from Java Worker! [host=..., jdk=21, ...]"
              │
              │   Step 9a: Temporal Server records ActivityTaskCompleted,
              │             then WorkflowExecutionCompleted events
              │
              │   Step 10a: Result flows back to the blocking sayHello() call
              │
              ├── Step 3b: Creates an UNTYPED workflow stub for Python
              │             WorkflowClient.newUntypedWorkflowStub("PythonHelloWorkflow")
              │             with taskQueue = "python-hello-queue"
              │
              │   Step 4b: Calls pythonWorkflow.start("Alice") + getResult()
              │             → start() is async; getResult() blocks until completion
              │
              │   Steps 5b-9b: Same flow as Java, but dispatched to Python Worker
              │                 on "python-hello-queue"
              │
              │   Step 10b: Python Worker runs PythonHelloWorkflow → compose_greeting()
              │             → Returns "Hello Alice from Python Worker! [host=..., python=3.13, ...]"
              │
Step 11: Controller assembles JSON response with both results
              │
Step 12: Browser receives and displays the response
```

### Temporal Event History (per workflow)

Each workflow execution generates this exact sequence of events,
all persisted in PostgreSQL:

```
 #   Event Type                  Payload / Notes
 1   WorkflowExecutionStarted   input="Alice", taskQueue="java-hello-queue"
 2   WorkflowTaskScheduled      (server internal)
 3   WorkflowTaskStarted        worker picks up workflow task
 4   WorkflowTaskCompleted      workflow code runs, schedules activity
 5   ActivityTaskScheduled       activityType="composeGreeting", input="Alice"
 6   ActivityTaskStarted        worker picks up activity task
 7   ActivityTaskCompleted       result="Hello Alice from Java Worker!..."
 8   WorkflowTaskScheduled      (server processes activity completion)
 9   WorkflowTaskStarted        worker picks up follow-up task
10   WorkflowTaskCompleted      workflow returns the result
11   WorkflowExecutionCompleted result="Hello Alice from Java Worker!..."
```

You can see this in the Temporal Web UI at http://localhost:8080 after running
a workflow. Each event has a timestamp, the full input/output payload, and
the worker identity that processed it.

---

## 2. Test Pyramid Overview

```
                    ┌──────────────────┐
                    │   End-to-End     │  1 test script
                    │   (Docker)       │  Full stack in containers
                    ├──────────────────┤
                    │   Integration    │  3 test classes
                    │   (Embedded      │  Temporal TestWorkflowEnvironment
                    │    Temporal)     │  (in-process server, no Docker)
                    ├──────────────────┤
                    │   Unit Tests     │  4 test classes
                    │   (Mocks only)   │  No Temporal server at all
                    └──────────────────┘
```

| Layer         | Server Needed? | What It Validates                          | Speed     |
|---------------|----------------|--------------------------------------------|-----------|
| Unit          | No             | Individual methods, error handling, mocks   | < 1s each |
| Integration   | Embedded       | Workflow→Activity round-trip, retries       | 2-5s each |
| End-to-End    | Full Docker    | All 6 containers, HTTP, gRPC, cross-lang    | ~60s      |

---

## 3. Spring Boot App Tests

### 3a. HelloControllerUnitTest

**File:** `spring-boot-app/src/test/java/.../controller/HelloControllerUnitTest.java`

**Purpose:** Test the controller's request handling logic in complete isolation.
No Spring context, no Temporal server — just the controller class with mocked dependencies.

**How it works:**

```
 Test Setup:
   @Mock WorkflowClient         ← Mockito creates a fake WorkflowClient
   @Mock JavaHelloWorkflow       ← Fake typed workflow stub
   @Mock WorkflowStub            ← Fake untyped workflow stub (for Python)
   controller = new HelloController(mockWorkflowClient)

 Test Execution:
   1. Configure mocks: when(javaStub.sayHello("Alice")).thenReturn("Hello Alice...")
   2. Call controller.sayHello(Map.of("name", "Alice"))
   3. Assert response map structure: javaWorker.status == "SUCCESS", etc.
```

**Test cases:**

| Test | What It Validates |
|------|-------------------|
| `sayHello_bothWorkersSucceed` | Happy path — both workers return results; response has correct structure with status, message, taskQueue for each worker plus temporalUi link |
| `sayHello_defaultName` | When request body has no "name" key, defaults to "World" |
| `sayHello_javaWorkerFails` | Java throws exception, Python succeeds — response has ERROR for Java, SUCCESS for Python; no HTTP 500 |
| `sayHello_bothWorkersFail` | Both throw — response still well-formed with ERROR status for both; temporalUi link still present |
| `health_returnsUp` | Health endpoint returns `{"status":"UP","service":"temporal-spring-app"}` |


### 3b. HelloControllerWebMvcTest

**File:** `spring-boot-app/src/test/java/.../controller/HelloControllerWebMvcTest.java`

**Purpose:** Test the HTTP layer (routing, content types, status codes) using Spring's
MockMvc. Loads only the web slice (@WebMvcTest), not the full app context.

**How it works:**

```
 Test Setup:
   @WebMvcTest(HelloController.class) ← Spring loads only web layer
   @MockBean WorkflowClient           ← Spring injects mock into context
   @Autowired MockMvc                  ← HTTP test client

 Test Execution:
   1. Configure @MockBean behavior
   2. mockMvc.perform(post("/api/hello").content(...))
   3. Assert: status().isOk(), jsonPath("$.javaWorker.status").value("SUCCESS")
```

**Test cases:**

| Test | What It Validates |
|------|-------------------|
| `postHello_returns200` | POST with valid JSON body → HTTP 200 + correct JSON structure |
| `postHello_workerError_stillReturns200` | Worker exceptions → still HTTP 200 (not 500); error in JSON body |
| `getHealth_returns200` | GET /api/health → HTTP 200 with UP status |
| `postHello_missingBody_returns400` | POST without body → HTTP 400 (Spring's built-in validation) |
| `getHello_returns405` | GET on POST-only endpoint → HTTP 405 Method Not Allowed |


### 3c. TemporalWorkflowIntegrationTest

**File:** `spring-boot-app/src/test/java/.../integration/TemporalWorkflowIntegrationTest.java`

**Purpose:** Full Temporal workflow round-trip using the embedded test server.
No Docker needed — TestWorkflowEnvironment runs Temporal in-process.

**How it works:**

```
 Test Setup:
   TestWorkflowEnvironment.newInstance()  ← Starts embedded Temporal server
   worker = testEnv.newWorker("java-hello-queue")
   worker.registerWorkflowImplementationTypes(...)
   worker.registerActivitiesImplementations(...)
   testEnv.start()

 Test Execution:
   1. Create workflow stub via testEnv.getWorkflowClient()
   2. Call workflow.sayHello("Alice")
   3. Embedded server dispatches to registered worker
   4. Worker executes workflow → schedules activity → executes activity
   5. Result flows back through the client
   6. Assert result string content

 Teardown:
   testEnv.close()  ← Stops embedded server
```

**Test cases:**

| Test | What It Validates |
|------|-------------------|
| `fullWorkflowRoundTrip` | Client → Server → Worker → Activity → Result completes successfully |
| `workflowWithDifferentNames` | Various inputs including empty, unicode, special chars all work |
| `uniqueWorkflowIds` | Two concurrent workflows with different IDs both complete independently |

---

## 4. Java Worker Tests

### 4a. GreetingActivitiesTest

**File:** `java-worker/src/test/java/.../activity/GreetingActivitiesTest.java`

**Purpose:** Unit test the activity implementation as a plain Java class.
No Temporal infrastructure at all — just instantiate and call.

**How it works:**

```
 Test Setup:
   activities = new GreetingActivitiesImpl()  ← Plain constructor, no DI

 Test Execution:
   String result = activities.composeGreeting("Alice")
   assertTrue(result.contains("Hello Alice"))
```

**Test cases:**

| Test | What It Validates |
|------|-------------------|
| `composeGreeting_containsName` | Output includes the input name |
| `composeGreeting_containsJdkVersion` | Output includes current JDK version |
| `composeGreeting_containsSdkVersion` | Output includes "temporal-sdk=1.32.0" |
| `composeGreeting_containsTimestamp` | Output includes ISO timestamp |
| `composeGreeting_containsHostname` | Output includes a non-empty hostname |
| `composeGreeting_emptyName` | Empty string input handled gracefully |
| `composeGreeting_specialCharacters` | Special chars preserved in output |
| `composeGreeting_nullName` | Null input produces "Hello null" (no NPE) |


### 4b. JavaHelloWorkflowTest

**File:** `java-worker/src/test/java/.../workflow/JavaHelloWorkflowTest.java`

**Purpose:** Test the workflow using Temporal's TestWorkflowEnvironment.
Two approaches demonstrated — real activities and mocked activities.

**How it works (real activity):**

```
 Setup:
   worker.registerWorkflowImplementationTypes(JavaHelloWorkflowImpl.class)
   worker.registerActivitiesImplementations(new GreetingActivitiesImpl())
   testEnv.start()

 Execution:
   workflow.sayHello("World")
   → Embedded server dispatches to worker
   → Workflow creates activity stub, calls composeGreeting()
   → Activity executes with real GreetingActivitiesImpl
   → Result returned
```

**How it works (mocked activity):**

```
 Setup:
   GreetingActivities mockActivities = mock(GreetingActivities.class)
   when(mockActivities.composeGreeting("MockUser")).thenReturn("Mocked greeting")
   worker.registerActivitiesImplementations(mockActivities)
   testEnv.start()

 Execution:
   workflow.sayHello("MockUser")
   → Workflow calls activity stub → Mockito intercepts → Returns "Mocked greeting"
   → verify(mockActivities).composeGreeting("MockUser")
```

**How retry testing works:**

```
 Setup:
   when(mockActivities.composeGreeting("RetryUser"))
       .thenThrow(RuntimeException)  ← 1st attempt fails
       .thenThrow(RuntimeException)  ← 2nd attempt fails
       .thenReturn("Success!")        ← 3rd attempt succeeds

 Execution:
   workflow.sayHello("RetryUser")
   → Activity fails twice, Temporal retries automatically
   → 3rd attempt succeeds
   → verify(mock, times(3)).composeGreeting("RetryUser")
```

**Test cases:**

| Test | What It Validates |
|------|-------------------|
| `workflow_realActivity_completes` | End-to-end with real activity |
| `workflow_realActivity_containsName` | Output includes input name |
| `workflow_mockedActivity_delegates` | Workflow correctly calls activity |
| `workflow_mockedActivity_emptyResult` | Handles empty string from activity |
| `workflow_mockedActivity_retriesOnFailure` | Activity retried 3x on transient failure |
| `workflow_mockedActivity_failsAfterMaxRetries` | WorkflowFailedException after 3 failures |

---

## 5. Python Worker Tests

### 5a. test_activities.py

**File:** `python-worker/tests/test_activities.py`

**Purpose:** Unit test the compose_greeting activity both as a plain async function
and inside Temporal's ActivityEnvironment.

**How the ActivityEnvironment works:**

```python
 # Direct call (no Temporal context):
 result = await compose_greeting("Alice")    # Just an async function call

 # With ActivityEnvironment (injects Temporal context):
 result = await activity_env.run(compose_greeting, "Alice")
 # ActivityEnvironment provides:
 #   - activity.info() (task queue, attempt number, workflow ID, etc.)
 #   - Cancellation support
 #   - Heartbeat testing
```

**Test cases (TestComposeGreetingDirect):**

| Test | What It Validates |
|------|-------------------|
| `test_greeting_contains_name` | Output includes input name |
| `test_greeting_identifies_python_worker` | Output says "Python Worker" |
| `test_greeting_contains_python_version` | Output includes Python version |
| `test_greeting_contains_hostname` | Output includes hostname |
| `test_greeting_contains_timestamp` | Output includes UTC timestamp |
| `test_greeting_empty_name` | Empty string handled |
| `test_greeting_special_characters` | Special chars preserved |
| `test_greeting_unicode_name` | Unicode handled |

**Test cases (TestComposeGreetingWithEnvironment):**

| Test | What It Validates |
|------|-------------------|
| `test_activity_runs_in_environment` | Activity works with Temporal context injected |
| `test_activity_returns_string` | Return type is str (critical for cross-language compat) |


### 5b. test_workflows.py

**File:** `python-worker/tests/test_workflows.py`

**Purpose:** Integration tests using Temporal's in-process test server (time-skipping mode).
Tests the full workflow→activity execution path.

**How WorkflowEnvironment works:**

```python
 # Start embedded Temporal server with time-skipping:
 async with await WorkflowEnvironment.start_time_skipping() as env:
     # Create a worker registered with our workflow + activities:
     async with Worker(env.client, task_queue=TASK_QUEUE,
                        workflows=[PythonHelloWorkflow],
                        activities=[compose_greeting]):
         # Execute workflow through the client:
         result = await env.client.execute_workflow(
             PythonHelloWorkflow.run, "Alice",
             id="test-001", task_queue=TASK_QUEUE)

 # Time-skipping mode:
 #   The embedded server fast-forwards through timers and retry backoff.
 #   A workflow with a 60-second retry delay completes instantly in tests.
```

**How mocked activities work in Python:**

```python
 @activity.defn(name="compose_greeting")  # Same name = replaces real activity
 async def mock_compose_greeting(name: str) -> str:
     return f"MOCKED: Hello {name}"

 # Register mock instead of real activity:
 Worker(env.client, ..., activities=[mock_compose_greeting])
```

**How retry testing works in Python:**

```python
 call_count = 0

 @activity.defn(name="compose_greeting")
 async def flaky_activity(name: str) -> str:
     nonlocal call_count
     call_count += 1
     if call_count < 3:
         raise RuntimeError("Transient error")    # Fails 1st and 2nd attempt
     return f"Success after {call_count} attempts"  # Succeeds 3rd attempt

 # Temporal's retry policy (maximum_attempts=3) handles this automatically.
 # Time-skipping makes the backoff delays instant.
```

**Test cases (TestPythonHelloWorkflowIntegration):**

| Test | What It Validates |
|------|-------------------|
| `test_workflow_completes_successfully` | Full round-trip with real activity |
| `test_workflow_with_different_names` | Multiple names including unicode |
| `test_workflow_returns_string_type` | Return type is str |
| `test_workflow_result_contains_metadata` | host, python version, timestamp present |

**Test cases (TestPythonHelloWorkflowWithMockedActivity):**

| Test | What It Validates |
|------|-------------------|
| `test_workflow_calls_activity` | Workflow delegates to mock activity |
| `test_workflow_retries_on_activity_failure` | 2 failures + 1 success = 3 calls total |
| `test_workflow_fails_after_max_retries` | WorkflowFailureError after 3 failures |

---

## 6. End-to-End Integration Tests

### e2e-test.sh

**File:** `tests/e2e-test.sh`

**Purpose:** Validates the FULL running Docker Compose stack. Tests all 6 containers,
HTTP endpoints, cross-language workflow execution, and container health.

**Prerequisites:** Run `docker compose up --build -d` first.

**Test phases:**

| Phase | What It Tests |
|-------|---------------|
| 1. Service Health Checks | Spring Boot and Temporal UI are reachable (waits up to 90s) |
| 2. Spring Boot API Tests | GET /api/health returns 200 with correct JSON |
| 3. Static Content | index.html served, contains demo title and tutorial section |
| 4. API Error Handling | Wrong HTTP method → 405; missing body → 400 |
| 5. Full Workflow Execution | POST /api/hello → both Java and Python workers respond with SUCCESS |
| 6. Temporal UI Validation | UI loads at port 8080 |
| 7. Docker Container Status | All 6 containers are in "running" state |

**How Phase 5 works (the critical test):**

```
 1. curl sends POST /api/hello {"name": "E2E-Test"} to Spring Boot
 2. Spring Boot creates workflow stubs and calls Temporal Server via gRPC
 3. Temporal Server persists events in PostgreSQL
 4. Java Worker (polling java-hello-queue) picks up and executes workflow
 5. Python Worker (polling python-hello-queue) picks up and executes workflow
 6. Both results flow back through Temporal Server to Spring Boot
 7. Spring Boot assembles JSON response
 8. Script validates:
      - javaWorker.status == "SUCCESS"
      - javaWorker.message contains "Hello E2E-Test" and "Java Worker"
      - pythonWorker.status == "SUCCESS"
      - pythonWorker.message contains "Hello E2E-Test" and "Python Worker"
      - temporalUi == "http://localhost:8080"
```

---

## 7. Running All Tests

### Unit + Integration Tests (no Docker required)

```bash
# Spring Boot App tests (unit + controller + embedded Temporal)
cd spring-boot-app
mvn test

# Java Worker tests (activity unit + workflow with embedded Temporal)
cd java-worker
mvn test

# Python Worker tests (activity unit + workflow with embedded Temporal)
cd python-worker
pip install -r requirements-test.txt
pytest tests/ -v
```

### End-to-End Tests (requires Docker)

```bash
# Start the full stack
docker compose up --build -d

# Wait ~60s for all services to be healthy, then:
./tests/e2e-test.sh

# Stop when done
docker compose down
```

### Run Everything in Sequence

```bash
# 1. Unit & integration tests (no Docker)
echo "=== Spring Boot App Tests ==="
(cd spring-boot-app && mvn test)

echo "=== Java Worker Tests ==="
(cd java-worker && mvn test)

echo "=== Python Worker Tests ==="
(cd python-worker && pip install -r requirements-test.txt && pytest tests/ -v)

# 2. End-to-end (Docker)
echo "=== Starting Docker Stack ==="
docker compose up --build -d
sleep 60

echo "=== E2E Tests ==="
./tests/e2e-test.sh

echo "=== Cleanup ==="
docker compose down
```

---

## 8. How Temporal Workflow Execution Works

### The Core Mechanism: Event Sourcing

Temporal does not keep your workflow running in memory. Instead, it persists every
state transition as an immutable event in the database. When a worker crashes or
restarts, the workflow is reconstructed by replaying these events.

```
Normal execution:          Replay after crash:

 Code runs                  Events replayed
   │                          │
   ├─ sayHello("Alice")       ├─ WorkflowExecutionStarted (skip, already happened)
   │                          │
   ├─ schedule activity       ├─ ActivityTaskScheduled (skip)
   │                          │
   ├─ activity completes      ├─ ActivityTaskCompleted → extract result
   │                          │
   └─ return result           └─ Continue from here with reconstructed state
```

This is why workflow code must be **deterministic** — during replay, the same
code runs again but Temporal feeds it previously recorded results instead of
actually executing activities again.

### Retry Policy in This Demo

Both workers configure the same retry policy:

```
Maximum Attempts:    3
Initial Interval:    1 second
Backoff Coefficient: 2.0

Attempt 1: Execute immediately
Attempt 2: Wait 1 second, retry
Attempt 3: Wait 2 seconds, retry
  (If still failing: workflow fails with the last error)
```

### Cross-Language Communication

The Java app triggers Python workflows using an **untyped stub**:

```java
// Java doesn't know about Python's @workflow.defn class.
// It only needs the workflow TYPE NAME (a string).
WorkflowStub stub = client.newUntypedWorkflowStub(
    "PythonHelloWorkflow",  // Must match Python's @workflow.defn class name
    options);
stub.start("Alice");       // Serialized as JSON, deserialized by Python
String result = stub.getResult(String.class);
```

Temporal serializes all inputs/outputs as JSON payloads. The Java SDK and Python
SDK both use JSON as the default data converter, so string arguments and results
pass transparently between languages.

---

## 9. Test Infrastructure Deep Dive

### TestWorkflowEnvironment (Java)

Provided by `io.temporal:temporal-testing`. Starts a full Temporal server in the
JVM process using an embedded test server implementation.

```
 TestWorkflowEnvironment
   ├── Embedded Temporal Server (gRPC on random port)
   ├── In-memory persistence (no PostgreSQL needed)
   ├── WorkflowClient connected to embedded server
   └── Worker factory for creating workers
```

Key methods:
- `newInstance()` — Start embedded server
- `newWorker(taskQueue)` — Create a worker for a queue
- `getWorkflowClient()` — Get a connected client
- `start()` — Begin dispatching
- `close()` — Shutdown everything

### WorkflowEnvironment (Python)

Provided by `temporalio.testing`. Two modes available:

```python
# Time-skipping mode (recommended for tests with timers/retries):
await WorkflowEnvironment.start_time_skipping()
# → Uses a separate Temporal test server binary
# → Automatically fast-forwards through sleep/timer/retry delays
# → Tests that would take minutes in real-time complete in milliseconds

# Local mode (for tests needing real-time behavior):
await WorkflowEnvironment.start_local()
# → Uses the Temporal CLI dev server
# → Real-time execution (no skipping)
```

### ActivityEnvironment (Python)

A lightweight test harness that runs activities without any server:

```python
env = ActivityEnvironment()
result = await env.run(my_activity, "input")

# Provides these to the activity code:
#   activity.info().task_queue → "test"
#   activity.info().attempt → 1
#   activity.info().workflow_id → "test"
```

### MockMvc (Spring)

Spring's test client for HTTP-layer testing without starting a real server:

```java
@WebMvcTest(HelloController.class)  // Only loads web layer
@MockBean WorkflowClient            // Injects mock into Spring context

mockMvc.perform(post("/api/hello")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\": \"Test\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.javaWorker.status").value("SUCCESS"));
```

No HTTP server is started. MockMvc dispatches directly to the controller
through Spring's DispatcherServlet, making tests fast and deterministic.
