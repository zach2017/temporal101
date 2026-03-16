import asyncio
import argparse
import logging
import signal

from temporalio.client import Client
from temporalio.worker import Worker

from workflows import LongRunningWorkflow
from activities import process_data, notify_completion

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logger = logging.getLogger("worker")


async def main(host: str, task_queue: str):
    logger.info(f"Connecting to Temporal at {host} ...")
    client = await Client.connect(host)

    worker = Worker(
        client,
        task_queue=task_queue,
        workflows=[LongRunningWorkflow],
        activities=[process_data, notify_completion],
        # Allow up to 10 concurrent activity executions on this worker
        max_concurrent_activities=10,
    )

    # ── Graceful shutdown on Ctrl+C / SIGTERM ────────────────────────────────
    loop = asyncio.get_event_loop()
    shutdown_event = asyncio.Event()

    def _signal_handler():
        logger.info("Shutdown signal received — draining worker ...")
        shutdown_event.set()

    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _signal_handler)

    logger.info(f"Worker running on task queue: '{task_queue}'")
    logger.info("Waiting for workflows... (Ctrl+C to stop)\n")

    # Run worker until shutdown signal
    worker_task = asyncio.create_task(worker.run())
    await shutdown_event.wait()

    worker_task.cancel()
    try:
        await worker_task
    except asyncio.CancelledError:
        pass

    logger.info("Worker stopped cleanly.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Long-Running Temporal Worker")
    parser.add_argument("--host",       default="localhost:7233")
    parser.add_argument("--task-queue", default="long-running-queue")
    args = parser.parse_args()

    asyncio.run(main(args.host, args.task_queue))
