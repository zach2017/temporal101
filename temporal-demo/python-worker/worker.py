import asyncio
import os
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from activities import compose_greeting
from workflows import PythonHelloWorkflow

TASK_QUEUE = "python-hello-queue"

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)


async def main():
    temporal_address = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")

    log.info("╔══════════════════════════════════════════════╗")
    log.info("║   Temporal Python Worker Starting...         ║")
    log.info("║   Server : %-33s║", temporal_address)
    log.info("║   Queue  : %-33s║", TASK_QUEUE)
    log.info("╚══════════════════════════════════════════════╝")

    # Connect to Temporal Server
    client = await Client.connect(temporal_address, namespace="default")
    log.info("Connected to Temporal at %s", temporal_address)

    # Create and run the worker
    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[PythonHelloWorkflow],
        activities=[compose_greeting],
    )

    log.info("Python Worker is now polling task queue: %s", TASK_QUEUE)
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
