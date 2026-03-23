"""
Temporal Workflow – Standalone Image Document Processing.
Copies image to output dir, runs OCR, saves text.
"""

from __future__ import annotations
from datetime import timedelta
from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings
    from infrastructure.retry_policy import build_retry_policy


@workflow.defn(name="ImageDocumentWorkflow")
class ImageDocumentWorkflow:

    @workflow.run
    async def run(self, file_name: str, file_location: str, file_type: str = "image/png") -> dict:
        stem = file_name.rsplit(".", 1)[0] if "." in file_name else file_name
        ext = file_name.rsplit(".", 1)[1].lower() if "." in file_name else "png"

        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)
        retry = build_retry_policy(settings.activity_max_retries)

        # Copy source image into output dir
        image_path: str = await workflow.execute_activity(
            "copy_source_image", args=[{
                "file_location": file_location,
                "document_name": stem,
                "extension": ext,
            }],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        # OCR via shared child workflow
        ocr_result: dict = await workflow.execute_child_workflow(
            "ImageOcrWorkflow", args=[{
                "image_path": image_path,
                "document_name": stem,
                "page_number": 0,
                "image_index": 0,
            }],
            id=f"ocr-img-{stem[:50]}",
            task_queue=settings.temporal_ocr_task_queue)

        # Save OCR text
        text_path: str = await workflow.execute_activity(
            "store_extracted_text", args=[{
                "document_name": stem,
                "source_mime_type": file_type,
                "full_text": ocr_result.get("extracted_text", ""),
            }],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        return {
            "document_name": stem,
            "source_mime_type": file_type,
            "category": "image",
            "text_output_path": text_path,
            "image_output_paths": [image_path],
            "ocr_results": [ocr_result],
        }
