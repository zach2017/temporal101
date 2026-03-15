import asyncio
import mimetypes
import os
from dataclasses import asdict, dataclass

from temporalio import activity
from temporalio.client import Client
from temporalio.worker import Worker

TEMPORAL_ADDRESS = os.getenv("TEMPORAL_ADDRESS", "temporal:7233")
TEMPORAL_NAMESPACE = os.getenv("TEMPORAL_NAMESPACE", "default")
TASK_QUEUE = os.getenv("PYTHON_ACTIVITY_TASK_QUEUE", "python-file-inspector")
SHARED_FILES_DIR = os.getenv("SHARED_FILES_DIR", "/shared-files")


@dataclass
class FileResult:
    worker: str
    filename: str
    detectedType: str
    fileSizeBytes: int
    message: str


@activity.defn
async def inspect(filename: str) -> dict:
    base = os.path.abspath(SHARED_FILES_DIR)
    target = os.path.abspath(os.path.join(base, filename))

    if not target.startswith(base) or not os.path.exists(target):
        return asdict(FileResult(
            worker="python",
            filename=filename,
            detectedType="unknown",
            fileSizeBytes=0,
            message="File not found in /shared-files"
        ))

    detected, _ = mimetypes.guess_type(target)
    detected = detected or "application/octet-stream"
    size = os.path.getsize(target)

    return asdict(FileResult(
        worker="python",
        filename=filename,
        detectedType=detected,
        fileSizeBytes=size,
        message="Processed by Python worker"
    ))


async def main() -> None:
    client = await Client.connect(TEMPORAL_ADDRESS, namespace=TEMPORAL_NAMESPACE)
    worker = Worker(client, task_queue=TASK_QUEUE, activities=[inspect])
    print(f"Python worker listening on {TASK_QUEUE} at {TEMPORAL_ADDRESS}")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
