"""
Worker Registry — discovers and registers all Temporal workers.

To add a new worker:
  1. Create a new package under workers/ (e.g., workers/my_task/)
  2. Inside it, define workflows and activities.
  3. Create a __init__.py that exposes a `register` dict:
       register = {
           "task_queue": "my-task-queue",
           "workflows": [MyWorkflow],
           "activities": [my_activity],
       }
  4. Add the module name to WORKER_MODULES below.

The runner will pick it up automatically.
"""

import importlib
import logging
from dataclasses import dataclass, field
from typing import Any

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────
#  ADD NEW WORKER MODULES HERE
# ──────────────────────────────────────────────
WORKER_MODULES: list[str] = [
    "workers.hello_world",
    # "workers.data_pipeline",   # ← example of future worker
    # "workers.email_sender",    # ← example of future worker
]


@dataclass
class WorkerRegistration:
    """Holds everything needed to start a Temporal worker."""

    task_queue: str
    workflows: list[Any] = field(default_factory=list)
    activities: list[Any] = field(default_factory=list)


def discover_workers() -> list[WorkerRegistration]:
    """Import each module in WORKER_MODULES and collect registrations."""
    registrations: list[WorkerRegistration] = []

    for module_path in WORKER_MODULES:
        try:
            mod = importlib.import_module(module_path)
            reg = getattr(mod, "register", None)

            if reg is None:
                logger.warning(
                    "Module %s has no `register` dict — skipping.", module_path
                )
                continue

            registrations.append(
                WorkerRegistration(
                    task_queue=reg["task_queue"],
                    workflows=reg.get("workflows", []),
                    activities=reg.get("activities", []),
                )
            )
            logger.info(
                "Registered worker: %s (queue=%s, workflows=%d, activities=%d)",
                module_path,
                reg["task_queue"],
                len(reg.get("workflows", [])),
                len(reg.get("activities", [])),
            )

        except Exception:
            logger.exception("Failed to load worker module %s", module_path)

    return registrations
