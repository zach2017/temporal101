"""Client to start ExtractionOutput workflows."""

import asyncio
import logging
from uuid import uuid4

from temporalio.client import Client

from models import BucketType, ExtractionDetails, ExtractionOutput
from workflows.extraction_output_workflow import (
    ExtractionOutputWorkflow,
    MergeExtractionOutputsWorkflow,
)

TASK_QUEUE = "extraction-output-task-queue"


async def run_extraction_output_workflow() -> ExtractionOutput:
    """Execute the ExtractionOutputWorkflow (validate + promote)."""
    client = await Client.connect("localhost:7233")

    output = ExtractionOutput(
        image_keys=[
            ExtractionDetails(doc_id="doc-1", page_number=1, key="logo.png", image_index=1),
            ExtractionDetails(doc_id="doc-1", page_number=2, key="chart.png", image_index=2),
        ],
        text_keys=[
            ExtractionDetails(doc_id="doc-1", page_number=1, key="title", image_index=0),
        ],
        bucket=BucketType.TEMP,
    )

    result = await client.execute_workflow(
        ExtractionOutputWorkflow.run,
        output,
        id=f"extraction-output-{uuid4()}",
        task_queue=TASK_QUEUE,
    )

    print(
        f"ExtractionOutput result: bucket={result.bucket.name}, "
        f"images={len(result.image_keys)}, texts={len(result.text_keys)}"
    )
    return result


async def run_merge_workflow() -> ExtractionOutput:
    """Execute the MergeExtractionOutputsWorkflow."""
    client = await Client.connect("localhost:7233")

    outputs = [
        ExtractionOutput(
            image_keys=[
                ExtractionDetails(doc_id="doc-1", page_number=1, key="img1.png", image_index=1),
            ],
            text_keys=[
                ExtractionDetails(doc_id="doc-1", page_number=1, key="heading", image_index=0),
            ],
            bucket=BucketType.TEMP,
        ),
        ExtractionOutput(
            image_keys=[
                ExtractionDetails(doc_id="doc-2", page_number=3, key="img2.png", image_index=1),
            ],
            text_keys=[
                ExtractionDetails(doc_id="doc-2", page_number=3, key="footer", image_index=0),
            ],
            bucket=BucketType.TEMP,
        ),
    ]

    result = await client.execute_workflow(
        MergeExtractionOutputsWorkflow.run,
        outputs,
        id=f"merge-outputs-{uuid4()}",
        task_queue=TASK_QUEUE,
    )

    print(
        f"Merged result: bucket={result.bucket.name}, "
        f"images={len(result.image_keys)}, texts={len(result.text_keys)}"
    )
    return result


async def main() -> None:
    logging.basicConfig(level=logging.INFO)
    await run_extraction_output_workflow()
    await run_merge_workflow()


if __name__ == "__main__":
    asyncio.run(main())
