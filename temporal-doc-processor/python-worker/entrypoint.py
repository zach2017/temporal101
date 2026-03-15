#!/usr/bin/env python3
"""
Entrypoint: runs the Temporal worker and the FastAPI API server
in parallel inside the same container.
"""
from __future__ import annotations

import asyncio
import os
import signal
import sys

import uvicorn
from temporalio.client import Client
from temporalio.worker import Worker

from activities import (
    document_exists,
    extract_text_from_pdf,
    read_text_file,
    save_text_to_file,
)
from workflows import DocumentDownloadWorkflow, PdfProcessingWorkflow

TEMPORAL_ADDRESS = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
TASK_QUEUE = "DOC_PROCESSING_QUEUE"


async def run_worker() -> None:
    """Connect to Temporal and run the worker forever."""
    print(f"[worker] Connecting to Temporal at {TEMPORAL_ADDRESS} ...")
    client = await Client.connect(TEMPORAL_ADDRESS)
    print(f"[worker] Connected. Listening on queue: {TASK_QUEUE}")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[PdfProcessingWorkflow, DocumentDownloadWorkflow],
        activities=[
            extract_text_from_pdf,
            save_text_to_file,
            read_text_file,
            document_exists,
        ],
    )
    await worker.run()


async def run_api() -> None:
    """Run the FastAPI server via uvicorn."""
    config = uvicorn.Config(
        "api:app",
        host="0.0.0.0",
        port=8080,
        log_level="info",
    )
    server = uvicorn.Server(config)
    await server.serve()


async def main() -> None:
    # Run both concurrently; if either crashes, shut down
    await asyncio.gather(run_worker(), run_api())


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nShutting down.")
        sys.exit(0)
