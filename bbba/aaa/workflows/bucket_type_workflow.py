"""Temporal workflow for BucketType operations."""

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from activities import is_safe_bucket, resolve_bucket


@workflow.defn
class BucketTypeWorkflow:
    """Resolve a bucket name and check whether it is the SAFE bucket."""

    @workflow.run
    async def run(self, bucket_name: str) -> dict:
        # Step 1: Resolve
        resolved = await workflow.execute_activity(
            resolve_bucket,
            bucket_name,
            start_to_close_timeout=timedelta(seconds=10),
        )

        # Step 2: Check safety
        safe = await workflow.execute_activity(
            is_safe_bucket,
            resolved,
            start_to_close_timeout=timedelta(seconds=10),
        )

        workflow.logger.info(f"Bucket resolved: {resolved}, is_safe={safe}")
        return {"bucket": resolved, "is_safe": safe}
