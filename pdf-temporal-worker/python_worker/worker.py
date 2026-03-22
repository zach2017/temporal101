"""
Temporal Worker entry-point.

Usage:
    python worker.py                        # default localhost:7233
    python worker.py --host temporal:7233   # custom host
"""

import argparse
import asyncio
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from workflows import PdfToTextWorkflow, TASK_QUEUE
from activities import fetch_pdf_activity, process_and_store_activity

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
log = logging.getLogger("pdf_worker")


async def run(host: str) -> None:
    log.info("Connecting to Temporal @ %s …", host)
    client = await Client.connect(host)

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[PdfToTextWorkflow],
        activities=[
            fetch_pdf_activity,
            process_and_store_activity,
        ],
    )
    log.info("Listening on queue [%s]", TASK_QUEUE)
    await worker.run()


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="PDF-to-Text Temporal Worker")
    ap.add_argument("--host", default="localhost:7233",
                    help="Temporal server address")
    asyncio.run(run(ap.parse_args().host))
