"""Auto-generated Temporal worker: fulfillment-worker"""

import asyncio
import signal
import logging
from datetime import timedelta
from temporalio.client import Client
from temporalio.worker import Worker

# Import activities & workflows
try:
    from .activities import (
        fulfill_order,
        send_notification,
    )
    from .workflows import (
        ProcessOrder,
    )
except ImportError:
    from activities import (
        fulfill_order,
        send_notification,
    )
    from workflows import (
        ProcessOrder,
    )

TASK_QUEUE = "fulfillment"
NAMESPACE = "orders-prod"
MAX_CONCURRENT_ACTIVITIES = 25
MAX_CONCURRENT_WORKFLOW_TASKS = 200

logger = logging.getLogger(__name__)


async def run_worker():
    """Start the fulfillment-worker worker."""
    logger.info(f'Connecting to Temporal at localhost:7233, namespace={NAMESPACE}')
    client = await Client.connect(
        "localhost:7233",
        namespace=NAMESPACE,
    )

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        activities=[
            fulfill_order,
            send_notification,
        ],
        workflows=[
            ProcessOrder,
        ],
        max_concurrent_activities=MAX_CONCURRENT_ACTIVITIES,
        max_concurrent_workflow_tasks=MAX_CONCURRENT_WORKFLOW_TASKS,
    )

    logger.info(f'Worker "fulfillment-worker" started on queue={TASK_QUEUE}')

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
