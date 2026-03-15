#!/usr/bin/env python3
"""
Temporal Worker Runner
======================
Connects to the Temporal server and starts one Worker per registered
task queue.  Workers are discovered automatically through the registry.

Usage:
    python run_workers.py
"""

import asyncio
import logging
import signal
import sys

from temporalio.client import Client
from temporalio.worker import Worker

from config import settings, worker_settings
from registry import discover_workers

logging.basicConfig(
    level=getattr(logging, worker_settings.log_level.upper(), logging.INFO),
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
)
logger = logging.getLogger("temporal_runner")


async def run() -> None:
    """Connect to Temporal and run every discovered worker until shutdown."""

    logger.info(
        "Connecting to Temporal at %s  (namespace=%s)",
        settings.server_url,
        settings.namespace,
    )

    client = await Client.connect(
        settings.server_url,
        namespace=settings.namespace,
    )

    registrations = discover_workers()

    if not registrations:
        logger.error("No workers registered — nothing to do.  Exiting.")
        sys.exit(1)

    logger.info("Starting %d worker(s) …", len(registrations))

    # Build a Worker instance for each registration
    workers: list[Worker] = []
    for reg in registrations:
        w = Worker(
            client,
            task_queue=reg.task_queue,
            workflows=reg.workflows,
            activities=reg.activities,
            max_concurrent_activities=worker_settings.max_concurrent_activities,
            max_concurrent_workflow_tasks=worker_settings.max_concurrent_workflows,
        )
        workers.append(w)
        logger.info("  → Worker ready on queue: %s", reg.task_queue)

    # Run all workers concurrently
    shutdown_event = asyncio.Event()

    def _signal_handler() -> None:
        logger.info("Shutdown signal received — draining workers …")
        shutdown_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _signal_handler)

    async def _run_worker(worker: Worker) -> None:
        """Run a single worker; stop when the shutdown event fires."""
        run_task = asyncio.create_task(worker.run())
        wait_task = asyncio.create_task(shutdown_event.wait())
        done, _ = await asyncio.wait(
            [run_task, wait_task],
            return_when=asyncio.FIRST_COMPLETED,
        )
        if wait_task in done:
            await worker.shutdown()
            run_task.cancel()

    logger.info("All workers running.  Press Ctrl+C to stop.")
    await asyncio.gather(*[_run_worker(w) for w in workers])
    logger.info("All workers stopped.  Goodbye.")


def main() -> None:
    asyncio.run(run())


if __name__ == "__main__":
    main()
