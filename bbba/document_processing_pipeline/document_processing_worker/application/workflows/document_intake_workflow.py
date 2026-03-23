"""
Temporal Workflow – Document Intake Pipeline (top-level orchestrator).

This is the single entry-point for ALL document types:

  1. Detect MIME type  →  classify into a DocumentCategory
  2. Route to the correct child workflow:
       PDF   → PdfExtractionWorkflow   (pdf-extraction-queue)
       IMAGE → ImageOcrWorkflow        (image-ocr-queue)
       OTHER → DocumentConversionWorkflow (document-conversion-queue)
  3. Return unified DocumentProcessingResult
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from domain.value_objects.documents import DocumentCategory
    from infrastructure.config import settings


@workflow.defn(name="DocumentIntakeWorkflow")
class DocumentIntakeWorkflow:
    """
    Top-level entry-point.  Accepts *any* file and routes it through
    the correct extraction pipeline based on its detected MIME type.
    """

    @workflow.run
    async def run(
        self, file_name: str, file_location: str, file_type: str = ""
    ) -> dict:
        workflow.logger.info(
            "DocumentIntakeWorkflow.start",
            extra={"file_name": file_name, "file_type": file_type},
        )

        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)

        # ── Step 1: Detect MIME type ──────────────────────────
        detection: dict = await workflow.execute_activity(
            "detect_mime_type",
            args=[{
                "file_name": file_name,
                "file_location": file_location,
                "file_type": file_type,
            }],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        mime_type = detection["mime_type"]
        category = detection["category"]

        workflow.logger.info(
            "DocumentIntakeWorkflow.detected",
            extra={"mime_type": mime_type, "category": category},
        )

        # ── Step 2: Route to child workflow ───────────────────

        if category == DocumentCategory.PDF:
            return await workflow.execute_child_workflow(
                "PdfExtractionWorkflow",
                args=[file_name, file_location, mime_type],
                id=f"pdf-{_safe_id(file_name)}",
                task_queue=settings.temporal_pdf_task_queue,
            )

        elif category == DocumentCategory.IMAGE:
            return await workflow.execute_child_workflow(
                "ImageDocumentWorkflow",
                args=[file_name, file_location, mime_type],
                id=f"img-{_safe_id(file_name)}",
                task_queue=settings.temporal_ocr_task_queue,
            )

        else:
            # Office docs, plain text, RTF, HTML, ebook, email, unknown
            return await workflow.execute_child_workflow(
                "DocumentConversionWorkflow",
                args=[file_name, file_location, mime_type, category],
                id=f"conv-{_safe_id(file_name)}",
                task_queue=settings.temporal_conversion_task_queue,
            )


def _safe_id(name: str) -> str:
    """Produce a Temporal-safe workflow ID fragment."""
    import hashlib
    return hashlib.sha1(name.encode()).hexdigest()[:12]
