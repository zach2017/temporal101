"""
Temporal Workflow – Image OCR (child workflow, runs on the OCR task queue).

This is the stub workflow that the PDF extraction pipeline dispatches
for every extracted image.  It delegates to the OCR activity.
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings


@workflow.defn(name="ImageOcrWorkflow")
class ImageOcrWorkflow:
    """
    Child workflow executed on the `image-ocr-queue`.

    Receives an ImageOcrRequest dict, calls the OCR activity, and returns
    the ImageOcrResult dict.
    """

    @workflow.run
    async def run(self, ocr_request: dict) -> dict:
        workflow.logger.info(
            "ImageOcrWorkflow.start",
            extra={"s3_key": ocr_request.get("s3_key")},
        )

        timeout = timedelta(
            seconds=settings.activity_start_to_close_timeout_seconds,
        )
        heartbeat = timedelta(
            seconds=settings.activity_heartbeat_timeout_seconds,
        )

        result: dict = await workflow.execute_activity(
            "ocr_extract_text_from_image",
            args=[ocr_request],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        workflow.logger.info(
            "ImageOcrWorkflow.complete",
            extra={
                "document_name": result.get("document_name"),
                "text_len": len(result.get("extracted_text", "")),
            },
        )

        return result
