# Temporal Hello World — Java CLI Client

Java client that triggers the `HelloWorldWorkflow` defined in the Python worker.

## Requirements
- Java 11+
- Maven 3.6+
- Python worker running (see `temporal-hello-world/`)
- Temporal server running on `localhost:7233`

## Build

```bash
mvn package -q
# Produces: target/temporal-hello-client.jar
```

## Run

```bash
# Defaults: name=World, taskQueue=hello-world-queue, host=localhost:7233
java -jar target/temporal-hello-client.jar

# Custom name
java -jar target/temporal-hello-client.jar Alice

# Custom name + task queue
java -jar target/temporal-hello-client.jar Alice hello-world-queue

# Custom name + task queue + host
java -jar target/temporal-hello-client.jar Alice hello-world-queue localhost:7233
```

## Expected output

```
Connecting to Temporal at localhost:7233 ...
Starting workflow  id=hello-Alice-3f2a1b4c  taskQueue=hello-world-queue  name=Alice
Result: Hello, Alice!
```

## How it works

```
Java CLI                    Temporal Server              Python Worker
─────────                   ───────────────              ─────────────
RunWorkflow.main()
  │
  ├─ connect(host)  ──────► accept connection
  │
  ├─ newWorkflowStub()
  │
  └─ workflow.run("Alice") ─► schedule workflow ────────► pick up task
                                                           execute say_hello()
                             receive result ◄──────────── return "Hello, Alice!"
        print result ◄──────
```

The `HelloWorldWorkflow` interface is a **stub only** — the actual implementation
lives in the Python worker. The task queue name must match on both sides.
