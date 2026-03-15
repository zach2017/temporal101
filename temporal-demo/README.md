# Temporal Multi-Language Workflow Demo

A complete demo application showcasing [Temporal](https://temporal.io) durable workflow execution with workers in **Java** and **Python**, orchestrated by a **Spring Boot** REST API with a polished HTML dashboard.

## Architecture

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Browser    │────▶│  Spring Boot    │────▶│  Temporal Server  │────▶│  Java Worker     │
│  index.html  │     │  REST API       │     │  + PostgreSQL 17  │     │  java-hello-queue│
└─────────────┘     │  :8081          │     │  :7233            │     └─────────────────┘
                    └─────────────────┘     │                   │     ┌─────────────────┐
                                            │  Temporal UI :8080│────▶│  Python Worker   │
                                            └──────────────────┘     │  python-hello-q  │
                                                                     └─────────────────┘
```

## Tech Stack & Versions

| Component          | Version                              |
|--------------------|--------------------------------------|
| Spring Boot        | 3.4.3                                |
| Java               | 21 (Eclipse Temurin)                 |
| Temporal Java SDK  | 1.32.0                               |
| Python             | 3.13                                 |
| Temporal Python SDK| >=1.9.0 (latest from PyPI)           |
| PostgreSQL         | 17                                   |
| Temporal Server    | latest (temporalio/auto-setup)       |
| Temporal UI        | latest (temporalio/ui)               |
| Docker Compose     | 3.8                                  |

## Quick Start

### Prerequisites

- **Docker** and **Docker Compose** installed
- Ports `5432`, `7233`, `8080`, `8081` available

### Run

```bash
# Clone or copy this directory, then:
cd temporal-demo

# Build and start all services
docker compose up --build

# First build takes ~3-5 minutes (Maven + pip downloads)
```

### Access

| Service              | URL                          |
|----------------------|------------------------------|
| **Demo App (HTML)**  | http://localhost:8081         |
| **REST API**         | http://localhost:8081/api     |
| **Temporal Web UI**  | http://localhost:8080         |
| **Temporal gRPC**    | localhost:7233                |
| **PostgreSQL**       | localhost:5432                |

### Use the Demo

1. Open **http://localhost:8081** in your browser
2. Enter a name and click **Send Hello**
3. Watch as the Spring Boot app triggers workflows on both workers
4. Open the **Temporal UI** at http://localhost:8080 to inspect event histories
5. Scroll down on the demo page for the **full tutorial** section

## Project Structure

```
temporal-demo/
├── docker-compose.yml          # Orchestrates all 5 services
├── README.md
│
├── spring-boot-app/            # Spring Boot REST API + Static HTML
│   ├── Dockerfile
│   ├── pom.xml                 # Spring Boot 3.4.3 + Temporal SDK 1.32.0
│   └── src/main/
│       ├── java/com/demo/temporal/
│       │   ├── TemporalDemoApp.java          # Main class
│       │   ├── config/TemporalClientConfig.java  # Temporal client bean
│       │   ├── controller/HelloController.java   # REST endpoints
│       │   └── shared/JavaHelloWorkflow.java     # Shared workflow interface
│       └── resources/
│           ├── application.yml
│           └── static/index.html             # Demo UI + Tutorial
│
├── java-worker/                # Standalone Java Temporal Worker
│   ├── Dockerfile
│   ├── pom.xml                 # Temporal SDK 1.32.0, JDK 21
│   └── src/main/java/com/demo/temporal/javaworker/
│       ├── JavaWorkerApp.java                # Worker entry point
│       ├── workflow/
│       │   ├── JavaHelloWorkflow.java        # @WorkflowInterface
│       │   └── JavaHelloWorkflowImpl.java    # Workflow implementation
│       └── activity/
│           ├── GreetingActivities.java       # @ActivityInterface
│           └── GreetingActivitiesImpl.java   # Activity implementation
│
└── python-worker/              # Python Temporal Worker
    ├── Dockerfile
    ├── requirements.txt        # temporalio>=1.9.0
    ├── activities.py           # @activity.defn
    ├── workflows.py            # @workflow.defn
    └── worker.py               # Worker entry point
```

## Scaling Workers

```bash
# Scale Java workers to 5 instances
docker compose up --scale java-worker=5 -d

# Scale Python workers to 3 instances
docker compose up --scale python-worker=3 -d

# Temporal automatically distributes tasks across all worker instances
```

## API Reference

### POST /api/hello

Triggers both Java and Python workflows.

**Request:**
```json
{ "name": "Alice" }
```

**Response:**
```json
{
  "javaWorker": {
    "status": "SUCCESS",
    "message": "Hello Alice from Java Worker! [host=abc123, jdk=21, ...]",
    "taskQueue": "java-hello-queue"
  },
  "pythonWorker": {
    "status": "SUCCESS",
    "message": "Hello Alice from Python Worker! [host=def456, python=3.13, ...]",
    "workflowId": "python-hello-...",
    "taskQueue": "python-hello-queue"
  },
  "temporalUi": "http://localhost:8080"
}
```

### GET /api/health

Returns service health status.

## Stopping

```bash
docker compose down          # Stop all services
docker compose down -v       # Stop and remove volumes (deletes DB data)
```

## Troubleshooting

| Issue | Solution |
|-------|---------|
| `temporal: unhealthy` | PostgreSQL may still be starting. Wait 30s and retry. |
| Worker errors | Check `docker compose logs java-worker` or `python-worker` |
| Port conflicts | Change ports in `docker-compose.yml` |
| First build slow | Maven/pip downloads. Subsequent builds use Docker cache. |

## License

MIT — Demo and educational purposes.
