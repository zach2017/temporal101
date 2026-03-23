"""Worker – PDF Extraction. Listens on ``pdf-extraction-queue``."""

from __future__ import annotations
import asyncio
import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.pdf_activities import (
    build_ocr_requests, extract_images_from_pdf, extract_text_from_pdf,
    store_extracted_text, store_extracted_image,
)
from application.workflows.pdf_extraction_workflow import PdfExtractionWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()

async def main() -> None:
    logger.info("worker.pdf.starting", task_queue=settings.temporal_pdf_task_queue)
    client = await Client.connect(settings.temporal_host, namespace=settings.temporal_namespace)
    worker = Worker(
        client, task_queue=settings.temporal_pdf_task_queue,
        workflows=[PdfExtractionWorkflow],
        activities=[extract_text_from_pdf, extract_images_from_pdf,
                    store_extracted_text, store_extracted_image, build_ocr_requests],
        max_concurrent_activities=settings.max_concurrent_activities,
    )
    logger.info("worker.pdf.running")
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())
