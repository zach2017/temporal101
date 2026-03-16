"""Auto-generated Temporal workflow definitions."""

from __future__ import annotations
from datetime import timedelta
from typing import Any, Optional
from temporalio import workflow
from temporalio.common import RetryPolicy

# Import activities for type-safe execution
with workflow.unsafe.imports_passed_through():
    try:
        from .activities import *
        from .types import *
    except ImportError:
        from activities import *
        from types import *


@workflow.defn
class demoworflow:
    """
    
    Mode: async
    """

    def __init__(self) -> None:
        self._status: str = 'pending'
        self._result: Optional[Any] = None

    @workflow.run
    async def run(self, input: str) -> str:

        # ── Step: 1 (activity) ──
        file = await workflow.execute_activity(
            upload,
            input,
            start_to_close_timeout=timedelta(seconds=30),
        )

        # ── Step: 2 (activity) ──
        answer = await workflow.execute_activity(
            checktype,
            input,
            start_to_close_timeout=timedelta(seconds=30),
        )

        self._status = 'completed'
        return self._result
