"""
Integration tests for PythonHelloWorkflow using Temporal's test server.

These tests spin up an in-process Temporal server (no Docker needed)
and execute workflows end-to-end, including activity dispatch and retries.

Test Flow:
  1. WorkflowEnvironment starts an embedded Temporal server (time-skipping mode)
  2. A Worker is created and registered with workflow + activity implementations
  3. The test starts a workflow via the Client
  4. The embedded server dispatches the workflow task to the Worker
  5. The Worker executes the workflow, which schedules the activity
  6. The activity executes and returns a result
  7. The workflow completes and the result is returned to the Client
  8. Assertions validate the full round-trip

Time-skipping mode:
  The test server can fast-forward through timers and retry backoff
  so tests that involve retries or delays complete instantly.
"""

import pytest

from temporalio import activity
from temporalio.testing import WorkflowEnvironment
from temporalio.worker import Worker

from workflows import PythonHelloWorkflow
from activities import compose_greeting


TASK_QUEUE = "python-hello-queue-test"


class TestPythonHelloWorkflowIntegration:
    """Full workflow integration tests with embedded Temporal server."""

    @pytest.mark.asyncio
    async def test_workflow_completes_successfully(self, workflow_env: WorkflowEnvironment):
        """Workflow should execute activity and return greeting."""
        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[compose_greeting],
        ):
            result = await workflow_env.client.execute_workflow(
                PythonHelloWorkflow.run,
                "World",
                id="test-wf-001",
                task_queue=TASK_QUEUE,
            )

        assert "Hello World" in result
        assert "Python Worker" in result

    @pytest.mark.asyncio
    async def test_workflow_with_different_names(self, workflow_env: WorkflowEnvironment):
        """Workflow should correctly pass name through to activity."""
        names = ["Alice", "Bob", "名前", "O'Brien", ""]

        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[compose_greeting],
        ):
            for i, name in enumerate(names):
                result = await workflow_env.client.execute_workflow(
                    PythonHelloWorkflow.run,
                    name,
                    id=f"test-names-{i}",
                    task_queue=TASK_QUEUE,
                )
                assert f"Hello {name}" in result

    @pytest.mark.asyncio
    async def test_workflow_returns_string_type(self, workflow_env: WorkflowEnvironment):
        """Workflow result should be a string (important for cross-language compat)."""
        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[compose_greeting],
        ):
            result = await workflow_env.client.execute_workflow(
                PythonHelloWorkflow.run,
                "TypeTest",
                id="test-type-001",
                task_queue=TASK_QUEUE,
            )

        assert isinstance(result, str)

    @pytest.mark.asyncio
    async def test_workflow_result_contains_metadata(self, workflow_env: WorkflowEnvironment):
        """Workflow result should include host, python version, and timestamp."""
        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[compose_greeting],
        ):
            result = await workflow_env.client.execute_workflow(
                PythonHelloWorkflow.run,
                "Meta",
                id="test-meta-001",
                task_queue=TASK_QUEUE,
            )

        assert "host=" in result
        assert "python=" in result
        assert "time=" in result


class TestPythonHelloWorkflowWithMockedActivity:
    """Workflow tests with mocked activity to isolate workflow logic."""

    @pytest.mark.asyncio
    async def test_workflow_calls_activity(self, workflow_env: WorkflowEnvironment):
        """Workflow should delegate to compose_greeting activity."""

        # Define a mock activity with the same name/signature
        @activity.defn(name="compose_greeting")
        async def mock_compose_greeting(name: str) -> str:
            return f"MOCKED: Hello {name}"

        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[mock_compose_greeting],
        ):
            result = await workflow_env.client.execute_workflow(
                PythonHelloWorkflow.run,
                "MockUser",
                id="test-mock-001",
                task_queue=TASK_QUEUE,
            )

        assert result == "MOCKED: Hello MockUser"

    @pytest.mark.asyncio
    async def test_workflow_retries_on_activity_failure(self, workflow_env: WorkflowEnvironment):
        """Workflow should retry activity up to 3 times on failure."""
        call_count = 0

        @activity.defn(name="compose_greeting")
        async def flaky_activity(name: str) -> str:
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                raise RuntimeError(f"Transient error (attempt {call_count})")
            return f"Success after {call_count} attempts for {name}"

        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[flaky_activity],
        ):
            result = await workflow_env.client.execute_workflow(
                PythonHelloWorkflow.run,
                "RetryUser",
                id="test-retry-001",
                task_queue=TASK_QUEUE,
            )

        assert "Success after 3 attempts" in result
        assert call_count == 3

    @pytest.mark.asyncio
    async def test_workflow_fails_after_max_retries(self, workflow_env: WorkflowEnvironment):
        """Workflow should fail if activity exhausts all 3 retry attempts."""

        @activity.defn(name="compose_greeting")
        async def always_fail(name: str) -> str:
            raise RuntimeError("Persistent failure")

        async with Worker(
            workflow_env.client,
            task_queue=TASK_QUEUE,
            workflows=[PythonHelloWorkflow],
            activities=[always_fail],
        ):
            from temporalio.client import WorkflowFailureError

            with pytest.raises(WorkflowFailureError):
                await workflow_env.client.execute_workflow(
                    PythonHelloWorkflow.run,
                    "FailUser",
                    id="test-fail-001",
                    task_queue=TASK_QUEUE,
                )
