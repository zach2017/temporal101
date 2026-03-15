"""
Hello World workflow.

Orchestrates the long-running say_hello activity with appropriate
timeouts configured for a task that takes several minutes.
"""

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from workers.hello_world.activities import (
        HelloInput,
        HelloResult,
        say_hello_long_running,
    )


@workflow.defn
class HelloWorldWorkflow:
    """
    A workflow that runs a long-running greeting activity.

    Start-to-close timeout is generous because this is a multi-step
    long-running task.  The heartbeat timeout is tight so Temporal
    quickly detects a dead worker and reschedules.
    """

    @workflow.run
    async def run(self, name: str) -> HelloResult:
        workflow.logger.info("HelloWorldWorkflow started for %s", name)

        result = await workflow.execute_activity(
            say_hello_long_running,
            HelloInput(name=name),
            start_to_close_timeout=timedelta(minutes=30),
            heartbeat_timeout=timedelta(seconds=30),
            retry_policy=workflow.RetryPolicy(
                initial_interval=timedelta(seconds=1),
                backoff_coefficient=2.0,
                maximum_interval=timedelta(seconds=30),
                maximum_attempts=5,
            ),
        )

        workflow.logger.info("HelloWorldWorkflow completed: %s", result.greeting)
        return result
