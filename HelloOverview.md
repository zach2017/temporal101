# How Temporal Works

## Workers, Clients & Fault Tolerance

*A walkthrough using the Python & Java Hello World demo*

`temporalio SDK · Python · Java · docs.temporal.io`

---

## What is Temporal?

*A durable execution platform that ensures your code runs to completion — no matter what.*

### 🔁 Workflows

Durable orchestrators that define your business logic. Temporal persists their full event history for replay.

### ⚙️ Activities

Units of work — HTTP calls, DB writes, I/O. Allowed to be non-deterministic. Retried automatically on failure.

### 👷 Workers

Long-running processes that poll a Task Queue, execute Workflows & Activities, and report results to Temporal.

> Temporal Server manages state, scheduling, and retries — your code stays clean.

---

## Architecture Overview

*How the Temporal Server connects Clients and Workers via Task Queues*

```
┌─────────────────────┐          ┌─────────────────────────┐          ┌──────────────────┐
│       Client        │          │     Temporal Server      │          │      Worker       │
│                     │   gRPC   │                          │   gRPC   │                   │
│  run_workflow.py    │ ───────► │     localhost:7233       │ ───────► │    worker.py      │
│  LongRunningCLI.java│          │                          │          │                   │
└─────────────────────┘          └────────────┬────────────┘          └──────────────────┘
                                              │
                                              ▼
                                 ┌────────────────────────┐
                                 │ 📋 Task Queue:          │
                                 │ "hello-world-queue"     │
                                 └────────────────────────┘
```

| Step | Description |
|------|-------------|
| **1. Client starts workflow** | Client sends a gRPC request to the Temporal Server |
| **2. Server queues tasks** | Server places workflow/activity tasks on the Task Queue |
| **3. Worker polls & executes** | Worker picks up tasks, runs the code, reports results |

> Clients and Workers never talk directly — Temporal Server mediates all communication via gRPC.

---

## The Hello World Demo

*7 files across Python and Java that show every Temporal primitive in action*

| File | Role | Description |
|------|------|-------------|
| `activities.py` | Activity Definition | `@activity.defn` — `say_hello()` |
| `workflows.py` | Workflow Definition | `@workflow.defn` — orchestrates activities |
| `worker.py` | Worker Bootstrap | Connects, registers, polls task queue |
| `run_workflow.py` | Python Client | Starts workflow & gets result |
| `LongRunningWorkflow.java` | Java Workflow Interface | `@WorkflowInterface` stub |
| `LongRunningCLI.java` | Java CLI Client | start, start-async, status, result |

> Python runs the Worker (server-side). Java provides a CLI client. Both talk to the same Temporal Server.

---

## Step 1 — Define an Activity

📄 **`activities.py`**

```python
from temporalio import activity

@activity.defn
async def say_hello(name: str) -> str:
    print(f"[Activity] Running say_hello for: {name}")
    return f"Hello, {name}!"
```

| Concept | Detail |
|---------|--------|
| **`@activity.defn`** | Registers the function as a Temporal Activity with the default name `"say_hello"` |
| **`async def`** | Activities can be async or sync. Async is recommended for the Python SDK |
| **Return value** | Must be serializable. Recorded in the Workflow execution event history |

> Activities are where all side-effects live — HTTP calls, database writes, file I/O. They can be retried independently.

