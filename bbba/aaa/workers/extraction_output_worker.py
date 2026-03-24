"""Temporal worker for ExtractionOutput workflows and activities."""

import asyncio
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from activities import (
    merge_extraction_outputs,
    promote_to_safe_bucket,
    validate_extraction_output,
)
from workflows.extraction_output_workflow import (
    ExtractionOutputWorkflow,
    MergeExtractionOutputsWorkflow,
)

TASK_QUEUE = "extraction-output-task-queue"


async def main() -> None:
    logging.basicConfig(level=logging.INFO)

    client = await Client.connect("localhost:7233")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[ExtractionOutputWorkflow, MergeExtractionOutputsWorkflow],
        activities=[
            validate_extraction_output,
            promote_to_safe_bucket,
            merge_extraction_outputs,
        ],
    )

    logging.info(f"Starting ExtractionOutput worker on queue: {TASK_QUEUE}")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
