"""
Worker – Document Intake (MIME detection + routing).

Listens on ``document-intake-queue``.  Runs the DocumentIntakeWorkflow
which detects MIME type and dispatches to the appropriate child workflow.

Run:  python -m worker.intake_worker
"""

from __future__ import annotations

import asyncio

import structlog
from temporalio.client import Client
from temporalio.worker import Worker

from application.activities.mime_activities import detect_mime_type
from application.workflows.document_intake_workflow import DocumentIntakeWorkflow
from infrastructure.config import settings

logger = structlog.get_logger()


async def main() -> None:
    logger.info(
        "worker.intake.starting",
        host=settings.temporal_host,
        task_queue=settings.temporal_task_queue,
    )

    client = await Client.connect(
        settings.temporal_host,
        namespace=settings.temporal_namespace,
    )

    worker = Worker(
        client,
        task_queue=settings.temporal_task_queue,
        workflows=[DocumentIntakeWorkflow],
        activities=[detect_mime_type],
        max_concurrent_activities=settings.max_concurrent_activities,
    )

    logger.info("worker.intake.running")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
