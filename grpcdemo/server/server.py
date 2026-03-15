"""gRPC Greeter Server — Python implementation."""

import asyncio
import logging
import signal
from datetime import datetime, timezone

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc
from grpc_reflection.v1alpha import reflection

import greeter_pb2
import greeter_pb2_grpc

logger = logging.getLogger(__name__)


class GreeterServicer(greeter_pb2_grpc.GreeterServicer):
    """Implements the Greeter service defined in greeter.proto."""

    async def SayHello(self, request, context):
        """Unary RPC — returns a single greeting."""
        logger.info("SayHello called with name=%s", request.name)
        return greeter_pb2.HelloReply(
            message=f"Hello, {request.name}! Welcome to gRPC.",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    async def SayHelloStream(self, request, context):
        """Server-streaming RPC — yields multiple greetings."""
        count = request.times if request.times > 0 else 3
        logger.info("SayHelloStream called with name=%s, times=%d", request.name, count)

        greetings = [
            "Hello", "Bonjour", "Hola", "Ciao", "Hallo",
            "Olá", "Namaste", "Konnichiwa", "Merhaba", "Salaam",
        ]
        for i in range(count):
            word = greetings[i % len(greetings)]
            yield greeter_pb2.HelloReply(
                message=f"{word}, {request.name}! (greeting {i + 1}/{count})",
                timestamp=datetime.now(timezone.utc).isoformat(),
            )
            await asyncio.sleep(0.5)  # simulate work


async def serve() -> None:
    """Start the async gRPC server with health checks and reflection."""
    server = grpc.aio.server()

    # Register the Greeter service
    greeter_pb2_grpc.add_GreeterServicer_to_server(GreeterServicer(), server)

    # --- Health check service (standard gRPC health protocol) ---
    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    health_servicer.set(
        "greeter.Greeter",
        health_pb2.HealthCheckResponse.SERVING,
    )

    # --- Server reflection (for tools like grpcurl / grpcui) ---
    service_names = (
        greeter_pb2.DESCRIPTOR.services_by_name["Greeter"].full_name,
        health_pb2.DESCRIPTOR.services_by_name["Health"].full_name,
        reflection.SERVICE_NAME,
    )
    reflection.enable_server_reflection(service_names, server)

    listen_addr = "0.0.0.0:50051"
    server.add_insecure_port(listen_addr)

    logger.info("Starting gRPC server on %s …", listen_addr)
    await server.start()
    logger.info("Server is ready.")

    # Graceful shutdown on SIGTERM / SIGINT
    loop = asyncio.get_running_loop()
    stop_event = asyncio.Event()

    def _signal_handler():
        logger.info("Shutdown signal received.")
        stop_event.set()

    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, _signal_handler)

    await stop_event.wait()
    logger.info("Shutting down server (5 s grace period)…")
    await server.stop(grace=5)
    logger.info("Server stopped.")


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)-7s | %(message)s",
    )
    asyncio.run(serve())
