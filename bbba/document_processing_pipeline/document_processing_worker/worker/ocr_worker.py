"""Worker – Image OCR. Listens on ``image-ocr-queue``."""

from __future__ import annotations
import asyncio
import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.ocr_activities import ocr_extract_text_from_image, copy_source_image
from application.activities.pdf_activities import store_extracted_text
from application.workflows.image_ocr_workflow import ImageOcrWorkflow
from application.workflows.image_document_workflow import ImageDocumentWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()

async def main() -> None:
    logger.info("worker.ocr.starting", task_queue=settings.temporal_ocr_task_queue)
    client = await Client.connect(settings.temporal_host, namespace=settings.temporal_namespace)
    worker = Worker(
        client, task_queue=settings.temporal_ocr_task_queue,
        workflows=[ImageOcrWorkflow, ImageDocumentWorkflow],
        activities=[ocr_extract_text_from_image, copy_source_image, store_extracted_text],
        max_concurrent_activities=settings.max_concurrent_activities,
    )
    logger.info("worker.ocr.running")
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())
