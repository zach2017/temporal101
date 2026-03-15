"""
Pytest configuration and shared fixtures for the Python Temporal Worker tests.

Provides:
  - Temporal test environment with in-process server
  - Pre-configured worker for workflow tests
  - Activity environment for isolated activity tests
"""

import pytest
from temporalio.testing import WorkflowEnvironment, ActivityEnvironment


@pytest.fixture
async def workflow_env():
    """
    Start an in-process Temporal test server for workflow integration tests.

    This uses Temporal's built-in test server (no Docker or external server).
    The environment auto-starts a lightweight Temporal server with SQLite
    persistence and provides a connected Client for starting workflows.

    Yields:
        WorkflowEnvironment: Connected test environment with client access.
    """
    async with await WorkflowEnvironment.start_time_skipping() as env:
        yield env


@pytest.fixture
def activity_env():
    """
    Create an ActivityEnvironment for unit-testing activities in isolation.

    ActivityEnvironment allows calling @activity.defn functions directly
    without needing a workflow or Temporal server. It provides:
      - activity.info() context (task queue, attempt number, etc.)
      - Cancellation support for testing cancel handling
      - Heartbeat testing

    Returns:
        ActivityEnvironment: Configured activity test harness.
    """
    return ActivityEnvironment()
