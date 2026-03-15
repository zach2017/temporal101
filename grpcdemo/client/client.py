"""gRPC Greeter Client — demonstrates unary and streaming calls."""

import asyncio
import logging
import os
import sys

import grpc

import greeter_pb2
import greeter_pb2_grpc

logger = logging.getLogger(__name__)

SERVER_ADDR = os.getenv("GRPC_SERVER_ADDR", "server:50051")
MAX_RETRIES = int(os.getenv("MAX_RETRIES", "10"))
RETRY_DELAY = float(os.getenv("RETRY_DELAY", "2.0"))


async def wait_for_server(channel: grpc.aio.Channel) -> None:
    """Block until the server is reachable (with retries)."""
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            await channel.channel_ready()
            logger.info("Connected to server at %s", SERVER_ADDR)
            return
        except Exception:
            logger.warning(
                "Server not ready (attempt %d/%d) — retrying in %.1fs…",
                attempt, MAX_RETRIES, RETRY_DELAY,
            )
            await asyncio.sleep(RETRY_DELAY)

    logger.error("Could not connect after %d attempts. Exiting.", MAX_RETRIES)
    sys.exit(1)


async def run_unary(stub: greeter_pb2_grpc.GreeterStub) -> None:
    """Call the unary SayHello RPC."""
    print("\n" + "=" * 50)
    print("  UNARY RPC — SayHello")
    print("=" * 50)

    response = await stub.SayHello(
        greeter_pb2.HelloRequest(name="World"),
        timeout=5,
    )
    print(f"  Response : {response.message}")
    print(f"  Timestamp: {response.timestamp}")


async def run_stream(stub: greeter_pb2_grpc.GreeterStub) -> None:
    """Call the server-streaming SayHelloStream RPC."""
    print("\n" + "=" * 50)
    print("  SERVER-STREAMING RPC — SayHelloStream")
    print("=" * 50)

    request = greeter_pb2.HelloRequest(name="Developer", times=5)
    async for reply in stub.SayHelloStream(request, timeout=30):
        print(f"  ➜ {reply.message}  [{reply.timestamp}]")

    print("  (stream complete)")


async def main() -> None:
    """Entry point: connect, then exercise both RPCs."""
    async with grpc.aio.insecure_channel(SERVER_ADDR) as channel:
        await wait_for_server(channel)
        stub = greeter_pb2_grpc.GreeterStub(channel)

        await run_unary(stub)
        await run_stream(stub)

    print("\n✅  All demos finished successfully!\n")


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)-7s | %(message)s",
    )
    asyncio.run(main())
