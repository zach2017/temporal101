# gRPC Python Demo — Docker Compose

A complete **gRPC** demo with a Python async server and client, packaged in Docker.

## Architecture

```
┌──────────────────────────┐       ┌──────────────────────────┐
│      grpc-client         │       │      grpc-server         │
│  (runs demos, exits)     │──────▶│  :50051  (async)         │
│                          │ gRPC  │                          │
│  • Unary call            │       │  • SayHello (unary)      │
│  • Server-streaming call │       │  • SayHelloStream        │
└──────────────────────────┘       │  • Health check          │
                                   │  • Reflection            │
                                   └──────────────────────────┘
```

## Quick Start

```bash
# Build and run everything
docker compose up --build

# Or use Make
make up
```

## What Happens

1. **Server** starts and listens on `:50051` with health checks enabled.
2. **Client** waits for the server to be healthy, then:
   - Calls **SayHello** (unary) — sends a name, gets one greeting back.
   - Calls **SayHelloStream** (server-streaming) — sends a name, receives 5 greetings streamed back with 0.5 s delays.
3. Client prints results and exits.

## Project Structure

```
grpc-demo/
├── protos/
│   └── greeter.proto        # Service + message definitions
├── server/
│   └── server.py            # Async gRPC server
├── client/
│   └── client.py            # Async gRPC client
├── Dockerfile               # Multi-stage (base → server / client)
├── docker-compose.yml       # Orchestration
├── requirements.txt         # Python dependencies
├── Makefile                 # Convenience targets
└── README.md
```

## Useful Commands

| Command | Description |
|---------|-------------|
| `docker compose up --build` | Build and run |
| `docker compose logs -f server` | Follow server logs |
| `docker compose down` | Stop everything |
| `make clean` | Remove containers, images, volumes |

## Testing with grpcurl

With the server running you can hit it directly:

```bash
# List available services (reflection is enabled)
grpcurl -plaintext localhost:50051 list

# Call SayHello
grpcurl -plaintext -d '{"name":"Alice"}' localhost:50051 greeter.Greeter/SayHello

# Call SayHelloStream
grpcurl -plaintext -d '{"name":"Bob","times":3}' localhost:50051 greeter.Greeter/SayHelloStream
```

To run the client directly on your host while the server stays in Docker, you'll need to compile the proto stubs locally and point the client at `localhost:50051`. Here are the steps:

**1. Start only the server in Docker:**

```bash
docker compose up --build server
```

**2. Set up a Python venv and install deps:**

```bash
cd grpc-demo
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

**3. Compile the proto stubs locally:**

```bash
python -m grpc_tools.protoc \
  -Iprotos \
  --python_out=client \
  --grpc_python_out=client \
  protos/greeter.proto

Windows:

  python -m grpc_tools.protoc -Iprotos --python_out=client --grpc_python_out=client protos/greeter.proto

  set GRPC_SERVER_ADDR=localhost:50051
  python client\client.py

```


This generates `greeter_pb2.py` and `greeter_pb2_grpc.py` inside the `client/` folder.

**4. Run the client, overriding the server address:**

```bash
GRPC_SERVER_ADDR=localhost:50051 python client/client.py
```

The key difference is changing the address from `server:50051` (the Docker service name) to `localhost:50051` since port 50051 is already mapped to your host in the Compose file.

## Key Features

- **Async** — both server and client use `grpc.aio` (asyncio-native)
- **Health checking** — standard `grpc.health.v1` protocol
- **Reflection** — introspect services at runtime with tools like `grpcurl`
- **Graceful shutdown** — server handles `SIGTERM`/`SIGINT` with a grace period
- **Retry logic** — client retries connection with configurable backoff
- **Multi-stage Docker build** — proto compilation happens at build time
- **Python 3.13** base image (latest slim)
