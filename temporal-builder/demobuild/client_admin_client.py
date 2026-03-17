"""Auto-generated Temporal client: admin-client"""

from __future__ import annotations
import asyncio
import logging
from datetime import timedelta
from typing import Any, Optional
from temporalio.client import Client, TLSConfig, WorkflowHandle

# Import types & workflow classes
try:
    from .types import *
    from .workflows import (
        ProcessOrder,
        CleanupStaleOrders,
    )
except ImportError:
    from types import *
    from workflows import (
        ProcessOrder,
        CleanupStaleOrders,
    )

logger = logging.getLogger(__name__)


class AdminClientClient:
    """
    Typed Temporal client: admin-client
    Default mode: sync
    Allowed workflows: ProcessOrder, CleanupStaleOrders
    """

    def __init__(self, client: Client):
        self._client = client

    @classmethod
    async def connect(cls) -> '{snake_to_pascal(name.replace('-', '_'))}Client':
        """Connect to Temporal server."""
        tls_config = TLSConfig(
        )
        client = await Client.connect(
            "temporal.internal:7233",
            namespace="orders-prod",
            tls=tls_config,
        )
        return cls(client)

    async def start_process_order(
        self,
        input: OrderInput,
        workflow_id: str,
    ) -> WorkflowHandle:
        """Start ProcessOrder — returns handle (non-blocking)."""
        return await self._client.start_workflow(
            ProcessOrder.run,
            input,
            id=workflow_id,
            task_queue="order-processing",
        )

    async def execute_process_order(
        self,
        input: OrderInput,
        workflow_id: str,
    ) -> str:
        """Execute ProcessOrder — blocks until result."""
        return await self._client.execute_workflow(
            ProcessOrder.run,
            input,
            id=workflow_id,
            task_queue="order-processing",
        )

    async def start_cleanup_stale_orders(
        self,
        input: None,
        workflow_id: str,
    ) -> WorkflowHandle:
        """Start CleanupStaleOrders — returns handle (non-blocking)."""
        return await self._client.start_workflow(
            CleanupStaleOrders.run,
            input,
            id=workflow_id,
            task_queue="order-processing",
        )

    async def execute_cleanup_stale_orders(
        self,
        input: None,
        workflow_id: str,
    ) -> int:
        """Execute CleanupStaleOrders — blocks until result."""
        return await self._client.execute_workflow(
            CleanupStaleOrders.run,
            input,
            id=workflow_id,
            task_queue="order-processing",
        )
