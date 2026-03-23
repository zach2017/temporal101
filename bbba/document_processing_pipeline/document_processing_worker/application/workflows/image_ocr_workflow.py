"""
Temporal Workflow – Image OCR (shared child workflow).

Runs on the ``image-ocr-queue``.  Invoked by:
  - PdfExtractionWorkflow  (for images extracted from PDFs)
  - ImageDocumentWorkflow  (for standalone image files)
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
        workflow.logger.info(
            "ImageOcrWorkflow.start",
            extra={"s3_key": ocr_request.get("s3_key")},
        )

        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)
        retry = build_retry_policy(settings.activity_max_retries)

        result: dict = await workflow.execute_activity(
            "ocr_extract_text_from_image",
            args=[ocr_request],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
            retry_policy=retry,
        )

        workflow.logger.info(
            "ImageOcrWorkflow.complete",
            extra={"text_len": len(result.get("extracted_text", ""))},
        )

        return result
