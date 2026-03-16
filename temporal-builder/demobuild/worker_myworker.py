"""Auto-generated Temporal worker: myworker"""

import asyncio
import signal
import logging
from datetime import timedelta
from temporalio.client import Client
from temporalio.worker import Worker

# Import activities & workflows
try:
    from .activities import (
        upload,
        checktype,
    )
    from .workflows import (
        demoworflow,
    )
except ImportError:
    from activities import (
        upload,
        checktype,
    )
    from workflows import (
        demoworflow,
    )

TASK_QUEUE = "main-queue"
NAMESPACE = "default"
MAX_CONCURRENT_ACTIVITIES = 200
MAX_CONCURRENT_WORKFLOW_TASKS = 200

logger = logging.getLogger(__name__)


async def run_worker():
    """Start the myworker worker."""
    logger.info(f'Connecting to Temporal at localhost:7233, namespace={NAMESPACE}')
    client = await Client.connect(
        "localhost:7233",
        namespace=NAMESPACE,
    )

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        activities=[
            upload,
            checktype,
        ],
        workflows=[
            demoworflow,
        ],
        max_concurrent_activities=MAX_CONCURRENT_ACTIVITIES,
        max_concurrent_workflow_tasks=MAX_CONCURRENT_WORKFLOW_TASKS,
    )

    logger.info(f'Worker "myworker" started on queue={TASK_QUEUE}')

    shutdown_event = asyncio.Event()

    def _signal_handler():
        logger.info('Shutdown signal received')
        shutdown_event.set()

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _signal_handler)

    async with worker:
        await shutdown_event.wait()
        logger.info('Draining in-flight work (timeout: 30s)')


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(run_worker())
