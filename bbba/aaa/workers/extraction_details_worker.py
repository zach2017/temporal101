"""Temporal worker for ExtractionDetails workflows and activities."""

import asyncio
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from activities import (
    classify_extraction_source,
    normalize_extraction_details,
    validate_extraction_details,
)
from workflows.extraction_details_workflow import ExtractionDetailsWorkflow

TASK_QUEUE = "extraction-details-task-queue"


async def main() -> None:
    logging.basicConfig(level=logging.INFO)

    client = await Client.connect("localhost:7233")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[ExtractionDetailsWorkflow],
        activities=[
            validate_extraction_details,
            normalize_extraction_details,
            classify_extraction_source,
        ],
    )

    logging.info(f"Starting ExtractionDetails worker on queue: {TASK_QUEUE}")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
