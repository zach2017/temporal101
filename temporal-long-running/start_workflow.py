import asyncio
import argparse
import uuid
import logging

from temporalio.client import Client
from workflows import LongRunningWorkflow

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("starter")


async def main(host: str, task_queue: str, job_id: str, steps: int):
    logger.info(f"Connecting to Temporal at {host} ...")
    client = await Client.connect(host)

    payload = {"job_id": job_id, "steps": steps}
    workflow_id = f"long-running-{job_id}"

    # ── Start async: returns immediately without waiting for the result ───────
    handle = await client.start_workflow(
        LongRunningWorkflow.run,
        payload,
        id=workflow_id,
        task_queue=task_queue,
    )

    logger.info(f"Workflow started!")
    logger.info(f"  Workflow ID : {workflow_id}")
    logger.info(f"  Job ID      : {job_id}")
    logger.info(f"  Steps       : {steps}  (~{steps * 2}s total)")
    logger.info(f"")
    logger.info(f"Check status:")
    logger.info(f"  python check_result.py --workflow-id {workflow_id} --host {host}")
    logger.info(f"  temporal workflow describe --workflow-id {workflow_id}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Start a long-running workflow async")
    parser.add_argument("--host",        default="localhost:7233")
    parser.add_argument("--task-queue",  default="long-running-queue")
    parser.add_argument("--job-id",      default=f"job-{uuid.uuid4().hex[:8]}")
    parser.add_argument("--steps",       type=int, default=10,
                        help="Number of steps to simulate (each ~2s)")
    args = parser.parse_args()

    asyncio.run(main(args.host, args.task_queue, args.job_id, args.steps))
