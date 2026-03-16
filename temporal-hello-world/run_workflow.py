import asyncio
import argparse
from temporalio.client import Client
from workflows import HelloWorldWorkflow

async def main(host: str, task_queue: str, name: str):
    client = await Client.connect(host)

    result = await client.execute_workflow(
        HelloWorldWorkflow.run,
        name,
        id=f"hello-{name}-workflow",
        task_queue=task_queue,
    )

    print(f"Workflow result: {result}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run Hello World Workflow")
    parser.add_argument("--host", default="localhost:7233")
    parser.add_argument("--task-queue", default="hello-world-queue")
    parser.add_argument("--name", default="World", help="Name to greet")
    args = parser.parse_args()

    asyncio.run(main(args.host, args.task_queue, args.name))
