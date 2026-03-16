import asyncio
import argparse
from temporalio.client import Client
from temporalio.worker import Worker

from workflows import HelloWorldWorkflow
from activities import say_hello

async def main(host: str, task_queue: str):
    print(f"Connecting to Temporal at {host} ...")
    client = await Client.connect(host)

    worker = Worker(
        client,
        task_queue=task_queue,
        workflows=[HelloWorldWorkflow],
        activities=[say_hello],
    )

    print(f"Worker started on task queue: '{task_queue}'")
    print("Waiting for tasks... (Ctrl+C to stop)\n")
    await worker.run()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Temporal Hello World Worker")
    parser.add_argument("--host", default="localhost:7233", help="Temporal server address")
    parser.add_argument("--task-queue", default="hello-world-queue", help="Task queue name")
    args = parser.parse_args()

    asyncio.run(main(args.host, args.task_queue))
