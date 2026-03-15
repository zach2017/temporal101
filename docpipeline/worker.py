"""
Temporal worker — polls the 'document-processing' task queue and
executes workflows + activities.
"""

import asyncio
import os
from temporalio.client import Client
from temporalio.worker import Worker

from workflows import DocumentProcessingWorkflow
from activities import ALL_ACTIVITIES

TEMPORAL_ADDRESS = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
TASK_QUEUE = "document-processing"


async def run_worker():
    print(f"Connecting to Temporal at {TEMPORAL_ADDRESS}...")
    client = await Client.connect(TEMPORAL_ADDRESS)
    print(f"Connected. Starting worker on queue '{TASK_QUEUE}'...")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[DocumentProcessingWorkflow],
        activities=ALL_ACTIVITIES,
    )

    print(f"Worker listening on '{TASK_QUEUE}' — ready for documents.")
    await worker.run()


def main():
    asyncio.run(run_worker())


if __name__ == "__main__":
    main()
