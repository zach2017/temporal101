"""
Hello World activities.

This demonstrates a *long-running* Temporal activity that:
  • Reports progress via heartbeats so the server knows it's alive.
  • Can resume from the last heartbeat if the worker restarts mid-flight.
  • Simulates real work with incremental steps.
"""

import asyncio
import logging
from dataclasses import dataclass

from temporalio import activity

logger = logging.getLogger(__name__)

TOTAL_STEPS = 20          # number of processing steps
STEP_DELAY_SECS = 3.0     # seconds of "work" per step


@dataclass
class HelloInput:
    name: str
    total_steps: int = TOTAL_STEPS
    step_delay: float = STEP_DELAY_SECS


@dataclass
class HelloResult:
    greeting: str
    steps_completed: int


@activity.defn
async def say_hello_long_running(input: HelloInput) -> HelloResult:
    """
    A long-running activity that greets someone while performing
    multi-step processing.  Each step heartbeats its progress so
    Temporal can track liveness and enable resumption.
    """
    logger.info("Starting long-running hello for %s (%d steps)", input.name, input.total_steps)

    # ── Resume from last heartbeat if this is a retry ────────────
    start_step = 0
    heartbeat_details = activity.info().heartbeat_details
    if heartbeat_details:
        start_step = int(heartbeat_details[0])
        logger.info("Resuming from step %d (heartbeat recovery)", start_step)

    # ── Simulate long-running work ───────────────────────────────
    for step in range(start_step, input.total_steps):
        # Check for cancellation before each unit of work
        activity.heartbeat(step)

        logger.info(
            "[%s] Processing step %d/%d …",
            input.name,
            step + 1,
            input.total_steps,
        )

        # Simulate CPU / IO work
        await asyncio.sleep(input.step_delay)

    greeting = f"Hello, {input.name}! Completed {input.total_steps} steps of work."
    logger.info("Finished: %s", greeting)

    return HelloResult(greeting=greeting, steps_completed=input.total_steps)
