from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

# Import activity through the sandbox pass-through
with workflow.unsafe.imports_passed_through():
    from activities import compose_greeting


@workflow.defn
class PythonHelloWorkflow:
    """
    A simple greeting workflow executed by the Python Temporal Worker.
    Delegates to the compose_greeting activity with retry policy.
    """

    @workflow.run
    async def run(self, name: str) -> str:
        return await workflow.execute_activity(
            compose_greeting,
            name,
            start_to_close_timeout=timedelta(seconds=10),
            retry_policy=RetryPolicy(
                maximum_attempts=3,
                initial_interval=timedelta(seconds=1),
                backoff_coefficient=2.0,
            ),
        )
