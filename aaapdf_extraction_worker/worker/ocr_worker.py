"""
Worker process – Image OCR Worker (STUB).

Listens on the ``image-ocr-queue`` task queue and executes the
ImageOcrWorkflow + its OCR activity.

Run:
    python -m worker.ocr_worker
"""

from __future__ import annotations

import asyncio

import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.ocr_activities import ocr_extract_text_from_image
from application.workflows.image_ocr_workflow import ImageOcrWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()


async def main() -> None:
    logger.info(
        "worker.ocr.starting",
        host=settings.temporal_host,
        namespace=settings.temporal_namespace,
        task_queue=settings.temporal_ocr_task_queue,
    )

    client = await Client.connect(
        settings.temporal_host,
        namespace=settings.temporal_namespace,
    )

    worker = Worker(
        client,
        task_queue=settings.temporal_ocr_task_queue,
        workflows=[ImageOcrWorkflow],
        activities=[ocr_extract_text_from_image],
        max_concurrent_activities=settings.max_concurrent_activities,
    )

    logger.info("worker.ocr.running")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
