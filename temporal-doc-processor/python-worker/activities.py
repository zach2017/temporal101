from __future__ import annotations

import json
import os
import re
from pathlib import Path

import fitz  # PyMuPDF
from temporalio import activity

STORAGE_PATH = os.environ.get("STORAGE_PATH", "/app/storage")


@activity.defn
async def extract_text_from_pdf(pdf_file_path: str) -> str:
    """Extract all text from a PDF using PyMuPDF."""
    activity.logger.info(f"Extracting text from: {pdf_file_path}")

    path = Path(pdf_file_path)
    if not path.exists():
        raise RuntimeError(f"PDF file not found: {pdf_file_path}")

    doc = fitz.open(str(path))
    pages_text: list[str] = []
    for page in doc:
        pages_text.append(page.get_text())
    doc.close()

    text = "\n".join(pages_text)
    activity.logger.info(
        f"Extracted {len(text)} characters from {len(pages_text)} pages"
    )
    return text


@activity.defn
async def save_text_to_file(
    document_id: str, original_file_name: str, text_content: str
) -> str:
    """Save extracted text + metadata to the filesystem. Returns output path."""
    activity.logger.info(
        f"Saving text for document {document_id} ({original_file_name})"
    )

    doc_dir = Path(STORAGE_PATH) / "documents" / document_id
    doc_dir.mkdir(parents=True, exist_ok=True)

    # Derive .txt filename from original
    text_file_name = re.sub(r"\.[^.]+$", "", original_file_name) + ".txt"
    text_file_path = doc_dir / text_file_name
    text_file_path.write_text(text_content, encoding="utf-8")

    # Write metadata
    metadata = {
        "documentId": document_id,
        "originalFileName": original_file_name,
        "textFileName": text_file_name,
        "charCount": len(text_content),
    }
    (doc_dir / "metadata.json").write_text(
        json.dumps(metadata), encoding="utf-8"
    )

    activity.logger.info(f"Saved to {text_file_path}")
    return str(text_file_path)


@activity.defn
async def read_text_file(document_id: str) -> str:
    """Read extracted text back from storage."""
    activity.logger.info(f"Reading text for document {document_id}")

    doc_dir = Path(STORAGE_PATH) / "documents" / document_id
    metadata_path = doc_dir / "metadata.json"
    if not metadata_path.exists():
        raise RuntimeError(f"Document not found: {document_id}")

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    text_file_name = metadata["textFileName"]
    return (doc_dir / text_file_name).read_text(encoding="utf-8")


@activity.defn
async def document_exists(document_id: str) -> bool:
    """Check whether a processed document exists."""
    return (
        Path(STORAGE_PATH) / "documents" / document_id / "metadata.json"
    ).exists()
