"""
Temporal Workflow – PDF Extraction Pipeline.
Writes to /files/output/<document_name>/ on the shared volume.
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
    async def run(self, file_name: str, file_location: str, file_type: str = "application/pdf") -> dict:
        doc_payload = {"file_name": file_name, "file_location": file_location, "file_type": file_type}
        timeout = timedelta(seconds=settings.activity_start_to_close_timeout_seconds)
        heartbeat = timedelta(seconds=settings.activity_heartbeat_timeout_seconds)
        retry = build_retry_policy(settings.activity_max_retries)

        extracted_text: dict = await workflow.execute_activity(
            "extract_text_from_pdf", args=[doc_payload],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        text_path: str = await workflow.execute_activity(
            "store_extracted_text", args=[extracted_text],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        image_metadata_list: list[dict] = await workflow.execute_activity(
            "extract_images_from_pdf", args=[doc_payload],
            start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        image_paths: list[str] = []
        for img in image_metadata_list:
            path: str = await workflow.execute_activity(
                "store_extracted_image", args=[{
                    "document_name": img["document_name"],
                    "image_bytes": img["_image_bytes"],
                    "page_number": img["page_number"],
                    "image_index": img["image_index"],
                    "extension": img["extension"],
                }],
                start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)
            image_paths.append(path)

        ocr_payloads: list[dict] = []
        if image_paths:
            ocr_payloads = await workflow.execute_activity(
                "build_ocr_requests", args=[{
                    "document_name": extracted_text["document_name"],
                    "image_paths": image_paths,
                    "image_metadata": [
                        {"page_number": m["page_number"], "image_index": m["image_index"]}
                        for m in image_metadata_list
                    ],
                }],
                start_to_close_timeout=timeout, heartbeat_timeout=heartbeat, retry_policy=retry)

        ocr_results: list[dict] = []
        for ocr_payload in ocr_payloads:
            result = await workflow.execute_child_workflow(
                "ImageOcrWorkflow", args=[ocr_payload],
                id=f"ocr-{ocr_payload['document_name']}-p{ocr_payload['page_number']}-i{ocr_payload['image_index']}",
                task_queue=settings.temporal_ocr_task_queue)
            ocr_results.append(result)

        return {
            "document_name": extracted_text["document_name"],
            "source_mime_type": file_type,
            "category": "pdf",
            "text_output_path": text_path,
            "image_output_paths": image_paths,
            "ocr_results": ocr_results,
        }
