import asyncio
import logging
from datetime import datetime

from temporalio import activity

log = logging.getLogger(__name__)


@activity.defn
async def process_async(job_name: str) -> str:
    """Background processing activity for the async workflow. Heartbeats progress."""
    log.info("[Async] Starting background job: %s", job_name)

    for i in range(1, 6):
        await asyncio.sleep(1)
        activity.heartbeat(f"Processing step {i}/5")
        log.info("[Async] Job '%s' progress: %d/5", job_name, i)

    result = f"Job '{job_name}' completed at {datetime.utcnow().isoformat()}Z by Python Worker"
    log.info("[Async] %s", result)
    return result


@activity.defn
async def extract_data(source: str) -> str:
    """ETL Step 1: Extract data from source. Heartbeats percentage."""
    log.info("[ETL] Extracting data from: %s", source)

    for pct in range(0, 101, 20):
        await asyncio.sleep(0.8)
        activity.heartbeat(f"Extracting: {pct}%")

    result = f"RAW_DATA[source={source},rows=10000]"
    log.info("[ETL] Extraction complete: %s", result)
    return result


@activity.defn
async def transform_data(raw_data: str) -> str:
    """ETL Step 2: Transform raw data. Heartbeats percentage."""
    log.info("[ETL] Transforming: %s", raw_data)

    for pct in range(0, 101, 25):
        await asyncio.sleep(0.6)
        activity.heartbeat(f"Transforming: {pct}%")

    result = f"TRANSFORMED[{raw_data},cleaned=True,normalized=True]"
    log.info("[ETL] Transform complete: %s", result)
    return result


@activity.defn
async def load_data(transformed_data: str) -> str:
    """ETL Step 3: Load data to warehouse. Heartbeats percentage."""
    log.info("[ETL] Loading: %s", transformed_data)

    for pct in range(0, 101, 33):
        await asyncio.sleep(0.7)
        activity.heartbeat(f"Loading: {min(pct, 100)}%")

    result = "LOADED[rows=10000,destination=warehouse]"
    log.info("[ETL] Load complete: %s", result)
    return result


@activity.defn
async def send_notification(summary: str) -> str:
    """ETL Step 4: Send completion notification."""
    log.info("[ETL] Sending notification: %s", summary)
    await asyncio.sleep(0.5)
    return f"Notification sent: {summary}"
