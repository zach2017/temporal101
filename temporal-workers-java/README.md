# Temporal Java Workers

An extensible Temporal worker framework in Java 17 with Maven. Ships with a **Hello World** long-running worker and is designed so new workers can be dropped in with minimal boilerplate.

## Architecture

```
temporal-workers-java/
├── src/main/java/com/temporal/workers/
│   ├── WorkerRunner.java              # Main entrypoint — starts all workers
│   ├── StartHello.java                # CLI to trigger the Hello World workflow
│   ├── config/
│   │   └── WorkerConfig.java          # Centralized env-driven config
│   ├── registry/
│   │   ├── WorkerRegistration.java    # Record: queue + workflows + activities
│   │   └── WorkerRegistry.java        # Central place to register workers
│   └── helloworld/                    # Hello World worker package
│       ├── HelloInput.java            # Activity input DTO
│       ├── HelloResult.java           # Activity result DTO
│       ├── HelloActivities.java       # Activity interface
│       ├── HelloActivitiesImpl.java   # Long-running impl with heartbeats
│       ├── HelloWorldWorkflow.java    # Workflow interface
│       └── HelloWorldWorkflowImpl.java# Workflow impl with retry policy
├── src/main/resources/
│   └── logback.xml                    # Logging config
├── scripts/
│   ├── entrypoint.sh                  # Docker entrypoint (wait + setup + run)
│   └── setup.d/                       # Drop-in setup scripts
│       └── 01-register-namespace.sh
├── pom.xml                            # Maven build with shade plugin (fat JAR)
├── Dockerfile                         # Multi-stage: Maven build → slim JRE
└── docker-compose.yml                 # Full local stack
```

## Quick Start

### With Docker (recommended)

```bash
# Start everything: Temporal server, UI, and Java workers
docker compose up --build

# In another terminal, trigger the Hello World workflow
docker compose exec workers java -cp /app/app.jar \
    com.temporal.workers.StartHello "Alice"
```

Open the Temporal UI at **http://localhost:8080** to watch the workflow execute its 20 long-running steps.

### Without Docker

```bash
# 1. Build the fat JAR
mvn clean package

# 2. Make sure a Temporal server is running on localhost:7233

# 3. Start the workers
java -jar target/temporal-workers-java-0.1.0.jar

# 4. In another terminal, trigger the workflow
java -cp target/temporal-workers-java-0.1.0.jar \
    com.temporal.workers.StartHello "Bob"
```

### Run directly with Maven (no JAR)

```bash
# Start workers
mvn exec:java

# Or with a custom main class
mvn exec:java -Dexec.mainClass="com.temporal.workers.StartHello" -Dexec.args="Bob"
```

## How the Hello World Worker Works

The Hello World worker demonstrates a **long-running** Temporal activity:

1. **`HelloWorldWorkflow`** receives a name and delegates to the activity.
2. **`HelloActivitiesImpl.sayHelloLongRunning`** runs 20 steps (configurable), each taking 3 seconds.
3. Every step **heartbeats** its progress back to Temporal.
4. If the worker crashes, Temporal reschedules the activity on another worker, and it **resumes from the last heartbeat** — no work is repeated.
5. Timeouts: 30-minute start-to-close, 30-second heartbeat. Five retries with exponential backoff.

## Adding a New Worker

1. Create a new package under `com.temporal.workers`:
   ```
   src/main/java/com/temporal/workers/myworker/
   ├── MyActivities.java          (interface)
   ├── MyActivitiesImpl.java      (implementation)
   ├── MyWorkflow.java            (interface)
   └── MyWorkflowImpl.java        (implementation)
   ```

2. Register it in `WorkerRegistry.discoverWorkers()`:
   ```java
   registrations.add(new WorkerRegistration(
           "my-worker-queue",
           List.of(MyWorkflowImpl.class),
           List.of(new MyActivitiesImpl())
   ));
   ```

3. Rebuild and restart:
   ```bash
   docker compose up --build
   ```

## Configuration

All settings are driven by environment variables:

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `localhost` | Temporal gRPC host |
| `TEMPORAL_PORT` | `7233` | Temporal gRPC port |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `WORKER_LOG_LEVEL` | `INFO` | Log level (logback) |
| `WORKER_MAX_CONCURRENT_ACTIVITIES` | `10` | Max parallel activities per worker |
| `WORKER_MAX_CONCURRENT_WORKFLOWS` | `10` | Max parallel workflow tasks per worker |

## Setup Scripts

Drop any `.sh` file into `scripts/setup.d/` and it will run (in alphabetical order) after the Temporal server is reachable but before workers start.
