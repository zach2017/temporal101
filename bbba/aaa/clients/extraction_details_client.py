"""Client to start ExtractionDetails workflows."""

import asyncio
import logging
from uuid import uuid4

from temporalio.client import Client

from models import ExtractionDetails
from workflows.extraction_details_workflow import ExtractionDetailsWorkflow

TASK_QUEUE = "extraction-details-task-queue"


async def run_extraction_details_workflow() -> ExtractionDetails:
    """Execute the ExtractionDetailsWorkflow and return the normalized result."""
    client = await Client.connect("localhost:7233")

    details = ExtractionDetails(
        doc_id="doc-abc-123",
        page_number=3,
        key="  Invoice_Total  ",
        image_index=0,
    )

    result = await client.execute_workflow(
        ExtractionDetailsWorkflow.run,
        details,
        id=f"extraction-details-{uuid4()}",
        task_queue=TASK_QUEUE,
    )

    print(
        f"ExtractionDetails result: doc_id={result.doc_id}, "
        f"page={result.page_number}, key={result.key}, "
        f"image_index={result.image_index}"
    )
    return result


async def main() -> None:
    logging.basicConfig(level=logging.INFO)
    await run_extraction_details_workflow()


if __name__ == "__main__":
    asyncio.run(main())