📖 [Python SDK → Activity Definition](https://docs.temporal.io/develop/python/core-application#develop-activities)

---

## Step 2 — Define a Workflow

📄 **`workflows.py`**

```python
from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from activities import say_hello

@workflow.defn
class HelloWorldWorkflow:
    @workflow.run
    async def run(self, name: str) -> str:
        result = await workflow.execute_activity(
            say_hello,
            name,
            start_to_close_timeout=timedelta(seconds=10),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )
        return result
```

| Concept | Detail |
|---------|--------|
| **`@workflow.defn`** | Registers class as a Workflow Definition |
| **`@workflow.run`** | Single entry point — must be async, only one allowed per class |
| **`execute_activity()`** | Schedules activity on the Task Queue and awaits its result |
| **`timedelta` + `RetryPolicy`** | 10s timeout per attempt, 3 max retries with exponential backoff |
| **`imports_passed_through()`** | Bypasses the workflow sandbox to import activity definitions safely |

> Workflows must be deterministic. All I/O goes in Activities. Temporal replays the event history to rebuild state.

📖 [Python SDK → Workflow Definition](https://docs.temporal.io/develop/python/core-application#develop-workflows)

---

## Step 3 — Register a Worker

📄 **`worker.py`**

```python
from temporalio.client import Client
from temporalio.worker import Worker

from workflows import HelloWorldWorkflow
from activities import say_hello

async def main(host: str, task_queue: str):
    client = await Client.connect(host)

    worker = Worker(
        client,
        task_queue=task_queue,
        workflows=[HelloWorldWorkflow],
        activities=[say_hello],
    )

    await worker.run()
```

| Step | What Happens |
|------|-------------|
| **1** | Connect to Temporal server at `localhost:7233` via gRPC |
| **2** | Create a Worker bound to `"hello-world-queue"` |
| **3** | Register `HelloWorldWorkflow` + `say_hello` activity |
| **4** | `worker.run()` starts infinite polling loop until Ctrl+C |

> The Worker is the runtime — it's what actually executes your Workflows and Activities.

📖 [Python SDK → Run a Worker](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker)

---

## Step 4 — Start a Workflow from a Client

### Python — `run_workflow.py`

```python
client = await Client.connect(host)

result = await client.execute_workflow(
    HelloWorldWorkflow.run,
    name,
    id=f"hello-{name}-workflow",
    task_queue=task_queue,
)

print(f"Workflow result: {result}")
```

`execute_workflow()` starts the workflow **and** blocks until the result is returned.

📖 [Python SDK → Start Workflow Execution](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution)

### Java — `LongRunningCLI.java`

```java
// Synchronous start — blocks until result
var result = wf.run(payload);

// Asynchronous start — returns immediately
WorkflowClient.start(wf::run, payload);

// Attach to existing workflow and get result
WorkflowStub stub = client.newUntypedWorkflowStub(
    workflowId, Optional.empty(), Optional.empty()
);
var result = stub.getResult(String.class);
```

The Java CLI offers four commands: `start`, `start-async`, `status`, and `result`.

📖 [Java SDK → Temporal Client](https://docs.temporal.io/develop/java/temporal-client)

> **Cross-language interop:** The Java CLI starts the same workflows that the Python worker executes. Temporal's gRPC + protobuf protocol makes this seamless — no shared code needed.

---

## Fault Tolerance

*How Temporal guarantees your workflow completes — no matter what*

### Normal Execution

```
→  say_hello scheduled
→  Worker picks up task
→  Activity completes
→  Result recorded
```

### Worker Crashes 💥

```
→  Process killed mid-run
→  Activity times out
→  Server marks as failed
```

### Automatic Recovery

```
→  Worker restarts
→  Replays event history
→  Skips completed activities
→  Resumes from last checkpoint
```

> **Key:** Temporal persists every event (`ActivityScheduled`, `ActivityCompleted`, etc.) to its database. On replay, completed activities return their cached result — no re-execution. The `RetryPolicy(maximum_attempts=3)` from `workflows.py` handles transient failures automatically.

📖 [Python SDK → Failure Detection](https://docs.temporal.io/develop/python/failure-detection)

---

## Workflow Lifecycle

*Every workflow passes through well-defined states — queryable via the Java CLI or Temporal Web UI*

| Status | Emoji | Description |
|--------|-------|-------------|
| **RUNNING** | 🔄 | Workflow is actively executing Activities |
| **COMPLETED** | ✅ | All Activities finished, result returned |
| **FAILED** | ❌ | Workflow threw an unrecoverable error |
| **CANCELED** | 🚫 | Gracefully canceled by client signal |
| **TERMINATED** | ⛔ | Force-killed from external command |
| **TIMED_OUT** | ⏰ | Exceeded the workflow execution timeout |

These states come from `WorkflowExecutionStatus` in the Temporal protobuf API. `LongRunningCLI.java` maps them to human-readable labels using `describeWorkflowExecution()` and formats timestamps with `Instant.ofEpochMilli()`.

---

## Key Takeaways

⚙️ **Activities** hold all side effects — I/O, HTTP, DB. Decorated with `@activity.defn`.

🔁 **Workflows** are deterministic orchestrators. They schedule Activities and define retry logic.

👷 **Workers** poll a Task Queue and execute both Workflows and Activities. Scale by adding instances.

📡 **Clients** (Python or Java) only talk to the Temporal Server — never directly to Workers.

🛡️ **Fault tolerance** — Temporal replays event history after crashes, skipping completed steps. Your code runs to completion.

🌐 **Cross-language** — Java CLI can start workflows that Python Workers execute — via gRPC + protobuf.

---

## References

| Resource | Link |
|----------|------|
| Python SDK Developer Guide | [docs.temporal.io/develop/python](https://docs.temporal.io/develop/python) |
| Java SDK Developer Guide | [docs.temporal.io/develop/java](https://docs.temporal.io/develop/java) |
| Python — Core Application | [docs.temporal.io/develop/python/core-application](https://docs.temporal.io/develop/python/core-application) |
| Java — Core Application | [docs.temporal.io/develop/java/core-application](https://docs.temporal.io/develop/java/core-application) |
| Python — Temporal Client | [docs.temporal.io/develop/python/temporal-client](https://docs.temporal.io/develop/python/temporal-client) |
| Java — Temporal Client | [docs.temporal.io/develop/java/temporal-client](https://docs.temporal.io/develop/java/temporal-client) |
| Python — Failure Detection | [docs.temporal.io/develop/python/failure-detection](https://docs.temporal.io/develop/python/failure-detection) |
| Java — Failure Detection | [docs.temporal.io/develop/java/failure-detection](https://docs.temporal.io/develop/java/failure-detection) |
| Python — Sandbox | [docs.temporal.io/develop/python/python-sdk-sandbox](https://docs.temporal.io/develop/python/python-sdk-sandbox) |
| Task Queues & Naming | [docs.temporal.io/task-queue/naming](https://docs.temporal.io/task-queue/naming) |
| Python SDK on PyPI | [pypi.org/project/temporalio](https://pypi.org/project/temporalio/) |
| Java SDK on GitHub | [github.com/temporalio/sdk-java](https://github.com/temporalio/sdk-java) |