# Temporal Java Workers

An extensible Temporal worker framework with a full CLI client.

**Java 21** · **Temporal SDK 1.32.1** · **picocli 4.7.7** · **logback 1.5.32** · **JUnit 5**

## Quick Start — Host Build

```bash
# Build the fat JAR
./build.sh

# Start workers (requires Temporal server on localhost:7233)
./run.sh workers

# In another terminal — use the CLI
./run.sh cli start Alice --wait
./run.sh cli list
./run.sh cli status --id <WORKFLOW_ID>
```

## Quick Start — Docker

```bash
# Start everything: Temporal server, UI, and workers
docker compose up --build

# Use the CLI inside the container
docker compose exec workers temporal-cli start Alice --wait
docker compose exec workers temporal-cli list
```

Temporal UI at **http://localhost:8080**.

## Project Structure

```
temporal-workers-java/
├── src/main/java/com/temporal/workers/
│   ├── WorkerRunner.java              # Main entrypoint — starts all workers
│   ├── config/
│   │   └── WorkerConfig.java          # Centralized env-driven config
│   ├── registry/
│   │   ├── WorkerRegistration.java    # Record: queue + workflows + activities
│   │   └── WorkerRegistry.java        # Central place to register workers
│   ├── helloworld/                    # Hello World worker package
│   │   ├── HelloInput.java            # Activity input DTO
│   │   ├── HelloResult.java           # Activity result DTO
│   │   ├── HelloActivities.java       # Activity interface
│   │   ├── HelloActivitiesImpl.java   # Long-running impl with heartbeats
│   │   ├── HelloWorldWorkflow.java    # Workflow interface
│   │   └── HelloWorldWorkflowImpl.java
│   └── cli/                           # CLI client (picocli 4.7.7)
│       ├── TemporalCli.java           # Main CLI entry point
│       ├── ClientFactory.java         # Shared Temporal client factory
│       ├── StartCommand.java          # Start a workflow
│       ├── StatusCommand.java         # Check workflow status
│       ├── ResultCommand.java         # Wait for workflow result
│       ├── DescribeCommand.java       # Full execution details + history
│       ├── CancelCommand.java         # Graceful cancellation
│       ├── TerminateCommand.java      # Immediate termination
│       └── ListCommand.java           # List workflow executions
├── build.sh                           # Host build script
├── run.sh                             # Host run script (workers + cli)
├── mvnw                               # Maven wrapper
├── pom.xml                            # Maven build (Java 21, shade plugin)
├── Dockerfile                         # Multi-stage: Maven build → JRE 21
├── docker-compose.yml
└── scripts/
    ├── entrypoint.sh                  # Docker entrypoint
    ├── temporal-cli.sh                # CLI wrapper (on PATH in container)
    └── setup.d/
        └── 01-register-namespace.sh
```

## Host Build & Run

Requires **Java 21+** and **Maven 3.9+** (or use the included `mvnw` wrapper).

```bash
# Build
./build.sh                    # clean + package
./build.sh --skip-tests       # skip tests

# Start workers
./run.sh workers

# CLI commands
./run.sh cli start Alice                          # fire-and-forget
./run.sh cli start Alice --wait                   # block until complete
./run.sh cli start Bob --id my-custom-id          # custom workflow ID
./run.sh cli status --id <ID>                     # check status
./run.sh cli result --id <ID>                     # wait for result
./run.sh cli result --id <ID> --timeout 120       # wait with deadline
./run.sh cli describe --id <ID> --history         # full details + events
./run.sh cli cancel --id <ID>                     # graceful cancel
./run.sh cli terminate --id <ID> --force          # immediate kill
./run.sh cli list                                 # list recent
./run.sh cli list --status RUNNING --limit 50     # filter
./run.sh cli --help                               # help

# Or run directly with Maven (no JAR needed)
mvn exec:java                                         # start workers
mvn exec:java -Dexec.mainClass="com.temporal.workers.cli.TemporalCli" -Dexec.args="list"
```

### Environment Variables

Override connection settings via env vars:

```bash
TEMPORAL_HOST=my-server TEMPORAL_PORT=7233 ./run.sh workers
TEMPORAL_HOST=my-server ./run.sh cli start Alice --wait
```

## CLI Reference

| Command | Description |
|---|---|
| `start <name>` | Start a Hello World workflow |
| `start <name> --wait` | Start and block until complete |
| `start <name> --id <ID>` | Start with a custom workflow ID |
| `status --id <ID>` | Check running/completed/failed status |
| `result --id <ID>` | Block until done, print result |
| `result --id <ID> --timeout 60` | Wait with deadline (seconds) |
| `describe --id <ID>` | Show execution details + pending activities |
| `describe --id <ID> --history` | Include event history |
| `cancel --id <ID>` | Graceful cancel (workflow can clean up) |
| `terminate --id <ID> --force` | Kill immediately, no cleanup |
| `list` | List recent executions |
| `list --status RUNNING` | Filter by status |
| `list --type HelloWorldWorkflow` | Filter by workflow type |

## How the Hello World Worker Works

1. **`HelloWorldWorkflow`** receives a name and delegates to the activity.
2. **`HelloActivitiesImpl.sayHelloLongRunning`** runs 20 steps × 3 seconds each.
3. Every step **heartbeats** progress back to Temporal.
4. If the worker crashes, Temporal reschedules and the activity **resumes from the last heartbeat**.
5. Timeouts: 30-minute start-to-close, 30-second heartbeat. Five retries with exponential backoff.

## Adding a New Worker

1. Create a new package under `com.temporal.workers` with your workflow/activity interfaces and implementations.

2. Register it in `WorkerRegistry.discoverWorkers()`:
   ```java
   registrations.add(new WorkerRegistration(
           "my-worker-queue",
           List.of(MyWorkflowImpl.class),
           List.of(new MyActivitiesImpl())
   ));
   ```

3. Rebuild: `./build.sh` or `docker compose up --build`

## Configuration

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `localhost` | Temporal gRPC host |
| `TEMPORAL_PORT` | `7233` | Temporal gRPC port |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `WORKER_LOG_LEVEL` | `INFO` | Log level (logback) |
| `WORKER_MAX_CONCURRENT_ACTIVITIES` | `10` | Max parallel activities |
| `WORKER_MAX_CONCURRENT_WORKFLOWS` | `10` | Max parallel workflow tasks |
| `JAVA_OPTS` | `-Djava.net.preferIPv4Stack=true` | JVM flags |

## Dependencies

| Artifact | Version |
|---|---|
| `io.temporal:temporal-sdk` | 1.32.1 |
| `info.picocli:picocli` | 4.7.7 |
| `ch.qos.logback:logback-classic` | 1.5.32 |
| `org.slf4j:slf4j-api` | 2.0.17 (transitive) |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 (test) |
