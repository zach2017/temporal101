import asyncio
from temporalio import activity


@activity.defn
async def process_data(payload: dict) -> dict:
    """
    Simulates a long-running data processing job.
    Reports heartbeat progress so Temporal knows the worker is alive.
    """
    total_steps = payload.get("steps", 10)
    job_id      = payload.get("job_id", "unknown")

    activity.logger.info(f"[{job_id}] Starting processing — {total_steps} steps")

    results = []

    for step in range(1, total_steps + 1):
        # ── Heartbeat: Temporal marks activity as alive; also carries progress ──
        activity.heartbeat({"step": step, "total": total_steps, "job_id": job_id})
        activity.logger.info(f"[{job_id}] Step {step}/{total_steps} ...")

        # Simulate work: each step takes 2 seconds
        await asyncio.sleep(2)

        results.append({"step": step, "status": "done"})

    activity.logger.info(f"[{job_id}] All steps complete.")
    return {"job_id": job_id, "steps_completed": total_steps, "results": results}


@activity.defn
async def notify_completion(summary: dict) -> str:
    """Simulates sending a notification after the long job finishes."""
    job_id = summary.get("job_id", "unknown")
    steps  = summary.get("steps_completed", 0)

    activity.logger.info(f"[{job_id}] Sending completion notification ...")
    await asyncio.sleep(1)  # Simulate network call

    message = f"Job '{job_id}' finished — {steps} steps completed successfully."
    activity.logger.info(message)
    return message
