# TEMPORAL IO — Docker Setup & Hello World Guide

**Java Worker · Python Worker · Java Client on Host**
*Production-Ready Step-by-Step Walkthrough — March 2026*

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Project Directory Structure](#3-project-directory-structure)
4. [Temporal Server in Docker (docker-compose)](#4-temporal-server-in-docker-docker-compose)
5. [Python Hello World Worker (Dockerized)](#5-python-hello-world-worker-dockerized)
6. [Java Hello World Worker (Dockerized)](#6-java-hello-world-worker-dockerized)
7. [Build & Launch All Services](#7-build--launch-all-services)
8. [Java Client Application (Host Machine)](#8-java-client-application-host-machine)
9. [Running & Testing End-to-End](#9-running--testing-end-to-end)
10. [Troubleshooting](#10-troubleshooting)
11. [Cleanup](#11-cleanup)

---

## 1. Architecture Overview

This guide sets up a complete Temporal ecosystem with the following components:

| Component | Runtime | Description |
|-----------|---------|-------------|
| **Temporal Server** | Docker | Orchestration engine (server + PostgreSQL + UI) |
| **Python Worker** | Docker | Polls `hello-python` task queue, runs Python workflow |
| **Java Worker** | Docker | Polls `hello-java` task queue, runs Java workflow |
| **Java Client App** | Host (JDK) | Submits workflow executions to Temporal from localhost |

> ⚡ **Key Concept:** Temporal decouples workflow logic from execution. Workers host the code; the Temporal Server orchestrates state, retries, and timers. Clients simply submit workflow requests.

---

## 2. Prerequisites

Ensure the following are installed on your host machine before proceeding:

| Tool | Minimum Version | Verify Command |
|------|----------------|----------------|
| Docker Engine | 20.10+ | `docker --version` |
| Docker Compose | v2.0+ | `docker compose version` |
| JDK (Java) | 17+ | `java --version` |
| Maven | 3.8+ | `mvn --version` |
| Git (optional) | Any | `git --version` |

> 💡 **Tip:** On macOS/Windows, Docker Desktop includes Docker Compose v2. On Linux, install `docker-compose-plugin` separately if needed.

---

## 3. Project Directory Structure

Create the following directory layout. Each worker lives in its own subfolder with its own Dockerfile.

```
temporal-hello-world/
├── docker-compose.yml
├── python-worker/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── workflows.py
│   ├── activities.py
│   └── worker.py
├── java-worker/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/hellotemporal/
│       ├── HelloActivity.java
│       ├── HelloActivityImpl.java
│       ├── HelloWorkflow.java
│       ├── HelloWorkflowImpl.java
│       └── HelloWorker.java
└── java-client/
    ├── pom.xml
    └── src/main/java/hellotemporal/
        └── HelloClient.java
```

**Create directories:**

```bash
mkdir -p temporal-hello-world/{python-worker,java-worker/src/main/java/hellotemporal,java-client/src/main/java/hellotemporal}
cd temporal-hello-world
```

---

## 4. Temporal Server in Docker (docker-compose)

This docker-compose file brings up the Temporal Server, its PostgreSQL database, the Temporal Web UI, and both workers.

### `docker-compose.yml`

```yaml
version: '3.8'

services:
  # ── PostgreSQL for Temporal persistence ──
  postgresql:
    image: postgres:15
    container_name: temporal-postgresql
    environment:
      POSTGRES_USER: temporal
      POSTGRES_PASSWORD: temporal
      POSTGRES_DB: temporal
    ports:
      - '5432:5432'
    volumes:
      - temporal-pg-data:/var/lib/postgresql/data
    networks:
      - temporal-network

  # ── Temporal Server ──
  temporal:
    image: temporalio/auto-setup:latest
    container_name: temporal-server
    depends_on:
      - postgresql
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=postgresql
    ports:
      - '7233:7233'     # gRPC Frontend
    networks:
      - temporal-network

  # ── Temporal Web UI ──
  temporal-ui:
    image: temporalio/ui:latest
    container_name: temporal-ui
    depends_on:
      - temporal
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
      - TEMPORAL_CORS_ORIGINS=http://localhost:3000
    ports:
      - '8080:8080'
    networks:
      - temporal-network

  # ── Python Hello World Worker ──
  python-worker:
    build: ./python-worker
    container_name: temporal-python-worker
    depends_on:
      - temporal
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
    networks:
      - temporal-network
    restart: on-failure

  # ── Java Hello World Worker ──
  java-worker:
    build: ./java-worker
    container_name: temporal-java-worker
    depends_on:
      - temporal
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
    networks:
      - temporal-network
    restart: on-failure

volumes:
  temporal-pg-data:

networks:
  temporal-network:
    driver: bridge
```

> 🔒 **Security Note:** This setup uses default credentials for local development. For production, use secrets management, TLS, and a hardened PostgreSQL configuration.

---

## 5. Python Hello World Worker (Dockerized)

The Python worker uses the official Temporal Python SDK (`temporalio`). It defines one workflow and one activity.

### `python-worker/requirements.txt`

```
temporalio>=1.7.0
```

### `python-worker/activities.py`

```python
from temporalio import activity


@activity.defn
async def say_hello(name: str) -> str:
    """Simple activity that returns a greeting."""
    print(f"Python Activity: Saying hello to {name}")
    return f"Hello {name} from Python!"
```

### `python-worker/workflows.py`

```python
from datetime import timedelta
from temporalio import workflow

# Import activities (must use string name for sandboxed workflows)
with workflow.unsafe.imports_passed_through():
    from activities import say_hello


@workflow.defn
class HelloPythonWorkflow:
    @workflow.run
    async def run(self, name: str) -> str:
        """Execute the say_hello activity."""
        result = await workflow.execute_activity(
            say_hello,
            name,
            start_to_close_timeout=timedelta(seconds=10),
        )
        return result
```

### `python-worker/worker.py`

```python
import asyncio
import os
from temporalio.client import Client
from temporalio.worker import Worker
from workflows import HelloPythonWorkflow
from activities import say_hello


async def main():
    temporal_address = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
    print(f"Connecting to Temporal at {temporal_address}...")

    client = await Client.connect(temporal_address)
    print("Connected. Starting Python worker...")

    worker = Worker(
        client,
        task_queue="hello-python",
        workflows=[HelloPythonWorkflow],
        activities=[say_hello],
    )
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
```

### `python-worker/Dockerfile`

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD ["python", "worker.py"]
```

> ✅ **What happens here:** The worker connects to Temporal, registers itself on the `hello-python` task queue, and waits. When a client starts a `HelloPythonWorkflow`, Temporal dispatches it to this worker.

---

## 6. Java Hello World Worker (Dockerized)

The Java worker uses the Temporal Java SDK. It follows the standard pattern: define an Activity interface, an Activity implementation, a Workflow interface, a Workflow implementation, and a Worker bootstrap class.

### `java-worker/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>java-worker</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <temporal.version>1.25.0</temporal.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.temporal</groupId>
            <artifactId>temporal-sdk</artifactId>
            <version>${temporal.version}</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.14</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compile with Java 17 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <!-- Build executable uber-jar with dependencies -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>hellotemporal.HelloWorker</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### `java-worker/src/main/java/hellotemporal/HelloActivity.java`

```java
package hellotemporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface HelloActivity {

    @ActivityMethod
    String sayHello(String name);
}
```

### `java-worker/src/main/java/hellotemporal/HelloActivityImpl.java`

```java
package hellotemporal;

public class HelloActivityImpl implements HelloActivity {

    @Override
    public String sayHello(String name) {
        System.out.println("Java Activity: Saying hello to " + name);
        return "Hello " + name + " from Java!";
    }
}
```

### `java-worker/src/main/java/hellotemporal/HelloWorkflow.java`

```java
package hellotemporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HelloWorkflow {

    @WorkflowMethod
    String greet(String name);
}
```

### `java-worker/src/main/java/hellotemporal/HelloWorkflowImpl.java`

```java
package hellotemporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class HelloWorkflowImpl implements HelloWorkflow {

    private final HelloActivity activity = Workflow.newActivityStub(
        HelloActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .build()
    );

    @Override
    public String greet(String name) {
        return activity.sayHello(name);
    }
}
```

### `java-worker/src/main/java/hellotemporal/HelloWorker.java`

```java
package hellotemporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class HelloWorker {

    public static void main(String[] args) {
        String target = System.getenv("TEMPORAL_ADDRESS");
        if (target == null) target = "localhost:7233";

        System.out.println("Connecting to Temporal at " + target + "...");

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        );

        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker("hello-java");
        worker.registerWorkflowImplementationTypes(HelloWorkflowImpl.class);
        worker.registerActivitiesImplementations(new HelloActivityImpl());

        factory.start();
        System.out.println("Java worker started on task queue: hello-java");
    }
}
```

### `java-worker/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/java-worker-1.0-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

> 🛠️ **Build Note:** The multi-stage Dockerfile uses Maven to compile and shade into an uber-jar in the first stage, then copies only the fat JAR into a slim JRE image. The `dependency:go-offline` step caches dependencies for faster rebuilds.

---

## 7. Build & Launch All Services

### Step 7a — Start Everything

```bash
cd temporal-hello-world

# Build and start all containers
docker compose up --build -d

# Watch the logs (all services)
docker compose logs -f
```

### Step 7b — Verify Services Are Running

```bash
# Check all containers are healthy
docker compose ps

# Expected output (5 services running):
# temporal-postgresql    running   0.0.0.0:5432->5432
# temporal-server        running   0.0.0.0:7233->7233
# temporal-ui            running   0.0.0.0:8080->8080
# temporal-python-worker running
# temporal-java-worker   running
```

### Step 7c — Open the Temporal Web UI

Navigate to **http://localhost:8080** in your browser. You should see the Temporal UI dashboard with the `default` namespace.

> ⏳ **Startup Timing:** The Temporal server may take 15–30 seconds to fully initialize. Workers will auto-retry connections (`restart: on-failure`). Watch `docker compose logs` to confirm workers print "Connected" or "worker started".

---

## 8. Java Client Application (Host Machine)

This standalone Java application runs on your host machine (not in Docker). It connects to Temporal at `localhost:7233` and triggers both the Python and Java workflows.

### `java-client/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>java-client</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <temporal.version>1.25.0</temporal.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.temporal</groupId>
            <artifactId>temporal-sdk</artifactId>
            <version>${temporal.version}</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.14</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <!-- exec-maven-plugin for 'mvn exec:java' convenience -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>hellotemporal.HelloClient</mainClass>
                </configuration>
            </plugin>
            <!-- Shade plugin to build runnable jar -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>hellotemporal.HelloClient</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### `java-client/src/main/java/hellotemporal/HelloClient.java`

```java
package hellotemporal;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;

public class HelloClient {

    public static void main(String[] args) throws Exception {
        // Connect to Temporal on localhost (exposed via docker port 7233)
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        // ── Trigger Python Workflow ──
        System.out.println("\n=== Starting Python Hello World Workflow ===");
        WorkflowStub pythonWorkflow = client.newUntypedWorkflowStub(
            "HelloPythonWorkflow",
            WorkflowOptions.newBuilder()
                .setTaskQueue("hello-python")
                .setWorkflowId("hello-python-" + System.currentTimeMillis())
                .build()
        );
        pythonWorkflow.start("World");
        String pythonResult = pythonWorkflow.getResult(String.class);
        System.out.println("Python Result: " + pythonResult);

        // ── Trigger Java Workflow ──
        System.out.println("\n=== Starting Java Hello World Workflow ===");
        WorkflowStub javaWorkflow = client.newUntypedWorkflowStub(
            "HelloWorkflow",
            WorkflowOptions.newBuilder()
                .setTaskQueue("hello-java")
                .setWorkflowId("hello-java-" + System.currentTimeMillis())
                .build()
        );
        javaWorkflow.start("World");
        String javaResult = javaWorkflow.getResult(String.class);
        System.out.println("Java Result:   " + javaResult);

        System.out.println("\n=== Both workflows completed! ===");
        System.exit(0);
    }
}
```

> 🔑 **How It Works:** The client uses `newUntypedWorkflowStub` so it can trigger workflows by name without needing the workflow interface classes. This is ideal for cross-language scenarios where the workflow is defined in Python.

---

## 9. Running & Testing End-to-End

### Step 9a — Run the Java Client from Host

```bash
cd java-client

# Option 1: Run directly with Maven
mvn compile exec:java

# Option 2: Build jar first, then run
mvn package -DskipTests
java -jar target/java-client-1.0-SNAPSHOT.jar

# Expected output:
# === Starting Python Hello World Workflow ===
# Python Result: Hello World from Python!
#
# === Starting Java Hello World Workflow ===
# Java Result:   Hello World from Java!
#
# === Both workflows completed! ===
```

### Step 9b — Verify in the Temporal Web UI

Open **http://localhost:8080** and navigate to the `default` namespace. You should see two completed workflow executions:

- `hello-python-<timestamp>` on queue `hello-python` (HelloPythonWorkflow)
- `hello-java-<timestamp>` on queue `hello-java` (HelloWorkflow)

### Step 9c — Check Worker Logs

```bash
# Python worker logs
docker compose logs python-worker
# Should show: Python Activity: Saying hello to World

# Java worker logs
docker compose logs java-worker
# Should show: Java Activity: Saying hello to World
```

### Step 9d — Using the Temporal CLI (Optional)

```bash
# Use temporal CLI via docker to inspect workflows
docker exec temporal-server temporal workflow list

# Describe a specific workflow
docker exec temporal-server temporal workflow describe \
    --workflow-id hello-python-<timestamp>
```

---

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Worker can't connect | Temporal server not ready | Wait 30s, check `docker compose logs temporal` |
| Connection refused on 7233 | Port not exposed | Verify ports mapping in docker-compose.yml |
| Workflow times out | Wrong task queue name | Ensure client queue matches worker queue exactly |
| Java build fails in Docker | Maven dependency issue | Run `mvn dependency:resolve` locally to verify, check pom.xml |
| Python import error | temporalio not installed | Check requirements.txt and Dockerfile `RUN pip install` |
| UI shows "No workflows" | Wrong namespace | Ensure client connects to `default` namespace |

### Quick Health Check Script

```bash
#!/bin/bash
echo '--- Container Status ---'
docker compose ps
echo ''
echo '--- Temporal Server Health ---'
docker exec temporal-server temporal operator cluster health
echo ''
echo '--- Active Task Queues ---'
docker exec temporal-server temporal task-queue describe \
    --task-queue hello-python
docker exec temporal-server temporal task-queue describe \
    --task-queue hello-java
```

---

## 11. Cleanup

### Stop All Services

```bash
# Stop and remove containers
docker compose down

# Stop and remove containers + delete volumes (database data)
docker compose down -v

# Also remove built images
docker compose down -v --rmi local
```

### Remove All Temporal Docker Data

```bash
# Nuclear option: remove everything
docker compose down -v --rmi all --remove-orphans
docker volume prune -f
```

---


Good — `index.html` is gone and all Java files are in place. Writing the new HTML now.All done. Here's a full summary of everything that's now in the project.

---

## What was added / changed

### New Java files (Temporal scaffolding)

**`temporal/workflow/`**
- `DocumentProcessingWorkflow` — `@WorkflowInterface` with `@WorkflowMethod processDocument()`, `@QueryMethod getStatus()`, and `@SignalMethod requestCancellation()`
- `DocumentWorkflowRequest` — input payload (jobId, fileName, storageKey, contentType, size)
- `DocumentWorkflowResult` — final output (extractedText, status, per-stage timing ms)
- `WorkflowStatusSnapshot` — live status returned by the query method; has `State` enum (`QUEUED / RUNNING / COMPLETED / FAILED / CANCELLED`) and `PipelineStage` enum (`NOT_STARTED → SECURITY_SCAN → TEXT_EXTRACTION → NLP_ANALYSIS → DONE`)

**`temporal/activity/`**
- `SecurityScanActivity` — `@ActivityInterface` Stage 1, returns `ScanResult` (clean, verdict, mimeType)
- `TextExtractionActivity` — `@ActivityInterface` Stage 2, returns `ExtractionResult` (rawText, pageCount, wordCount, parserUsed)
- `NlpAnalysisActivity` — `@ActivityInterface` Stage 3, returns `AnalysisResult` (documentType, summary, namedEntities, keyTopics)

**`temporal/worker/DocumentWorker`** — marker interface defining the `DOCUMENT_PROCESSING_TASK_QUEUE` constant and `start()`/`shutdown()` contract that the future impl class must honour

**`temporal/stub/DocumentWorkflowClientStub`** — Spring `@Service` anti-corruption layer wrapping `WorkflowClient`: `startWorkflow()`, `queryStatus()`, `awaitResult()`, `cancelWorkflow()`

**`controller/UploadWorkerController`** — new REST controller:

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/worker/upload` | Stages file, starts Temporal workflow, returns **202** with `workflowId` immediately |
| `GET` | `/api/worker/status/{workflowId}` | Calls `@QueryMethod` → live `WorkflowStatusSnapshot` |
| `GET` | `/api/worker/result/{workflowId}` | Awaits final `DocumentWorkflowResult` via `CompletableFuture` |
| `DELETE` | `/api/worker/cancel/{workflowId}` | Sends `@SignalMethod` cancellation |
| `GET` | `/api/worker/health` | Health check |

### Updated `index.html`

- **Mode toggle pill** in the header — switches between **CompletableFuture** (acid green) and **Temporal Worker** (purple) — all colours, badges, and behaviours swap cleanly
- **CF mode** — unchanged: timer-driven stage animation matching the 1.5 s / 2.5 s / 1.5 s backend sleeps
- **Temporal mode** — submits to `/api/worker/upload` (202), then polls `/api/worker/status/{id}` every 2 s, maps `PipelineStage` → stage dots in real time, fetches final result from `/api/worker/result/{id}` on completion
- **Temporal info card** on the left panel shows the 3-activity pipeline graph with wire connectors
- **Two health dots** in the header — one for each API, both checked every 10 s
- **Workflow ID badge** appears in the results header when running in Temporal mode
- **Spinning poll indicator** shown while the status loop is active