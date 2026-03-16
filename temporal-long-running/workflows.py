from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from activities import process_data, notify_completion


@workflow.defn
class LongRunningWorkflow:
    """
    Workflow that:
      1. Runs a long multi-step data processing activity (with heartbeat)
      2. Sends a completion notification
    """

    @workflow.run
    async def run(self, payload: dict) -> str:
        workflow.logger.info(f"Workflow started with payload: {payload}")

        # ── Step 1: Long processing activity ──────────────────────────────────
        # schedule_to_close_timeout: total wall-clock budget for the activity
        # heartbeat_timeout: if no heartbeat arrives within this window,
        #                    Temporal marks the activity as failed/timed-out
        summary = await workflow.execute_activity(
            process_data,
            payload,
            schedule_to_close_timeout=timedelta(minutes=10),
            heartbeat_timeout=timedelta(seconds=15),   # must heartbeat every 15s
            retry_policy=RetryPolicy(maximum_attempts=3),
        )

        workflow.logger.info(f"Processing done: {summary}")

        # ── Step 2: Notify ─────────────────────────────────────────────────────
        message = await workflow.execute_activity(
            notify_completion,
            summary,
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )

        return message
