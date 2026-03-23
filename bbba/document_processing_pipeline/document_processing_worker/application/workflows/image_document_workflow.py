"""
Temporal Workflow – Standalone Image Document Processing.

Runs on the ``image-ocr-queue``.  Handles image files (png, jpeg, tiff, etc.)
submitted directly (not extracted from PDFs).

Steps:
  1. Upload the source image to S3
  2. Delegate to the shared ImageOcrWorkflow for text extraction
  3. Store OCR text to S3
  4. Return result
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings


@workflow.defn(name="ImageDocumentWorkflow")
class ImageDocumentWorkflow:

    @workflow.run
    async def run(
        self, file_name: str, file_location: str, file_type: str = "image/png"
    ) -> dict:
        workflow.logger.info(
            "ImageDocumentWorkflow.start",
            extra={"file_name": file_name, "file_type": file_type},
        )

        stem = file_name.rsplit(".", 1)[0] if "." in file_name else file_name
        ext = file_name.rsplit(".", 1)[1].lower() if "." in file_name else "png"

        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)

        # ── Step 1: Upload source image to S3 ─────────────────
        s3_key = f"{stem}/source_image.{ext}"
        await workflow.execute_activity(
            "upload_image_for_ocr",
            args=[{
                "file_location": file_location,
                "s3_key": s3_key,
                "extension": ext,
            }],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        # ── Step 2: OCR via shared child workflow ─────────────
        ocr_result: dict = await workflow.execute_child_workflow(
            "ImageOcrWorkflow",
            args=[{
                "s3_bucket": settings.s3_bucket_name,
                "s3_key": s3_key,
                "document_name": stem,
                "page_number": 0,
                "image_index": 0,
            }],
            id=f"ocr-img-{stem[:50]}",
            task_queue=settings.temporal_ocr_task_queue,
        )

        # ── Step 3: Store OCR text to S3 ──────────────────────
        text_s3_key: str = await workflow.execute_activity(
            "store_extracted_text_to_s3",
            args=[{
                "document_name": stem,
                "source_mime_type": file_type,
                "full_text": ocr_result.get("extracted_text", ""),
            }],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        return {
            "document_name": stem,
            "source_mime_type": file_type,
            "category": "image",
            "text_s3_key": text_s3_key,
            "image_s3_keys": [s3_key],
            "ocr_results": [ocr_result],
        }
