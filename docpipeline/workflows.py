"""
DocumentProcessingWorkflow — Temporal workflow orchestrator.

Routes documents through the full pipeline based on detected file type:
  Upload → Detect → Store raw (parallel) → Extract text (routed) → 
  Store text (parallel) → Index metadata → Done

Supports signals (cancel) and queries (get_status) for live tracking.
"""

import asyncio
from datetime import timedelta
from dataclasses import dataclass, field
from typing import Optional

from temporalio import workflow

# Import activity function references (stubs)
with workflow.unsafe.imports_passed_through():
    from activities import (
        detect_file_type,
        store_document_s3,
        store_document_filesystem,
        extract_text_from_pdf,
        extract_text_from_docx,
        extract_text_plain,
        extract_images_from_pdf,
        ocr_single_image,
        ocr_batch_images,
        store_text_s3,
        store_text_filesystem,
        store_text_elasticsearch,
        store_images_s3,
        index_document_metadata,
    )


# ── Shared retry/timeout configs ────────────────────────────────────────────

FAST_TIMEOUT = timedelta(seconds=30)
MEDIUM_TIMEOUT = timedelta(seconds=60)
SLOW_TIMEOUT = timedelta(seconds=120)
STORAGE_TIMEOUT = timedelta(seconds=60)


