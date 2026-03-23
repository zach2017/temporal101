"""
Shared Temporal retry and timeout policy.

Every workflow imports ``activity_opts`` and ``child_workflow_opts``
from here so retry limits, timeouts, and backoff are configured
in one place and driven by ENV.
"""

from __future__ import annotations

from datetime import timedelta

from temporalio.common import RetryPolicy


def build_retry_policy(max_attempts: int = 2) -> RetryPolicy:
    """
    Build a RetryPolicy with the given max attempts.

    ``max_attempts=2`` means: 1 initial attempt + 1 retry = 2 total.
    Non-retryable error types (FileNotFoundError, etc.) skip retries
    entirely regardless of this setting.
    """
    return RetryPolicy(
        maximum_attempts=max_attempts,
        initial_interval=timedelta(seconds=1),
        backoff_coefficient=2.0,
        maximum_interval=timedelta(seconds=30),
        non_retryable_error_types=[
            "FileNotFoundError",
            "FileNotReadableError",
        ],
    )
