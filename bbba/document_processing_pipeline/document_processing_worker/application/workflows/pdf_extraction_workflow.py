"""
Temporal Workflow – PDF Extraction Pipeline.

All activity calls use the shared retry policy
(default: 2 max attempts, configurable via ACTIVITY_MAX_RETRIES).
"""

from __future__ import annotations

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from infrastructure.config import settings
    from infrastructure.retry_policy import build_retry_policy


@workflow.defn(name="PdfExtractionWorkflow")
class PdfExtractionWorkflow:

    @workflow.run
    async def run(
        self, file_name: str, file_location: str, file_type: str = "application/pdf"
    ) -> dict:
        workflow.logger.info(
            "PdfExtractionWorkflow.start",
            extra={"file_name": file_name, "file_type": file_type},
        )

        doc_payload = {
            "file_name": file_name,
            "file_location": file_location,
            "file_type": file_type,
        }
        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)
        retry = build_retry_policy(settings.activity_max_retries)

        extracted_text: dict = await workflow.execute_activity(
            "extract_text_from_pdf",
            args=[doc_payload],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
            retry_policy=retry,
        )

        text_s3_key: str = await workflow.execute_activity(
            "store_extracted_text_to_s3",
            args=[extracted_text],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
            retry_policy=retry,
        )

        image_metadata_list: list[dict] = await workflow.execute_activity(
            "extract_images_from_pdf",
            args=[doc_payload],
            start_to_close_timeout=timeout,
            heartbeat_timeout=heartbeat,
            retry_policy=retry,
        )

        image_s3_keys: list[str] = []
        for img_meta in image_metadata_list:
            s3_key: str = await workflow.execute_activity(
                "store_image_to_s3",
                args=[{
                    "s3_object_key": img_meta["s3_object_key"],
                    "image_bytes": img_meta["_image_bytes"],
                    "extension": img_meta["extension"],
                }],
                start_to_close_timeout=timeout,
                heartbeat_timeout=heartbeat,
                retry_policy=retry,
            )
            image_s3_keys.append(s3_key)

        ocr_payloads: list[dict] = []
        if image_s3_keys:
            ocr_payloads = await workflow.execute_activity(
                "build_ocr_requests",
                args=[{
                    "document_name": extracted_text["document_name"],
                    "image_s3_keys": image_s3_keys,
                    "s3_bucket": settings.s3_bucket_name,
                    "image_metadata": [
                        {"page_number": m["page_number"], "image_index": m["image_index"]}
                        for m in image_metadata_list
                    ],
                }],
                start_to_close_timeout=timeout,
                heartbeat_timeout=heartbeat,
                retry_policy=retry,
            )

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

        return {
            "document_name": extracted_text["document_name"],
            "source_mime_type": file_type,
            "category": "pdf",
            "text_s3_key": text_s3_key,
            "image_s3_keys": image_s3_keys,
            "ocr_results": ocr_results,
        }
