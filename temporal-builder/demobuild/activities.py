"""Auto-generated Temporal activity stubs."""

from __future__ import annotations
from datetime import timedelta
from typing import Any
from temporalio import activity
from temporalio.common import RetryPolicy

# Import generated types
try:
    from .types import *
except ImportError:
    from types import *

AUTO_UPLOAD_RETRY = RetryPolicy(
    initial_interval=timedelta(seconds=1),
    backoff_coefficient=2,
    maximum_interval=timedelta(seconds=30),
    maximum_attempts=3,
)

AUTO_CHECKTYPE_RETRY = RetryPolicy(
    initial_interval=timedelta(seconds=1),
    backoff_coefficient=2,
    maximum_interval=timedelta(seconds=30),
    maximum_attempts=3,
)


@activity.defn
async def upload(input: person) -> person:
    """
    
    Mode: sync
    """
    # ╔══════════════════════════════════════════╗
    # ║  TODO: Implement upload
    # ╚══════════════════════════════════════════╝
    raise NotImplementedError('upload not yet implemented')


@activity.defn
async def checktype(input: person) -> str:
    """
    
    Mode: sync
    """
    # ╔══════════════════════════════════════════╗
    # ║  TODO: Implement checktype
    # ╚══════════════════════════════════════════╝
    raise NotImplementedError('checktype not yet implemented')
