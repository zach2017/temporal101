"""
Temporal Workflow – PDF Extraction Pipeline.

Orchestrates the complete extraction flow:
  1. Extract text  → store in S3  (document_name/extracted_text.json)
  2. Extract images → store each in S3  (document_name/extracted_images/…)
  3. Dispatch a child workflow for OCR on every extracted image
  4. Return aggregated result
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings


@workflow.defn(name="PdfExtractionWorkflow")
class PdfExtractionWorkflow:
    """
    Top-level workflow – deterministic orchestration only.

    All I/O happens inside activities; the workflow merely sequences them
    and fans-out the OCR child-workflow for each image.
    """

    @workflow.run
    async def run(self, file_name: str, file_location: str) -> dict:
        workflow.logger.info(
            "PdfExtractionWorkflow.start",
            extra={"file_name": file_name},
        )

        pdf_payload = {"file_name": file_name, "file_location": file_location}
        timeout = timedelta(
            seconds=settings.activity_start_to_close_timeout_seconds,
        )
        heartbeat = timedelta(
            seconds=settings.activity_heartbeat_timeout_seconds,
        )

        # ── Step 1: Extract text ──────────────────────────────
        extracted_text: dict = await workflow.execute_activity(
            "extract_text_from_pdf",
            args=[pdf_payload],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        # ── Step 2: Store extracted text in S3 ────────────────
        text_s3_key: str = await workflow.execute_activity(
            "store_extracted_text_to_s3",
            args=[extracted_text],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        # ── Step 3: Extract images ────────────────────────────
        image_metadata_list: list[dict] = await workflow.execute_activity(
            "extract_images_from_pdf",
            args=[pdf_payload],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
        )

        # ── Step 4: Upload each image to S3 (fan-out) ────────
        image_s3_keys: list[str] = []
        for img_meta in image_metadata_list:
            s3_key: str = await workflow.execute_activity(
                "store_image_to_s3",
                args=[
                    {
                        "s3_object_key": img_meta["s3_object_key"],
                        "image_bytes": img_meta["_image_bytes"],
                        "extension": img_meta["extension"],
                    }
                ],
                start_to_close_timeout=timeout,
                heartbeat_timeout=heartbeat,
            )
            image_s3_keys.append(s3_key)

        # ── Step 5: Build OCR requests ────────────────────────
        ocr_payloads: list[dict] = []
        if image_s3_keys:
            ocr_payloads = await workflow.execute_activity(
                "build_ocr_requests",
                args=[
                    {
                        "document_name": extracted_text["document_name"],
                        "image_s3_keys": image_s3_keys,
                        "s3_bucket": settings.s3_bucket_name,
                        "image_metadata": [
                            {
                                "page_number": m["page_number"],
                                "image_index": m["image_index"],
                            }
                            for m in image_metadata_list
                        ],
                    }
                ],
                start_to_close_timeout=timeout,
                heartbeat_timeout=heartbeat,
            )

        # ── Step 6: Fan-out child workflow for OCR ────────────
        ocr_results: list[dict] = []
        for ocr_payload in ocr_payloads:
            result = await workflow.execute_child_workflow(
                "ImageOcrWorkflow",
                args=[ocr_payload],
                id=f"ocr-{ocr_payload['document_name']}"
                   f"-p{ocr_payload['page_number']}"
                   f"-i{ocr_payload['image_index']}",
                task_queue=settings.temporal_ocr_task_queue,
            )
            ocr_results.append(result)

        # ── Done ──────────────────────────────────────────────
        workflow.logger.info(
            "PdfExtractionWorkflow.complete",
            extra={
                "document_name": extracted_text["document_name"],
                "images": len(image_s3_keys),
                "ocr_results": len(ocr_results),
            },
        )

        return {
            "document_name": extracted_text["document_name"],
            "text_s3_key": text_s3_key,
            "image_s3_keys": image_s3_keys,
            "ocr_results": ocr_results,
        }
