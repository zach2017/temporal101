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
class ProcessOrder:
    """
    End-to-end order processing with saga-style compensation.
    Mode: async
    """

    def __init__(self) -> None:
        self._status: str = 'pending'
        self._result: Optional[Any] = None

    @workflow.run
    async def run(self, input: OrderInput) -> str:

        # ── Step: validate (activity) ──
        validated_order = await workflow.execute_activity(
            validate_order,
            input,
            start_to_close_timeout=timedelta(seconds=10),
        )

        # ── Step: reserve (activity) ──
        reserved = await workflow.execute_activity(
            reserve_inventory,
            input,
            start_to_close_timeout=timedelta(seconds=15),
        )

        # ── Step: payment (activity) ──
        payment_result = await workflow.execute_activity(
            capture_payment,
            input,
            start_to_close_timeout=timedelta(minutes=3),
            heartbeat_timeout=timedelta(seconds=30),
        )

        # ── Step: fulfill (activity) ──
        shipment = await workflow.execute_activity(
            fulfill_order,
            input,
            start_to_close_timeout=timedelta(hours=23),
            heartbeat_timeout=timedelta(minutes=5),
            task_queue="fulfillment",
        )

        # ── Step: notify (activity) ──
        notified = await workflow.execute_activity(
            send_notification,
            input,
            start_to_close_timeout=timedelta(seconds=30),
        )

        self._status = 'completed'
        return self._result


@workflow.defn
class CleanupStaleOrders:
    """
    Runs daily to cancel stuck orders older than 24h.
    Mode: cron
    Cron: 0 3 * * *
    """

    def __init__(self) -> None:
        self._status: str = 'pending'
        self._result: Optional[Any] = None

    @workflow.run
    async def run(self, input: None) -> int:
        # No steps defined — implement workflow logic here
        raise NotImplementedError('CleanupStaleOrders not yet implemented')
