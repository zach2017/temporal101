# ==============================================================
#  Base stage — install deps and compile .proto → Python stubs
# ==============================================================
FROM python:3.13-slim AS base

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY protos/ protos/

# Generate Python gRPC stubs from the .proto file
RUN python -m grpc_tools.protoc \
        -Iprotos \
        --python_out=. \
        --grpc_python_out=. \
        protos/greeter.proto

# ==============================================================
#  Server image
# ==============================================================
FROM base AS server
COPY server/server.py .
EXPOSE 50051
CMD ["python", "-u", "server.py"]

# ==============================================================
#  Client image
# ==============================================================
FROM base AS client
COPY client/client.py .
CMD ["python", "-u", "client.py"]
