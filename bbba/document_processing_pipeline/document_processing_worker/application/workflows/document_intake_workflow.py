"""
Temporal Workflow – Document Intake Pipeline (top-level orchestrator).

Calls the Java Tika worker for MIME detection, then routes to the
correct child workflow.  All activity calls use the shared retry policy
(default: 2 max attempts, configurable via ACTIVITY_MAX_RETRIES).
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from domain.value_objects.documents import DocumentCategory
    from infrastructure.config import settings
    from infrastructure.retry_policy import build_retry_policy


@workflow.defn(name="DocumentIntakeWorkflow")
class DocumentIntakeWorkflow:

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
        retry = build_retry_policy(settings.activity_max_retries)

        # ── Step 1: Detect MIME type via Java Tika worker ─────
        detection: dict = await workflow.execute_activity(
            "detect_file_type_tika",
            args=[{
                "file_name": file_name,
                "file_location": file_location,
                "file_type": file_type,
            }],
            task_queue=settings.temporal_tika_task_queue,
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
            retry_policy=retry,
        )

        mime_type = detection["mime_type"]
        category = detection["category"]

        workflow.logger.info(
            "DocumentIntakeWorkflow.tika_detected",
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
            return await workflow.execute_child_workflow(
                "DocumentConversionWorkflow",
                args=[file_name, file_location, mime_type, category],
                id=f"conv-{_safe_id(file_name)}",
                task_queue=settings.temporal_conversion_task_queue,
            )


def _safe_id(name: str) -> str:
    import hashlib
    return hashlib.sha1(name.encode()).hexdigest()[:12]
