from __future__ import annotations

import os
from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from activities import (
        document_exists,
        extract_text_from_pdf,
        read_text_file,
        save_text_to_file,
    )
    from models import DocumentResult

STORAGE_PATH = os.environ.get("STORAGE_PATH", "/app/storage")


@workflow.defn
class PdfProcessingWorkflow:
    """Upload pipeline: PDF → extract text → store → return reference."""

    @workflow.run
    async def run(self, document_id: str, original_file_name: str) -> DocumentResult:
        workflow.logger.info(
            f"Starting PDF processing for {document_id} ({original_file_name})"
        )

        # Step 1 – Build the path where the API saved the uploaded PDF
        pdf_path = f"{STORAGE_PATH}/uploads/{document_id}/{original_file_name}"

        # Step 2 – Extract text from the PDF
        extracted_text: str = await workflow.execute_activity(
            extract_text_from_pdf,
            pdf_path,
            start_to_close_timeout=timedelta(minutes=5),
        )

        # Step 3 – Persist extracted text to the filesystem
        text_file_path: str = await workflow.execute_activity(
            save_text_to_file,
            args=[document_id, original_file_name, extracted_text],
            start_to_close_timeout=timedelta(minutes=2),
        )

        # Step 4 – Return result
        return DocumentResult(
            document_id=document_id,
            original_file_name=original_file_name,
            text_file_path=text_file_path,
            status="COMPLETED",
            extracted_char_count=len(extracted_text),
        )


@workflow.defn
class DocumentDownloadWorkflow:
    """Download pipeline: validate existence → read text → return content."""

    @workflow.run
    async def run(self, document_id: str) -> str:
        workflow.logger.info(f"Starting download workflow for {document_id}")

        exists: bool = await workflow.execute_activity(
            document_exists,
            document_id,
            start_to_close_timeout=timedelta(seconds=30),
        )
        if not exists:
            raise RuntimeError(f"Document not found: {document_id}")

        content: str = await workflow.execute_activity(
            read_text_file,
            document_id,
            start_to_close_timeout=timedelta(minutes=2),
        )

        workflow.logger.info(
            f"Download complete for {document_id} ({len(content)} chars)"
        )
        return content
