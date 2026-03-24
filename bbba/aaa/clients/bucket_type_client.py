"""Client to start BucketType workflows."""

import asyncio
import logging
from uuid import uuid4

from temporalio.client import Client

from workflows.bucket_type_workflow import BucketTypeWorkflow

TASK_QUEUE = "bucket-type-task-queue"


async def run_bucket_type_workflow(bucket_name: str = "SAFE") -> dict:
    """Execute the BucketTypeWorkflow to resolve and check a bucket."""
    client = await Client.connect("localhost:7233")

    result = await client.execute_workflow(
        BucketTypeWorkflow.run,
        bucket_name,
        id=f"bucket-type-{uuid4()}",
        task_queue=TASK_QUEUE,
    )

    print(f"BucketType result: bucket={result['bucket']}, is_safe={result['is_safe']}")
    return result


async def main() -> None:
    logging.basicConfig(level=logging.INFO)
    await run_bucket_type_workflow("SAFE")
    await run_bucket_type_workflow("TEMP")


if __name__ == "__main__":
    asyncio.run(main())
