# Temporal Functions — Complete Code Overview

> A comprehensive reference for every Temporal function, import, and usage pattern found in the Python and Java source files of this project — with links to the official [Temporal documentation](https://docs.temporal.io).

---

## Table of Contents

- [Project Structure](#project-structure)
- [Python — Imports & Temporal Functions](#python--imports--temporal-functions)
  - [`activities.py` — Activity Definitions](#activitiespy--activity-definitions)
  - [`workflows.py` — Workflow Definitions](#workflowspy--workflow-definitions)
  - [`worker.py` — Worker Bootstrap](#workerpy--worker-bootstrap)
  - [`run_workflow.py` — Workflow Client / Trigger](#run_workflowpy--workflow-client--trigger)
- [Java — Imports & Temporal Functions](#java--imports--temporal-functions)
  - [`LongRunningWorkflow.java` — Workflow Interface](#longrunningworkflowjava--workflow-interface)
  - [`LongRunningCLI.java` — CLI Client](#longrunningclijava--cli-client)
- [Cross-Reference: Python ↔ Java Equivalents](#cross-reference-python--java-equivalents)
- [Dependency Summary](#dependency-summary)
- [Data Flow](#data-flow)
- [Official Documentation Links](#official-documentation-links)

---

## Project Structure

```
.
├── activities.py              # Python — activity definitions
├── workflows.py               # Python — workflow definitions
├── worker.py                  # Python — Temporal worker bootstrap
├── run_workflow.py            # Python — client that starts a workflow
├── requirements.txt           # Python — dependencies (temporalio>=1.0.0)
├── LongRunningWorkflow.java   # Java  — workflow interface stub
└── LongRunningCLI.java        # Java  — CLI client (start, status, result)
```

---

## Python — Imports & Temporal Functions

> **SDK Guide:** [Python SDK Developer Guide](https://docs.temporal.io/develop/python)

### `activities.py` — Activity Definitions

> **Docs:** [Develop Activities — Python SDK](https://docs.temporal.io/develop/python/core-application#develop-activities)

#### Imports

```python
from temporalio import activity
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `activity` | `temporalio` | Provides decorators and utilities for defining Temporal activity functions | [Activity Definition](https://docs.temporal.io/develop/python/core-application#develop-activities) |

#### Functions Used

---

##### `@activity.defn`

> **Docs:** [Define an Activity — Python SDK](https://docs.temporal.io/develop/python/core-application#develop-activities)

**Type:** Decorator

**What it does:** Registers an `async` (or sync) function as a Temporal Activity. The Temporal worker discovers it by this decorator and makes it available to be called from workflows.

**Usage in code:**

```python
@activity.defn
async def say_hello(name: str) -> str:
    print(f"[Activity] Running say_hello for: {name}")
    return f"Hello, {name}!"
```

**Key details:**
- The decorated function becomes a callable activity with the name `say_hello` (derived from the function name by default).
- Accepts a single input argument (`name: str`) and returns a string.
- Runs inside the worker process when invoked by a workflow via `workflow.execute_activity()`.
- Supports both async and sync implementations — see [Sync vs. Async Activities](https://docs.temporal.io/develop/python/python-sdk-sync-vs-async).

---

### `workflows.py` — Workflow Definitions

> **Docs:** [Develop Workflows — Python SDK](https://docs.temporal.io/develop/python/core-application#develop-workflows)

#### Imports

```python
from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from activities import say_hello
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `timedelta` | `datetime` (stdlib) | Represents a duration; used to set activity timeouts | [Activity Timeouts](https://docs.temporal.io/develop/python/failure-detection#activity-timeouts) |
| `workflow` | `temporalio` | Core module for defining workflows, executing activities, and accessing workflow utilities | [Workflow Definition](https://docs.temporal.io/develop/python/core-application#develop-workflows) |
| `RetryPolicy` | `temporalio.common` | Configures automatic retry behavior for activities | [Activity Retry Policy](https://docs.temporal.io/develop/python/failure-detection#activity-retry-policy) |
| `workflow.unsafe.imports_passed_through()` | `temporalio` | Context manager that allows importing non-workflow-safe modules without sandboxing errors | [Python Sandbox](https://docs.temporal.io/develop/python/python-sdk-sandbox) |
| `say_hello` | `activities` | The activity function to be invoked inside the workflow | — |

#### Functions Used

---

##### `workflow.unsafe.imports_passed_through()`

> **Docs:** [Python SDK Sandbox — Passthrough Modules](https://docs.temporal.io/develop/python/python-sdk-sandbox)

**Type:** Context manager

**What it does:** Disables Temporal's workflow sandbox import restrictions within its block. Temporal sandboxes workflow code to ensure determinism — this context manager tells the sandbox to pass through (allow) the enclosed imports without interception.

**Usage in code:**

```python
with workflow.unsafe.imports_passed_through():
    from activities import say_hello
```

**Why it's needed:** Activity definitions live outside the workflow sandbox. Without this wrapper, importing `activities` at module level would raise a sandbox restriction error.

---

##### `@workflow.defn`

> **Docs:** [Define a Workflow — Python SDK](https://docs.temporal.io/develop/python/core-application#develop-workflows)

**Type:** Class decorator

**What it does:** Registers a class as a Temporal Workflow Definition. The Temporal worker discovers it by this decorator.

**Usage in code:**

```python
@workflow.defn
class HelloWorldWorkflow:
    ...
```

**Key details:**
- Applied to a class (not a function).
- The class must contain exactly one method decorated with `@workflow.run`.
- The workflow name defaults to the class name (`"HelloWorldWorkflow"`).

---

##### `@workflow.run`

> **Docs:** [Workflow Definition — Python SDK](https://docs.temporal.io/develop/python/core-application#develop-workflows)

**Type:** Method decorator

**What it does:** Marks the single entry-point method of a workflow. This is the method that Temporal executes when the workflow is started.

**Usage in code:**

```python
@workflow.run
async def run(self, name: str) -> str:
    ...
```

**Key details:**
- Must be an `async` method.
- Only one `@workflow.run` method is allowed per workflow class.
- Accepts the workflow input (`name: str`) and returns the workflow result (`str`).

---

##### `workflow.execute_activity()`

> **Docs:** [Start an Activity Execution — Python SDK](https://docs.temporal.io/develop/python/core-application#activity-execution)

**Type:** Async function (called with `await`)

**What it does:** Schedules and executes a registered activity from within a workflow. Blocks (awaits) until the activity completes or fails.

**Usage in code:**

```python
result = await workflow.execute_activity(
    say_hello,
    name,
    start_to_close_timeout=timedelta(seconds=10),
    retry_policy=RetryPolicy(maximum_attempts=3),
)
```

**Parameters used:**

| Parameter | Value | Description | Docs |
|-----------|-------|-------------|------|
| 1st positional | `say_hello` | Reference to the activity function to execute | [Activity Execution](https://docs.temporal.io/develop/python/core-application#activity-execution) |
| 2nd positional | `name` | The input argument passed to the activity | [Activity Parameters](https://docs.temporal.io/develop/python/core-application#develop-activity-parameters) |
| `start_to_close_timeout` | `timedelta(seconds=10)` | Max time the activity has to complete after it starts. **Required** — at least one timeout must be specified. | [Activity Timeouts](https://docs.temporal.io/develop/python/failure-detection#activity-timeouts) |
| `retry_policy` | `RetryPolicy(maximum_attempts=3)` | Retry up to 3 total attempts on failure | [Activity Retry Policy](https://docs.temporal.io/develop/python/failure-detection#activity-retry-policy) |

---

##### `timedelta(seconds=10)`

**Type:** Standard library constructor (`datetime.timedelta`)

**What it does:** Creates a duration object representing 10 seconds. Used by Temporal to enforce activity timeouts.

**Usage in code:**

```python
start_to_close_timeout=timedelta(seconds=10)
```

**Key details:**
- Temporal's Python SDK accepts `timedelta` objects natively for all timeout fields.
- Other common usages: `timedelta(minutes=5)`, `timedelta(hours=1)`.
- See [Activity Timeouts](https://docs.temporal.io/develop/python/failure-detection#activity-timeouts) and [Workflow Timeouts](https://docs.temporal.io/develop/python/failure-detection#workflow-timeouts).

---

##### `RetryPolicy(maximum_attempts=3)`

> **Docs:** [Activity Retry Policy — Python SDK](https://docs.temporal.io/develop/python/failure-detection#activity-retry-policy)

**Type:** Constructor (`temporalio.common.RetryPolicy`)

**What it does:** Defines the automatic retry behavior for an activity. If the activity raises an exception, Temporal retries it according to this policy.

**Usage in code:**

```python
retry_policy=RetryPolicy(maximum_attempts=3)
```

**Available parameters (defaults shown):**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `initial_interval` | `timedelta(seconds=1)` | Wait time before the first retry |
| `backoff_coefficient` | `2.0` | Multiplier applied to interval after each retry |
| `maximum_interval` | `None` | Cap on the retry interval |
| `maximum_attempts` | `0` (unlimited) | Total number of attempts (including the first). Set to `3` in this code. |
| `non_retryable_error_types` | `None` | List of exception type names that should not be retried |

---

### `worker.py` — Worker Bootstrap

> **Docs:** [Develop a Worker — Python SDK](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker)

#### Imports

```python
import asyncio
import argparse
from temporalio.client import Client
from temporalio.worker import Worker

from workflows import HelloWorldWorkflow
from activities import say_hello
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `asyncio` | stdlib | Runs the async event loop | — |
| `argparse` | stdlib | Parses CLI arguments (`--host`, `--task-queue`) | — |
| `Client` | `temporalio.client` | Creates a connection to the Temporal server | [Temporal Client — Python SDK](https://docs.temporal.io/develop/python/temporal-client) |
| `Worker` | `temporalio.worker` | Polls a task queue and executes workflows/activities | [Run a Worker — Python SDK](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker) |
| `HelloWorldWorkflow` | `workflows` | The workflow class to register with the worker | — |
| `say_hello` | `activities` | The activity function to register with the worker | — |

#### Functions Used

---

##### `Client.connect(host)`

> **Docs:** [Connect to a Temporal Service — Python SDK](https://docs.temporal.io/develop/python/temporal-client#connect-to-a-dev-cluster)

**Type:** Async class method

**What it does:** Establishes a gRPC connection to the Temporal server and returns a `Client` instance.

**Usage in code:**

```python
client = await Client.connect(host)
```

**Parameters:**

| Parameter | Value | Description |
|-----------|-------|-------------|
| `host` | `"localhost:7233"` (default) | Address of the Temporal frontend service |

---

##### `Worker(client, task_queue, workflows, activities)`

> **Docs:** [Develop a Worker — Python SDK](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker)

**Type:** Constructor (`temporalio.worker.Worker`)

**What it does:** Creates a worker that polls the specified task queue for workflow tasks and activity tasks, then dispatches them to the registered handlers.

**Usage in code:**

```python
worker = Worker(
    client,
    task_queue=task_queue,
    workflows=[HelloWorldWorkflow],
    activities=[say_hello],
)
```

**Parameters used:**

| Parameter | Value | Description | Docs |
|-----------|-------|-------------|------|
| 1st positional | `client` | The connected Temporal `Client` | [Temporal Client](https://docs.temporal.io/develop/python/temporal-client) |
| `task_queue` | `"hello-world-queue"` | Name of the task queue to poll | [Task Queues](https://docs.temporal.io/task-queue/naming) |
| `workflows` | `[HelloWorldWorkflow]` | List of workflow classes this worker handles | [Register Types](https://docs.temporal.io/develop/python/core-application#register-types) |
| `activities` | `[say_hello]` | List of activity functions this worker handles | [Register Types](https://docs.temporal.io/develop/python/core-application#register-types) |

---

##### `worker.run()`

> **Docs:** [Run a Worker — Python SDK](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker)

**Type:** Async method

**What it does:** Starts the worker's polling loop. Blocks indefinitely until cancelled (e.g., `Ctrl+C`). Internally handles graceful shutdown.

**Usage in code:**

```python
await worker.run()
```

---

##### `asyncio.run(main(...))`

**Type:** Standard library function

**What it does:** Creates a new event loop, runs the given coroutine (`main`), and closes the loop when finished. This is the standard entry point for async Python programs.

**Usage in code:**

```python
asyncio.run(main(args.host, args.task_queue))
```

---

### `run_workflow.py` — Workflow Client / Trigger

> **Docs:** [Start a Workflow Execution — Python SDK](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution)

#### Imports

```python
import asyncio
import argparse
from temporalio.client import Client
from workflows import HelloWorldWorkflow
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `asyncio` | stdlib | Runs the async event loop | — |
| `argparse` | stdlib | Parses CLI arguments (`--host`, `--task-queue`, `--name`) | — |
| `Client` | `temporalio.client` | Creates a connection to the Temporal server | [Temporal Client — Python SDK](https://docs.temporal.io/develop/python/temporal-client) |
| `HelloWorldWorkflow` | `workflows` | Referenced to get the workflow type for `execute_workflow` | — |

#### Functions Used

---

##### `Client.connect(host)`

> **Docs:** [Connect to a Temporal Service — Python SDK](https://docs.temporal.io/develop/python/temporal-client#connect-to-a-dev-cluster)

Same as in `worker.py` — establishes a gRPC connection to Temporal.

```python
client = await Client.connect(host)
```

---

##### `client.execute_workflow()`

> **Docs:** [Start a Workflow Execution — Python SDK](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution)

**Type:** Async method

**What it does:** Starts a workflow execution **and** waits for its result in a single call. Combines "start" + "get result" into one operation.

**Usage in code:**

```python
result = await client.execute_workflow(
    HelloWorldWorkflow.run,
    name,
    id=f"hello-{name}-workflow",
    task_queue=task_queue,
)
```

**Parameters used:**

| Parameter | Value | Description | Docs |
|-----------|-------|-------------|------|
| 1st positional | `HelloWorldWorkflow.run` | Reference to the workflow's entry-point method | [Workflow Definition](https://docs.temporal.io/develop/python/core-application#develop-workflows) |
| 2nd positional | `name` | Input argument passed to the workflow | [Workflow Parameters](https://docs.temporal.io/develop/python/core-application#develop-workflow-parameters) |
| `id` | `f"hello-{name}-workflow"` | Unique workflow ID (used for deduplication and lookup) | [Workflow ID](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution) |
| `task_queue` | `"hello-world-queue"` | Which task queue the workflow should be scheduled on | [Task Queues](https://docs.temporal.io/task-queue/naming) |

**Return value:** The workflow's final result (a `str` in this case). See [Get Workflow Results](https://docs.temporal.io/develop/python/temporal-client#get-workflow-results).

---

## Java — Imports & Temporal Functions

> **SDK Guide:** [Java SDK Developer Guide](https://docs.temporal.io/develop/java)

### `LongRunningWorkflow.java` — Workflow Interface

> **Docs:** [Develop Workflows — Java SDK](https://docs.temporal.io/develop/java/core-application#develop-workflows)

#### Imports

```java
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `WorkflowInterface` | `io.temporal.workflow` | Annotation that marks an interface as a Temporal Workflow | [Workflow Definition — Java SDK](https://docs.temporal.io/develop/java/core-application#develop-workflows) |
| `WorkflowMethod` | `io.temporal.workflow` | Annotation that marks the workflow's entry-point method | [Workflow Definition — Java SDK](https://docs.temporal.io/develop/java/core-application#develop-workflows) |
| `Map` | `java.util` | Used as the payload type (`Map<String, Object>`) | — |

#### Annotations & Functions Used

---

##### `@WorkflowInterface`

> **Docs:** [Define Workflow Interface — Java SDK](https://docs.temporal.io/develop/java/core-application#develop-workflows)

**Type:** Interface-level annotation

**What it does:** Declares a Java interface as a Temporal Workflow type. Temporal's code generation and client stubs rely on this annotation to discover workflow definitions.

**Usage in code:**

```java
@WorkflowInterface
public interface LongRunningWorkflow {
    ...
}
```

**Key details:**
- Must be applied to an **interface** (not a class).
- The interface name (`LongRunningWorkflow`) becomes the default workflow type name.
- This is a **stub interface** — the actual implementation runs in the Python worker.

---

##### `@WorkflowMethod`

> **Docs:** [Define Workflow Method — Java SDK](https://docs.temporal.io/develop/java/core-application#develop-workflows)

**Type:** Method-level annotation

**What it does:** Marks the single entry-point method of the workflow interface. Equivalent to Python's `@workflow.run`.

**Usage in code:**

```java
@WorkflowMethod
String run(Map<String, Object> payload);
```

**Key details:**
- Only one `@WorkflowMethod` per interface.
- The return type (`String`) defines the workflow's result type.
- The parameter (`Map<String, Object>`) matches the Python workflow's expected input: `{"job_id": "...", "steps": N}`.

---

### `LongRunningCLI.java` — CLI Client

> **Docs:** [Temporal Client — Java SDK](https://docs.temporal.io/develop/java/temporal-client)

#### Imports

```java
// ── Temporal Client SDK ──────────────────────────────────
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

// ── Temporal Protobuf / gRPC API ─────────────────────────
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.common.v1.WorkflowExecution;

// ── Java Standard Library ────────────────────────────────
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
```

| Import | Source | Purpose | Docs |
|--------|--------|---------|------|
| `WorkflowClient` | `io.temporal.client` | High-level client for starting workflows and getting results | [Temporal Client — Java SDK](https://docs.temporal.io/develop/java/temporal-client) |
| `WorkflowOptions` | `io.temporal.client` | Builder for workflow execution options (ID, task queue, timeouts) | [Workflow Options — Java SDK](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution) |
| `WorkflowStub` | `io.temporal.client` | Untyped workflow handle for attaching to existing executions | [WorkflowStub — Java SDK](https://docs.temporal.io/develop/java/temporal-client#get-workflow-results) |
| `WorkflowServiceStubs` | `io.temporal.serviceclient` | Low-level gRPC connection to the Temporal server | [Connect to Temporal — Java SDK](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster) |
| `WorkflowServiceStubsOptions` | `io.temporal.serviceclient` | Configuration for the gRPC connection (target address) | [Connect to Temporal — Java SDK](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster) |
| `WorkflowExecutionStatus` | `io.temporal.api.enums.v1` | Enum of workflow states (RUNNING, COMPLETED, FAILED, etc.) | [Temporal API Reference](https://docs.temporal.io/references) |
| `DescribeWorkflowExecutionRequest` | `io.temporal.api.workflowservice.v1` | Protobuf request to describe/query a workflow execution | [Temporal API Reference](https://docs.temporal.io/references) |
| `DescribeWorkflowExecutionResponse` | `io.temporal.api.workflowservice.v1` | Protobuf response containing workflow execution metadata | [Temporal API Reference](https://docs.temporal.io/references) |
| `WorkflowExecution` | `io.temporal.api.common.v1` | Protobuf message identifying a workflow by ID and run ID | [Temporal API Reference](https://docs.temporal.io/references) |
| `Instant` | `java.time` | Represents a point in time; used to format start/close timestamps | — |
| `HashMap` / `Map` | `java.util` | Builds the workflow payload | — |
| `Optional` | `java.util` | Wraps nullable values (env vars, optional args) | — |
| `UUID` | `java.util` | Generates random job IDs | — |

#### Functions Used

---

##### `WorkflowServiceStubs.newServiceStubs(options)`

> **Docs:** [Connect to a Temporal Service — Java SDK](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster)

**Type:** Static factory method

**What it does:** Opens a gRPC channel to the Temporal server. This is the lowest-level connection object.

**Usage in code:**

```java
var service = WorkflowServiceStubs.newServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
        .setTarget(config.host())   // e.g. "localhost:7233"
        .build()
);
```

**Python equivalent:** [`Client.connect(host)`](https://docs.temporal.io/develop/python/temporal-client#connect-to-a-dev-cluster)

---

##### `WorkflowServiceStubsOptions.newBuilder().setTarget(host).build()`

> **Docs:** [Connect to a Temporal Service — Java SDK](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster)

**Type:** Builder pattern

**What it does:** Configures the gRPC connection target address.

**Usage in code:**

```java
WorkflowServiceStubsOptions.newBuilder()
    .setTarget("localhost:7233")
    .build()
```

---

##### `WorkflowClient.newInstance(service)`

> **Docs:** [Create a Temporal Client — Java SDK](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster)

**Type:** Static factory method

**What it does:** Creates a high-level `WorkflowClient` from the gRPC service stubs. Used to start workflows and retrieve results.

**Usage in code:**

```java
var client = WorkflowClient.newInstance(service);
```

**Python equivalent:** The `Client` object returned by [`Client.connect()`](https://docs.temporal.io/develop/python/temporal-client#connect-to-a-dev-cluster).

---

##### `client.newWorkflowStub(WorkflowClass, options)`

> **Docs:** [Start Workflow Execution — Java SDK](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution)

**Type:** Instance method

**What it does:** Creates a **typed** workflow stub — a local proxy object whose methods map to workflow executions. Calling `stub.run(payload)` starts the workflow and blocks until completion.

**Usage in code:**

```java
var wf = client.newWorkflowStub(LongRunningWorkflow.class, opts);
```

---

##### `wf.run(payload)` — Typed Stub Synchronous Start

> **Docs:** [Start Workflow Synchronously — Java SDK](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution)

**Type:** Proxy method call

**What it does:** Starts the workflow and **blocks until the result is returned** (synchronous execution).

**Usage in code (`start` command):**

```java
var result = wf.run(payload);
System.out.printf("Result: %s%n", result);
```

**Python equivalent:** [`client.execute_workflow()`](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution)

---

##### `WorkflowClient.start(wf::run, payload)`

> **Docs:** [Start Workflow Asynchronously — Java SDK](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution)

**Type:** Static method

**What it does:** Starts the workflow **asynchronously** — returns immediately after the server acknowledges the start. Does not wait for the result.

**Usage in code (`start-async` command):**

```java
WorkflowClient.start(wf::run, payload);
System.out.printf("Workflow started (async)!%n");
```

**Python equivalent:** [`client.start_workflow()`](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution) (not used in this codebase, but the SDK provides it).

---

##### `WorkflowOptions.newBuilder().setWorkflowId(id).setTaskQueue(queue).build()`

> **Docs:** [Workflow Options — Java SDK](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution)

**Type:** Builder pattern

**What it does:** Configures workflow execution options — the unique workflow ID and the task queue.

**Usage in code:**

```java
private static WorkflowOptions workflowOptions(String workflowId, String taskQueue) {
    return WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(taskQueue)
        .build();
}
```

**See also:** [Task Queues & Naming](https://docs.temporal.io/task-queue/naming)

---

##### `service.blockingStub().describeWorkflowExecution(request)`

> **Docs:** [Temporal gRPC API](https://docs.temporal.io/references)

**Type:** gRPC blocking call

**What it does:** Queries the Temporal server for the current state of a workflow execution (status, start time, close time, etc.).

**Usage in code (`status` command):**

```java
var desc = service.blockingStub().describeWorkflowExecution(
    DescribeWorkflowExecutionRequest.newBuilder()
        .setNamespace("default")
        .setExecution(WorkflowExecution.newBuilder()
            .setWorkflowId(cli.workflowId())
            .build())
        .build()
);
```

**Response fields used:**

| Accessor | Returns | Description |
|----------|---------|-------------|
| `desc.getWorkflowExecutionInfo().getStatus()` | `WorkflowExecutionStatus` | Current state enum |
| `desc.getWorkflowExecutionInfo().getStartTime().getSeconds()` | `long` | Unix epoch seconds when the workflow started |
| `desc.getWorkflowExecutionInfo().getCloseTime().getSeconds()` | `long` | Unix epoch seconds when the workflow closed |

---

##### `WorkflowExecutionStatus` — Enum Values

> **Docs:** [Temporal API Enums](https://docs.temporal.io/references)

**What it does:** Represents all possible states of a workflow execution. Used in the `STATUS_LABELS` map to render human-readable output.

**Values used in code:**

| Enum Constant | Label |
|---------------|-------|
| `WORKFLOW_EXECUTION_STATUS_RUNNING` | RUNNING |
| `WORKFLOW_EXECUTION_STATUS_COMPLETED` | COMPLETED |
| `WORKFLOW_EXECUTION_STATUS_FAILED` | FAILED |
| `WORKFLOW_EXECUTION_STATUS_CANCELED` | CANCELED |
| `WORKFLOW_EXECUTION_STATUS_TERMINATED` | TERMINATED |
| `WORKFLOW_EXECUTION_STATUS_TIMED_OUT` | TIMED_OUT |
| `WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW` | CONTINUED_AS_NEW |

---

##### `Instant.ofEpochMilli(ms)`

**Type:** Static factory method (`java.time.Instant`)

**What it does:** Converts a Unix timestamp (milliseconds) to a `java.time.Instant`, which prints as an ISO-8601 string.

**Usage in code:**

```java
var startMs = desc.getWorkflowExecutionInfo().getStartTime().getSeconds() * 1000;
System.out.printf("Started at  : %s%n", Instant.ofEpochMilli(startMs));
```

**Output example:** `2026-03-16T14:30:00Z`

---

##### `System.currentTimeMillis()`

**Type:** Static method (`java.lang`)

**What it does:** Returns the current time as Unix epoch milliseconds. Used to compute elapsed duration of a running workflow.

**Usage in code:**

```java
var elapsed = (System.currentTimeMillis() - startMs) / 1000;
System.out.printf("Running for : %ds%n", elapsed);
```

---

##### `client.newUntypedWorkflowStub(workflowId, runId, workflowType)`

> **Docs:** [Get Workflow Results — Java SDK](https://docs.temporal.io/develop/java/temporal-client#get-workflow-results)

**Type:** Instance method

**What it does:** Creates an **untyped** stub that attaches to an existing workflow execution by its ID. Unlike the typed stub, this does not need the workflow interface class.

**Usage in code (`result` command):**

```java
WorkflowStub stub = client.newUntypedWorkflowStub(
    cli.workflowId(), Optional.empty(), Optional.empty()
);
```

---

##### `stub.getResult(resultClass)`

> **Docs:** [Get Workflow Results — Java SDK](https://docs.temporal.io/develop/java/temporal-client#get-workflow-results)

**Type:** Instance method

**What it does:** Blocks until the workflow completes and returns the result, deserialized to the specified class.

**Usage in code:**

```java
var result = stub.getResult(String.class);
System.out.printf("Result: %s%n", result);
```

**Python equivalent:** `await handle.result()` — see [Get Workflow Results — Python SDK](https://docs.temporal.io/develop/python/temporal-client#get-workflow-results)

---

##### `service.shutdown()`

**Type:** Instance method

**What it does:** Gracefully shuts down the gRPC channel to the Temporal server.

**Usage in code:**

```java
service.shutdown();
System.exit(0);
```

---

## Cross-Reference: Python ↔ Java Equivalents

| Concept | Python | Java | Docs (Python) | Docs (Java) |
|---------|--------|------|---------------|-------------|
| **Connect to server** | `Client.connect(host)` | `WorkflowServiceStubs.newServiceStubs(opts)` + `WorkflowClient.newInstance(service)` | [Link](https://docs.temporal.io/develop/python/temporal-client#connect-to-a-dev-cluster) | [Link](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster) |
| **Define a workflow** | `@workflow.defn` on a class | `@WorkflowInterface` on an interface | [Link](https://docs.temporal.io/develop/python/core-application#develop-workflows) | [Link](https://docs.temporal.io/develop/java/core-application#develop-workflows) |
| **Workflow entry point** | `@workflow.run` on async method | `@WorkflowMethod` on interface method | [Link](https://docs.temporal.io/develop/python/core-application#develop-workflows) | [Link](https://docs.temporal.io/develop/java/core-application#develop-workflows) |
| **Define an activity** | `@activity.defn` on a function | `@ActivityInterface` + `@ActivityMethod` | [Link](https://docs.temporal.io/develop/python/core-application#develop-activities) | [Link](https://docs.temporal.io/develop/java/core-application#develop-activities) |
| **Execute activity** | `workflow.execute_activity(fn, arg, ...)` | `Workflow.newActivityStub(...)` | [Link](https://docs.temporal.io/develop/python/core-application#activity-execution) | [Link](https://docs.temporal.io/develop/java/core-application#activity-execution) |
| **Set timeout** | `timedelta(seconds=10)` | `Duration.ofSeconds(10)` | [Link](https://docs.temporal.io/develop/python/failure-detection#activity-timeouts) | [Link](https://docs.temporal.io/develop/java/failure-detection#activity-timeouts) |
| **Retry policy** | `RetryPolicy(maximum_attempts=3)` | `RetryOptions.newBuilder().setMaximumAttempts(3).build()` | [Link](https://docs.temporal.io/develop/python/failure-detection#activity-retry-policy) | [Link](https://docs.temporal.io/develop/java/failure-detection#activity-retry-policy) |
| **Start workflow (sync)** | `client.execute_workflow(...)` | `stub.run(payload)` via typed stub | [Link](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution) | [Link](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution) |
| **Start workflow (async)** | `client.start_workflow(...)` | `WorkflowClient.start(stub::run, payload)` | [Link](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution) | [Link](https://docs.temporal.io/develop/java/temporal-client#start-workflow-execution) |
| **Get result of existing** | `handle.result()` | `stub.getResult(cls)` | [Link](https://docs.temporal.io/develop/python/temporal-client#get-workflow-results) | [Link](https://docs.temporal.io/develop/java/temporal-client#get-workflow-results) |
| **Run worker** | `Worker(client, ...)` → `worker.run()` | `WorkerFactory` → `factory.start()` | [Link](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker) | [Link](https://docs.temporal.io/develop/java/core-application#run-a-dev-worker) |
| **Sandbox / Determinism** | `workflow.unsafe.imports_passed_through()` | N/A (Java uses class-based isolation) | [Link](https://docs.temporal.io/develop/python/python-sdk-sandbox) | [Link](https://docs.temporal.io/develop/java/core-application#workflow-logic-requirements) |

---

## Dependency Summary

### Python

From `requirements.txt`:

```
temporalio>=1.0.0
```

**Modules used from `temporalio`:**

| Module Path | What It Provides | Docs |
|-------------|-----------------|------|
| `temporalio.activity` | `@activity.defn` decorator | [Activity Definition](https://docs.temporal.io/develop/python/core-application#develop-activities) |
| `temporalio.workflow` | `@workflow.defn`, `@workflow.run`, `workflow.execute_activity()`, `workflow.unsafe.imports_passed_through()` | [Workflow Definition](https://docs.temporal.io/develop/python/core-application#develop-workflows) |
| `temporalio.common` | `RetryPolicy` | [Retry Policy](https://docs.temporal.io/develop/python/failure-detection#activity-retry-policy) |
| `temporalio.client` | `Client` (connect, execute/start workflows) | [Temporal Client](https://docs.temporal.io/develop/python/temporal-client) |
| `temporalio.worker` | `Worker` (poll task queue, dispatch work) | [Worker](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker) |

**Standard library modules:**

| Module | Used For |
|--------|----------|
| `datetime.timedelta` | Activity timeout durations |
| `asyncio` | Event loop (`asyncio.run`) |
| `argparse` | CLI argument parsing |

### Java

**Temporal SDK packages:**

| Package | What It Provides | Docs |
|---------|-----------------|------|
| `io.temporal.client` | `WorkflowClient`, `WorkflowOptions`, `WorkflowStub` | [Temporal Client — Java](https://docs.temporal.io/develop/java/temporal-client) |
| `io.temporal.serviceclient` | `WorkflowServiceStubs`, `WorkflowServiceStubsOptions` | [Connect to Temporal — Java](https://docs.temporal.io/develop/java/temporal-client#connect-to-a-dev-cluster) |
| `io.temporal.workflow` | `@WorkflowInterface`, `@WorkflowMethod` | [Workflow Definition — Java](https://docs.temporal.io/develop/java/core-application#develop-workflows) |
| `io.temporal.api.enums.v1` | `WorkflowExecutionStatus` | [API Reference](https://docs.temporal.io/references) |
| `io.temporal.api.workflowservice.v1` | `DescribeWorkflowExecutionRequest/Response` | [API Reference](https://docs.temporal.io/references) |
| `io.temporal.api.common.v1` | `WorkflowExecution` | [API Reference](https://docs.temporal.io/references) |

**Java standard library:**

| Class | Used For |
|-------|----------|
| `java.time.Instant` | Converting epoch timestamps to ISO-8601 strings |
| `java.util.UUID` | Generating random job IDs |
| `java.util.Optional` | Wrapping nullable env vars and optional arguments |
| `java.util.HashMap` / `Map` | Building workflow payloads |

---

## Data Flow

```
                         ┌──────────────────────┐
                         │   Temporal Server     │
                         │   (localhost:7233)    │
                         └──────┬───────┬───────┘
                                │       │
              ┌─────────────────┘       └──────────────────┐
              │  gRPC                            gRPC      │
              ▼                                            ▼
   ┌─────────────────────┐                  ┌──────────────────────────┐
   │  Python Worker       │                  │  Java CLI Client          │
   │  (worker.py)         │                  │  (LongRunningCLI.java)    │
   │                      │                  │                           │
   │  Registers:          │                  │  Commands:                │
   │  - HelloWorldWorkflow│                  │  - start       (sync)     │
   │  - say_hello activity│                  │  - start-async (async)    │
   │                      │                  │  - status      (describe) │
   │  Polls: task queue   │                  │  - result      (getResult)│
   └─────────────────────┘                  └──────────────────────────┘
              ▲
              │ also triggered by
              │
   ┌─────────────────────┐
   │  Python Client       │
   │  (run_workflow.py)   │
   │                      │
   │  execute_workflow()  │
   └─────────────────────┘
```

---

## Official Documentation Links

### Quick-Start Guides

| Resource | Link |
|----------|------|
| Python SDK — Set Up Local | [docs.temporal.io/develop/python/set-up-your-local-python](https://docs.temporal.io/develop/python/set-up-your-local-python) |
| Java SDK — Set Up Local | [docs.temporal.io/develop/java/set-up-your-local-java](https://docs.temporal.io/develop/java/set-up-your-local-java) |

### Python SDK

| Topic | Link |
|-------|------|
| Developer Guide (Overview) | [docs.temporal.io/develop/python](https://docs.temporal.io/develop/python) |
| Core Application (Workflows, Activities, Workers) | [docs.temporal.io/develop/python/core-application](https://docs.temporal.io/develop/python/core-application) |
| Temporal Client (Connect, Start, Results) | [docs.temporal.io/develop/python/temporal-client](https://docs.temporal.io/develop/python/temporal-client) |
| Failure Detection (Timeouts, Retries, Heartbeats) | [docs.temporal.io/develop/python/failure-detection](https://docs.temporal.io/develop/python/failure-detection) |
| Python Sandbox (Determinism) | [docs.temporal.io/develop/python/python-sdk-sandbox](https://docs.temporal.io/develop/python/python-sdk-sandbox) |
| Sync vs. Async Activities | [docs.temporal.io/develop/python/python-sdk-sync-vs-async](https://docs.temporal.io/develop/python/python-sdk-sync-vs-async) |
| Message Passing (Signals, Queries, Updates) | [docs.temporal.io/develop/python/message-passing](https://docs.temporal.io/develop/python/message-passing) |
| Testing | [docs.temporal.io/develop/python/testing-suite](https://docs.temporal.io/develop/python/testing-suite) |
| Cancellation & Termination | [docs.temporal.io/develop/python/cancellation](https://docs.temporal.io/develop/python/cancellation) |
| Interceptors | [docs.temporal.io/develop/python/interceptors](https://docs.temporal.io/develop/python/interceptors) |

### Java SDK

| Topic | Link |
|-------|------|
| Developer Guide (Overview) | [docs.temporal.io/develop/java](https://docs.temporal.io/develop/java) |
| Core Application (Workflows, Activities, Workers) | [docs.temporal.io/develop/java/core-application](https://docs.temporal.io/develop/java/core-application) |
| Temporal Client (Connect, Start, Results) | [docs.temporal.io/develop/java/temporal-client](https://docs.temporal.io/develop/java/temporal-client) |
| Failure Detection (Timeouts, Retries, Heartbeats) | [docs.temporal.io/develop/java/failure-detection](https://docs.temporal.io/develop/java/failure-detection) |
| Message Passing (Signals, Queries, Updates) | [docs.temporal.io/develop/java/message-passing](https://docs.temporal.io/develop/java/message-passing) |
| Observability (Metrics, Logging) | [docs.temporal.io/develop/java/observability](https://docs.temporal.io/develop/java/observability) |
| Converters & Encryption | [docs.temporal.io/develop/java/converters-and-encryption](https://docs.temporal.io/develop/java/converters-and-encryption) |

### General Concepts

| Topic | Link |
|-------|------|
| Task Queues & Naming | [docs.temporal.io/task-queue/naming](https://docs.temporal.io/task-queue/naming) |
| API References | [docs.temporal.io/references](https://docs.temporal.io/references) |
| Python SDK PyPI | [pypi.org/project/temporalio](https://pypi.org/project/temporalio/) |
| Java SDK GitHub | [github.com/temporalio/sdk-java](https://github.com/temporalio/sdk-java) |

---