@workflow.defn
class DocumentProcessingWorkflow:
    """
    Full document processing pipeline.

    Steps:
      1. Detect file type (MIME, category)
      2. Store raw document to S3 + filesystem (parallel)
      3. Extract text — routed by file type (PDF / DOCX / text / image OCR)
      4. For PDFs: also extract embedded images → batch OCR
      5. Store extracted text to S3, filesystem, and Elasticsearch (parallel)
      6. Store extracted images to S3 (if any)
      7. Index full document metadata in Elasticsearch
    """

    def __init__(self):
        self._status: str = "initialized"
        self._step: str = ""
        self._steps_completed: list = []
        self._cancelled: bool = False
        self._result: Optional[dict] = None
        self._error: Optional[str] = None
        self._progress: float = 0.0

    # ── Signals ──────────────────────────────────────────────────────────

    @workflow.signal
    async def cancel_processing(self):
        """Signal to cancel the workflow mid-processing."""
        self._cancelled = True
        self._status = "cancelling"

    # ── Queries ──────────────────────────────────────────────────────────

    @workflow.query
    def get_status(self) -> dict:
        """Query current workflow status without side effects."""
        return {
            "status": self._status,
            "current_step": self._step,
            "steps_completed": self._steps_completed,
            "progress": self._progress,
            "cancelled": self._cancelled,
            "error": self._error,
        }

    # ── Helpers ──────────────────────────────────────────────────────────

    def _advance(self, step_name: str, progress: float):
        if self._cancelled:
            raise workflow.ContinueAsNewError()  # abort cleanly
        self._step = step_name
        self._status = "running"
        self._progress = progress
        workflow.logger.info(f"[{progress:.0%}] {step_name}")

    def _complete_step(self, step_name: str):
        self._steps_completed.append(step_name)

    # ── Main orchestrator ────────────────────────────────────────────────

    @workflow.run
    async def run(self, file_path: str, doc_id: str) -> dict:
        result = {}

        try:
            # ── Step 1: Detect file type ─────────────────────────────
            self._advance("detect_file_type", 0.0)
            file_info = await workflow.execute_activity(
                detect_file_type,
                file_path,
                start_to_close_timeout=FAST_TIMEOUT,
            )
            result["file_info"] = file_info
            self._complete_step("detect_file_type")

            if self._cancelled:
                return {"status": "cancelled", "completed": self._steps_completed}

            # ── Step 2: Store raw document (S3 + filesystem PARALLEL) ─
            self._advance("store_raw_document", 0.15)

            s3_task = workflow.execute_activity(
                store_document_s3,
                args=[doc_id, file_path, file_info["mime_type"]],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )
            fs_task = workflow.execute_activity(
                store_document_filesystem,
                args=[doc_id, file_path],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )
            s3_key, fs_path = await asyncio.gather(s3_task, fs_task)
            result["s3_key"] = s3_key
            result["fs_path"] = fs_path
            self._complete_step("store_document_s3")
            self._complete_step("store_document_filesystem")

            if self._cancelled:
                return {"status": "cancelled", "completed": self._steps_completed}

            # ── Step 3: Extract text (routed by file type) ────────────
            category = file_info["category"]
            self._advance(f"extract_text ({category})", 0.35)

            text_result = {}
            images_result = None
            ocr_result = None

            if category == "pdf":
                text_result = await workflow.execute_activity(
                    extract_text_from_pdf,
                    file_path,
                    start_to_close_timeout=SLOW_TIMEOUT,
                    heartbeat_timeout=timedelta(seconds=30),
                )
                self._complete_step("extract_text_from_pdf")

                # Also extract images from the PDF
                self._advance("extract_images_from_pdf", 0.50)
                images_result = await workflow.execute_activity(
                    extract_images_from_pdf,
                    args=[file_path, doc_id],
                    start_to_close_timeout=MEDIUM_TIMEOUT,
                )
                self._complete_step("extract_images_from_pdf")

                # OCR on extracted images
                if images_result["image_count"] > 0:
                    self._advance("ocr_batch_images", 0.55)
                    ocr_result = await workflow.execute_activity(
                        ocr_batch_images,
                        images_result["image_paths"],
                        start_to_close_timeout=SLOW_TIMEOUT,
                        heartbeat_timeout=timedelta(seconds=60),
                    )
                    self._complete_step("ocr_batch_images")

            elif category == "docx":
                text_result = await workflow.execute_activity(
                    extract_text_from_docx,
                    file_path,
                    start_to_close_timeout=MEDIUM_TIMEOUT,
                )
                self._complete_step("extract_text_from_docx")

            elif category == "text":
                text_result = await workflow.execute_activity(
                    extract_text_plain,
                    file_path,
                    start_to_close_timeout=FAST_TIMEOUT,
                )
                self._complete_step("extract_text_plain")

            elif category == "image":
                text_result = await workflow.execute_activity(
                    ocr_single_image,
                    file_path,
                    start_to_close_timeout=SLOW_TIMEOUT,
                )
                self._complete_step("ocr_single_image")

            else:
                text_result = {"text": "", "word_count": 0}
                self._complete_step("skip_extraction_unknown_type")

            result["text_extraction"] = text_result

            if self._cancelled:
                return {"status": "cancelled", "completed": self._steps_completed}

            # ── Step 4: Store extracted text (S3 + FS + ES PARALLEL) ──
            extracted_text = text_result.get("text", "")
            self._advance("store_extracted_text", 0.65)

            text_s3_task = workflow.execute_activity(
                store_text_s3,
                args=[doc_id, extracted_text],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )
            text_fs_task = workflow.execute_activity(
                store_text_filesystem,
                args=[doc_id, extracted_text],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )
            text_es_task = workflow.execute_activity(
                store_text_elasticsearch,
                args=[
                    doc_id,
                    extracted_text,
                    file_info["filename"],
                    file_info["category"],
                    {"file_size": file_info["file_size"]},
                ],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )

            text_s3_key, text_fs_path, text_es_id = await asyncio.gather(
                text_s3_task, text_fs_task, text_es_task
            )
            result["text_s3_key"] = text_s3_key
            result["text_fs_path"] = text_fs_path
            result["text_es_id"] = text_es_id
            self._complete_step("store_text_s3")
            self._complete_step("store_text_filesystem")
            self._complete_step("store_text_elasticsearch")

            # ── Step 5: Store images to S3 (if PDF had images) ────────
            if images_result and images_result["image_count"] > 0:
                self._advance("store_images_s3", 0.80)
                img_store = await workflow.execute_activity(
                    store_images_s3,
                    args=[doc_id, images_result["image_paths"]],
                    start_to_close_timeout=STORAGE_TIMEOUT,
                )
                result["image_s3_keys"] = img_store["s3_keys"]
                self._complete_step("store_images_s3")

            # ── Step 6: Index full metadata in Elasticsearch ──────────
            self._advance("index_document_metadata", 0.90)
            meta_es_id = await workflow.execute_activity(
                index_document_metadata,
                args=[
                    doc_id,
                    file_info["filename"],
                    file_info,
                    s3_key,
                    fs_path,
                    {
                        "word_count": text_result.get("word_count", len(extracted_text.split())),
                        "page_count": text_result.get("page_count", 0),
                        "has_ocr": ocr_result is not None,
                    },
                ],
                start_to_close_timeout=STORAGE_TIMEOUT,
            )
            result["metadata_es_id"] = meta_es_id
            self._complete_step("index_document_metadata")

            # ── Done ─────────────────────────────────────────────────
            self._status = "completed"
            self._step = "done"
            self._progress = 1.0
            result["status"] = "completed"
            result["steps_completed"] = self._steps_completed
            self._result = result

            return result

        except Exception as e:
            self._status = "failed"
            self._error = str(e)
            workflow.logger.error(f"Workflow failed: {e}")
            raise
