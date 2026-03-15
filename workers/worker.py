import asyncio
import os
from temporalio import activity, workflow
from temporalio.client import Client
from temporalio.worker import Worker

@activity.defn
async def get_file_size(filename: str) -> int:
    # Dummy logic: length of filename * 1024 as "bytes"
    return len(filename) * 1024

async def main():
    client = await Client.connect("temporal:7233")
    worker = Worker(
        client,
        task_queue="python-tasks",
        activities=[get_file_size],
    )
    print("Python Worker started...")
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())