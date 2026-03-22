"""
CLI client – starts a PdfExtractionWorkflow execution.

Usage:
    python -m worker.start_workflow --file-name report.pdf --file-location /data/report.pdf
"""

from __future__ import annotations

import argparse
import asyncio
import json
import uuid

import structlog
from temporalio.client import Client

from infrastructure.config import settings

logger = structlog.get_logger()


async def start(file_name: str, file_location: str) -> None:
    client = await Client.connect(
        settings.temporal_host,
        namespace=settings.temporal_namespace,
    )

    workflow_id = f"pdf-extract-{file_name}-{uuid.uuid4().hex[:8]}"

    logger.info(
        "client.start_workflow",
        workflow_id=workflow_id,
        file_name=file_name,
        file_location=file_location,
    )

    result = await client.execute_workflow(
        "PdfExtractionWorkflow",
        args=[file_name, file_location],
        id=workflow_id,
        task_queue=settings.temporal_task_queue,
    )

    print(json.dumps(result, indent=2, default=str))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Start a PDF extraction workflow"
    )
    parser.add_argument("--file-name", required=True, help="PDF file name")
    parser.add_argument(
        "--file-location", required=True, help="Absolute path to the PDF"
    )
    args = parser.parse_args()
    asyncio.run(start(args.file_name, args.file_location))


if __name__ == "__main__":
    main()
