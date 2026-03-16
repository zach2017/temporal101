"""Auto-generated Temporal client: client"""

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
        demoworflow,
    )
except ImportError:
    from types import *
    from workflows import (
        demoworflow,
    )

logger = logging.getLogger(__name__)


class ClientClient:
    """
    Typed Temporal client: client
    Default mode: async
    Allowed workflows: demoworflow
    """

    def __init__(self, client: Client):
        self._client = client

    @classmethod
    async def connect(cls) -> '{snake_to_pascal(name.replace('-', '_'))}Client':
        """Connect to Temporal server."""
        client = await Client.connect(
            "localhost:7233",
            namespace="default",
        )
        return cls(client)

    async def start_demoworflow(
        self,
        input: str,
        workflow_id: str,
    ) -> WorkflowHandle:
        """Start demoworflow — returns handle (non-blocking)."""
        return await self._client.start_workflow(
            demoworflow.run,
            input,
            id=workflow_id,
            task_queue="main-queue",
        )

    async def execute_demoworflow(
        self,
        input: str,
        workflow_id: str,
    ) -> str:
        """Execute demoworflow — blocks until result."""
        return await self._client.execute_workflow(
            demoworflow.run,
            input,
            id=workflow_id,
            task_queue="main-queue",
        )
