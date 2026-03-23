"""
Temporal Workflow – Document Conversion Pipeline.

Runs on the ``document-conversion-queue``.  Converts office docs,
plain text, RTF, HTML, ebooks, email, etc. into extracted text and
stores the result in S3.
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings


@workflow.defn(name="DocumentConversionWorkflow")
class DocumentConversionWorkflow:

    @workflow.run
    async def run(
        self,
        file_name: str,
        file_location: str,
        mime_type: str,
        category: str,
    ) -> dict:
        workflow.logger.info(
            "DocumentConversionWorkflow.start",
            extra={
                "file_name": file_name,
                "mime_type": mime_type,
                "category": category,
            },
        )

        stem = file_name.rsplit(".", 1)[0] if "." in file_name else file_name

        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)

        # ── Step 1: Convert document to text ──────────────────
        conversion: dict = await workflow.execute_activity(
            "convert_document_to_text",
            args=[{
                "file_name": file_name,
                "file_location": file_location,
                "mime_type": mime_type,
                "category": category,
                "document_name": stem,
            }],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        # ── Step 2: Store extracted text to S3 ────────────────
        text_s3_key: str = await workflow.execute_activity(
            "store_extracted_text_to_s3",
            args=[{
                "document_name": conversion["document_name"],
                "source_mime_type": conversion["source_mime_type"],
                "full_text": conversion["extracted_text"],
            }],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        return {
            "document_name": conversion["document_name"],
            "source_mime_type": mime_type,
            "category": category,
            "text_s3_key": text_s3_key,
            "image_s3_keys": [],
            "ocr_results": [],
        }
