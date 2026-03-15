from __future__ import annotations

import asyncio
import os

from temporalio.client import Client
from temporalio.worker import Worker

from activities import (
    document_exists,
    extract_text_from_pdf,
    read_text_file,
    save_text_to_file,
)
from workflows import DocumentDownloadWorkflow, PdfProcessingWorkflow

TEMPORAL_ADDRESS = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
TASK_QUEUE = "DOC_PROCESSING_QUEUE"


async def main() -> None:
    print(f"Connecting to Temporal at {TEMPORAL_ADDRESS} ...")
    client = await Client.connect(TEMPORAL_ADDRESS)
    print(f"Connected. Starting worker on queue: {TASK_QUEUE}")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[PdfProcessingWorkflow, DocumentDownloadWorkflow],
        activities=[
            extract_text_from_pdf,
            save_text_to_file,
            read_text_file,
            document_exists,
        ],
    )
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
