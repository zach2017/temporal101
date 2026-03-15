#!/usr/bin/env python3
"""
Starter script — kicks off a HelloWorldWorkflow execution.

Usage:
    python start_hello.py [NAME]
"""

import asyncio
import sys

from temporalio.client import Client

from config import settings
from workers.hello_world.workflows import HelloWorldWorkflow


async def main() -> None:
    name = sys.argv[1] if len(sys.argv) > 1 else "World"

    client = await Client.connect(
        settings.server_url,
        namespace=settings.namespace,
    )

    print(f"Starting HelloWorldWorkflow for '{name}' …")

    handle = await client.start_workflow(
        HelloWorldWorkflow.run,
        name,
        id=f"hello-world-{name.lower().replace(' ', '-')}",
        task_queue="hello-world-python-queue",
    )

    print(f"Workflow started: id={handle.id}, run_id={handle.result_run_id}")
    print("Waiting for result (this is a long-running task) …")

    result = await handle.result()
    print(f"Result: {result}")


if __name__ == "__main__":
    asyncio.run(main())
