import time
from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from processing_activities import (
        extract_data,
        transform_data,
        load_data,
        send_notification,
    )

ACTIVITY_OPTS = {
    "start_to_close_timeout": timedelta(minutes=5),
    "heartbeat_timeout": timedelta(seconds=30),
    "retry_policy": RetryPolicy(
        maximum_attempts=3,
        initial_interval=timedelta(seconds=2),
        backoff_coefficient=2.0,
    ),
}


@workflow.defn
class LongRunningWorkflow:
    """
    Long-running multi-step ETL pipeline.

    Demonstrates:
      - Sequential activity execution (Extract → Transform → Load → Notify)
      - Activity heartbeats for liveness detection
      - Workflow query for real-time progress tracking
      - Automatic retry on failure at any step
      - Full audit trail in Temporal event history

    If the worker crashes mid-pipeline, Temporal replays completed steps
    and resumes from exactly where it left off.
    """

    def __init__(self) -> None:
        self._progress = "PENDING|0|Waiting to start"

    @workflow.run
    async def run_pipeline(self, source: str) -> str:
        start_ms = workflow.now().timestamp()

        # ── Step 1: Extract ──────────────────────────────
        self._progress = f"RUNNING|10|Step 1/4: Extracting data from {source}"
        raw_data = await workflow.execute_activity(
            extract_data, source, **ACTIVITY_OPTS
        )
        elapsed = int(workflow.now().timestamp() - start_ms)
        self._progress = f"RUNNING|25|Step 1/4: Extraction complete ({elapsed}s)"

        # ── Step 2: Transform ────────────────────────────
        self._progress = "RUNNING|35|Step 2/4: Transforming data"
        transformed = await workflow.execute_activity(
            transform_data, raw_data, **ACTIVITY_OPTS
        )
        elapsed = int(workflow.now().timestamp() - start_ms)
        self._progress = f"RUNNING|55|Step 2/4: Transform complete ({elapsed}s)"

        # ── Step 3: Load ─────────────────────────────────
        self._progress = "RUNNING|65|Step 3/4: Loading to warehouse"
        loaded = await workflow.execute_activity(
            load_data, transformed, **ACTIVITY_OPTS
        )
        elapsed = int(workflow.now().timestamp() - start_ms)
        self._progress = f"RUNNING|80|Step 3/4: Load complete ({elapsed}s)"

        # ── Step 4: Notify ───────────────────────────────
        self._progress = "RUNNING|90|Step 4/4: Sending notification"
        elapsed = int(workflow.now().timestamp() - start_ms)
        summary = f"ETL Pipeline finished: {source} → {loaded} in {elapsed}s"
        await workflow.execute_activity(
            send_notification, summary, **ACTIVITY_OPTS
        )

        elapsed = int(workflow.now().timestamp() - start_ms)
        self._progress = f"COMPLETED|100|Done in {elapsed}s"
        return summary

    @workflow.query(name="getProgress")
    def get_progress(self) -> str:
        return self._progress
