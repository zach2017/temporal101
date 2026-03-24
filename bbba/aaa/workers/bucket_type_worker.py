"""Temporal worker for BucketType workflows and activities."""

import asyncio
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from activities import is_safe_bucket, resolve_bucket
from workflows.bucket_type_workflow import BucketTypeWorkflow

TASK_QUEUE = "bucket-type-task-queue"


async def main() -> None:
    logging.basicConfig(level=logging.INFO)

    client = await Client.connect("localhost:7233")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[BucketTypeWorkflow],
        activities=[resolve_bucket, is_safe_bucket],
    )

    logging.info(f"Starting BucketType worker on queue: {TASK_QUEUE}")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
