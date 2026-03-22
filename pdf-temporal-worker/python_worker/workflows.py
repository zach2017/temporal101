"""
Temporal Workflow: fetch PDF → streaming extract + store.

Constants TASK_QUEUE and WORKFLOW_NAME are shared with the Java client.
"""

from datetime import timedelta
from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from models import PdfProcessingRequest, PdfProcessingResult
    from activities import fetch_pdf_activity, process_and_store_activity

# ── shared constants — Java client must use identical values ──────────────────
TASK_QUEUE     = "pdf-to-text-queue"
WORKFLOW_NAME  = "PdfToTextWorkflow"


@workflow.defn(name=WORKFLOW_NAME)
class PdfToTextWorkflow:
    """
    Two-stage pipeline (reduced from three to keep the streaming pass
    as a single activity — avoids serialising the full extracted text
    through the Temporal event history).

      1. fetch_pdf            – pull from S3 / NFS / URL → local temp
      2. process_and_store    – page-by-page extract + immediate storage
    """

    @workflow.run
    async def run(self, request: PdfProcessingRequest) -> PdfProcessingResult:
        workflow.logger.info(
            "Start  file=%s  type=%s  loc=%s",
            request.file_name, request.storage_type, request.location,
        )

        # 1 ── Fetch PDF to worker-local temp dir
        local_path: str = await workflow.execute_activity(
            fetch_pdf_activity,
            request,
            start_to_close_timeout=timedelta(minutes=10),
            heartbeat_timeout=timedelta(seconds=60),
        )

        # 2 ── Stream-extract text + images → storage (one page at a time)
        request_dict = {
            "file_name":      request.file_name,
            "storage_type":   request.storage_type,
            "location":       request.location,
            "extract_images": request.extract_images,
        }
        result: PdfProcessingResult = await workflow.execute_activity(
            process_and_store_activity,
            args=[local_path, request_dict],
            start_to_close_timeout=timedelta(minutes=30),
            heartbeat_timeout=timedelta(seconds=120),
        )

        workflow.logger.info(
            "Done   pages=%d  images=%d  text→%s",
            result.page_count, result.image_count, result.text_storage_path,
        )
        return result
