# Temporal File Processing — Java Shared Library Demo

A **Maven multi-module** project (3 modules) demonstrating how to share
Workflow interfaces, Activity interfaces, and model classes between a
Temporal **Worker** and a Temporal **Client** using the
[Temporal Java SDK 1.32.x](https://github.com/temporalio/sdk-java).

---

## Dependency graph

```
┌───────────────┐                         ┌───────────────┐
│    client     │                         │    worker      │
│               │                         │                │
│ Starts the    │                         │ Implements     │
│ Workflow via  │                         │ Workflow +     │
│ typed stub    │                         │ Activities,    │
│               │                         │ polls tasks    │
└──────┬────────┘                         └───────┬────────┘
       │           ┌────────────────┐             │
       └──────────►│   shared-lib   │◄────────────┘
                   │                │
                   │ • Workflow IF  │
                   │ • Activity IF  │
                   │ • Models       │
                   │ • Constants    │
                   └────────────────┘
```

---

## Project structure

```
temporal-file-processing/
├── pom.xml                                       ← Parent POM (modules + versions)
│
├── shared-lib/                                   ← MODULE 1 — library JAR
│   ├── pom.xml
│   └── src/main/java/com/example/shared/
│       ├── SharedConstants.java                  ← TASK_QUEUE name
│       ├── model/
│       │   ├── FileProcessingRequest.java        ← jobId, fileName, fileLocation
│       │   └── FileProcessingResult.java         ← jobId, status, message
│       ├── workflow/
│       │   └── FileProcessingWorkflow.java       ← @WorkflowInterface
│       └── activity/
│           └── FileProcessingActivities.java     ← @ActivityInterface
│
├── worker/                                       ← MODULE 2 — Worker process
│   ├── pom.xml
│   └── src/main/java/com/example/worker/
│       ├── FileProcessingWorker.java             ← main() — starts Worker
│       ├── workflow/
│       │   └── FileProcessingWorkflowImpl.java   ← implements the Workflow
│       └── activity/
│           └── FileProcessingActivitiesImpl.java ← implements the Activities
│
└── client/                                       ← MODULE 3 — Client process
    ├── pom.xml
    └── src/main/java/com/example/client/
        └── FileProcessingClient.java             ← main() — starts a Workflow
```

---

## Prerequisites

1. **Java 21+**  (`java -version`)
2. **Maven 3.8+** (`mvn -version`)
3. **Temporal CLI** — start a local dev server:
   ```bash
   temporal server start-dev
   ```
   This gives you a Temporal Service on `localhost:7233` and Web UI on `http://localhost:8233`.

---

## Build & Run

```bash
# 1. Build all three modules
mvn clean install

# 2. Start the Worker (Terminal 1) — blocks, polls for tasks
mvn -pl worker exec:java

# 3. Start a Workflow from the Client (Terminal 2)
mvn -pl client exec:java
```

**Expected Client output:**
```
Starting workflow — FileProcessingRequest{jobId='JOB-a1b2c3d4', fileName='report-2026-q1.csv', fileLocation='/data/incoming/reports'}
Workflow completed — FileProcessingResult{jobId='JOB-a1b2c3d4', status='COMPLETED', message='Job JOB-a1b2c3d4: successfully processed 'report-2026-q1.csv' from '/data/incoming/reports''}
```

---

## Why shared-lib?

| What lives where | Why |
|---|---|
| `@WorkflowInterface` in **shared-lib** | The Client needs it to create a typed `WorkflowStub`. The Worker needs it to register an implementation. |
| `@ActivityInterface` in **shared-lib** | The Workflow (also in shared-lib) calls `Workflow.newActivityStub(...)` with this type. |
| Model POJOs in **shared-lib** | Both Client and Worker serialize/deserialize the same request/result objects. |
| Workflow *implementation* in **worker** only | Only the Worker executes the Workflow code. The Client never sees it. |
| Activity *implementation* in **worker** only | Only the Worker executes Activity code (side effects, I/O). |
| `WorkflowClient` + `WorkflowStub` in **client** only | Only the Client starts Workflow Executions. |

---

## SDK version

`io.temporal:temporal-sdk:1.32.1` (Feb 2026) — set in the parent POM `<dependencyManagement>`.
