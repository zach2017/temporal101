from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from processing_activities import process_async


@workflow.defn
class AsyncProcessingWorkflow:
    """
    Async (fire-and-forget) workflow.

    The caller starts this and gets a workflow ID immediately without waiting.
    Progress can be polled at any time via the getStatus query.
    """

    def __init__(self) -> None:
        self._status = "QUEUED"

    @workflow.run
    async def run_job(self, job_name: str) -> str:
        self._status = "RUNNING"
        result = await workflow.execute_activity(
            process_async,
            job_name,
            start_to_close_timeout=timedelta(seconds=30),
            heartbeat_timeout=timedelta(seconds=10),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )
        self._status = "COMPLETED"
        return result

    @workflow.query(name="getStatus")
    def get_status(self) -> str:
        return self._status
