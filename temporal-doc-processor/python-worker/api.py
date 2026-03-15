from __future__ import annotations

import os
import uuid
from dataclasses import asdict
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from temporalio.client import Client

from models import DocumentResult
from workflows import DocumentDownloadWorkflow, PdfProcessingWorkflow

TEMPORAL_ADDRESS = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
STORAGE_PATH = os.environ.get("STORAGE_PATH", "/app/storage")
TASK_QUEUE = "DOC_PROCESSING_QUEUE"

app = FastAPI(title="DocFlow API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_client: Client | None = None


async def get_client() -> Client:
    global _client
    if _client is None:
        _client = await Client.connect(TEMPORAL_ADDRESS)
    return _client


# ── Health ───────────────────────────────────────────────────────────────────


@app.get("/api/health")
async def health():
    return {"status": "UP", "service": "doc-processor"}


# ── Upload & Process ─────────────────────────────────────────────────────────


@app.post("/api/upload")
async def upload_pdf(file: UploadFile = File(...)):
    if not file.filename or not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are accepted")

    document_id = str(uuid.uuid4())
    original_file_name = file.filename

    # Persist uploaded PDF
    upload_dir = Path(STORAGE_PATH) / "uploads" / document_id
    upload_dir.mkdir(parents=True, exist_ok=True)
    upload_path = upload_dir / original_file_name

    content = await file.read()
    upload_path.write_bytes(content)

    # Start the Temporal workflow (synchronous execution – waits for result)
    client = await get_client()
    workflow_id = f"pdf-process-{document_id}"

    result: DocumentResult = await client.execute_workflow(
        PdfProcessingWorkflow.run,
        args=[document_id, original_file_name],
        id=workflow_id,
        task_queue=TASK_QUEUE,
    )

    return {
        "documentId": result.document_id,
        "originalFileName": result.original_file_name,
        "textFilePath": result.text_file_path,
        "status": result.status,
        "extractedCharCount": result.extracted_char_count,
        "workflowId": workflow_id,
    }


# ── Download ─────────────────────────────────────────────────────────────────


@app.get("/api/download/{document_id}")
async def download_document(document_id: str):
    client = await get_client()
    workflow_id = f"download-{document_id}-{uuid.uuid4().hex[:8]}"

    try:
        content: str = await client.execute_workflow(
            DocumentDownloadWorkflow.run,
            document_id,
            id=workflow_id,
            task_queue=TASK_QUEUE,
        )
    except Exception as exc:
        raise HTTPException(status_code=404, detail=str(exc))

    return {
        "documentId": document_id,
        "content": content,
        "charCount": len(content),
        "status": "SUCCESS",
    }
