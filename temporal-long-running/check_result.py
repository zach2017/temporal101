import asyncio
import argparse
import logging

from temporalio.client import Client, WorkflowExecutionStatus

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("checker")

STATUS_LABELS = {
    WorkflowExecutionStatus.RUNNING:          "🔄 RUNNING",
    WorkflowExecutionStatus.COMPLETED:        "✅ COMPLETED",
    WorkflowExecutionStatus.FAILED:           "❌ FAILED",
    WorkflowExecutionStatus.CANCELED:         "🚫 CANCELED",
    WorkflowExecutionStatus.TERMINATED:       "⛔ TERMINATED",
    WorkflowExecutionStatus.CONTINUED_AS_NEW: "➡️  CONTINUED_AS_NEW",
    WorkflowExecutionStatus.TIMED_OUT:        "⏰ TIMED_OUT",
}


async def main(host: str, workflow_id: str, wait: bool):
    logger.info(f"Connecting to Temporal at {host} ...")
    client = await Client.connect(host)

    handle = client.get_workflow_handle(workflow_id)
    desc   = await handle.describe()
    status = desc.status
    label  = STATUS_LABELS.get(status, str(status))

    logger.info(f"Workflow ID : {workflow_id}")
    logger.info(f"Status      : {label}")
    logger.info(f"Started at  : {desc.start_time}")

    if status == WorkflowExecutionStatus.RUNNING:
        if wait:
            logger.info("Waiting for completion ...")
            result = await handle.result()
            logger.info(f"Result: {result}")
        else:
            logger.info("Still running. Re-run with --wait to block until done.")
            logger.info(f"  python check_result.py --workflow-id {workflow_id} --wait")

    elif status == WorkflowExecutionStatus.COMPLETED:
        result = await handle.result()
        logger.info(f"Close time  : {desc.close_time}")
        logger.info(f"Result      : {result}")

    else:
        logger.info(f"Close time  : {desc.close_time}")
        logger.info("Workflow did not complete successfully.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Check result of a long-running workflow")
    parser.add_argument("--host",        default="localhost:7233")
    parser.add_argument("--workflow-id", required=True, help="Workflow ID to check")
    parser.add_argument("--wait",        action="store_true",
                        help="Block and wait if the workflow is still running")
    args = parser.parse_args()

    asyncio.run(main(args.host, args.workflow_id, args.wait))
