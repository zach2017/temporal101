"""
Worker – Document Conversion (office docs, text, RTF, HTML, ebook, email).

Listens on ``document-conversion-queue``.

Run:  python -m worker.conversion_worker
"""

from __future__ import annotations

import asyncio

import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.conversion_activities import convert_document_to_text
from application.activities.pdf_activities import store_extracted_text_to_s3
from application.workflows.document_conversion_workflow import DocumentConversionWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()


async def main() -> None:
    logger.info(
        "worker.conversion.starting",
        host=settings.temporal_host,
        task_queue=settings.temporal_conversion_task_queue,
    )

    client = await Client.connect(
        settings.temporal_host,
        namespace=settings.temporal_namespace,
    )

    worker = Worker(
        client,
        task_queue=settings.temporal_conversion_task_queue,
        workflows=[DocumentConversionWorkflow],
        activities=[
            convert_document_to_text,
            store_extracted_text_to_s3,
        ],
        max_concurrent_activities=settings.max_concurrent_activities,
    )

    logger.info("worker.conversion.running")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
