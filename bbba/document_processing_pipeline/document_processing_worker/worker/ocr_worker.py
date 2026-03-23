"""
Worker – Image OCR (shared image-to-text).

Listens on ``image-ocr-queue``.  Runs both:
  - ImageOcrWorkflow       (child of PdfExtractionWorkflow)
  - ImageDocumentWorkflow  (standalone image files from intake)

Run:  python -m worker.ocr_worker
"""

from __future__ import annotations

import asyncio

import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.ocr_activities import (
    ocr_extract_text_from_image,
    upload_image_for_ocr,
)
from application.activities.pdf_activities import store_extracted_text_to_s3
from application.workflows.image_ocr_workflow import ImageOcrWorkflow
from application.workflows.image_document_workflow import ImageDocumentWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()


async def main() -> None:
    logger.info(
        "worker.ocr.starting",
        host=settings.temporal_host,
        task_queue=settings.temporal_ocr_task_queue,
    )

    client = await Client.connect(
        settings.temporal_host,
        namespace=settings.temporal_namespace,
    )

    worker = Worker(
        client,
        task_queue=settings.temporal_ocr_task_queue,
        workflows=[ImageOcrWorkflow, ImageDocumentWorkflow],
        activities=[
            ocr_extract_text_from_image,
            upload_image_for_ocr,
            store_extracted_text_to_s3,
        ],
        max_concurrent_activities=settings.max_concurrent_activities,
    )

    logger.info("worker.ocr.running")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
