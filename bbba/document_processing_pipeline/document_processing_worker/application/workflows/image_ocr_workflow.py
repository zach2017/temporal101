"""
Temporal Workflow – Image OCR (shared child workflow).
Reads images from /files, runs OCR activity.
"""

from __future__ import annotations
from datetime import timedelta
from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings
    from infrastructure.retry_policy import build_retry_policy


@workflow.defn(name="ImageOcrWorkflow")
class ImageOcrWorkflow:

    @workflow.run
    async def run(self, ocr_request: dict) -> dict:
        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)
        retry = build_retry_policy(settings.activity_max_retries)

        return await workflow.execute_activity(
            "ocr_extract_text_from_image", args=[ocr_request],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)
