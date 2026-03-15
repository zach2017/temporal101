"""
Document processing activities — STUB implementations.

Each activity simulates the real operation (S3 upload, OCR, Elasticsearch indexing, etc.)
by sleeping briefly and returning a realistic success response. Replace these with real
implementations when connecting to actual services.
"""

import asyncio
import os
import uuid
import mimetypes
from datetime import datetime
from temporalio import activity


# ─── File Detection ─────────────────────────────────────────────────────────

CATEGORY_MAP = {
    "application/pdf": "pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
    "text/plain": "text",
    "image/png": "image",
    "image/jpeg": "image",
    "image/tiff": "image",
    "image/gif": "image",
    "image/webp": "image",
}


@activity.defn
async def detect_file_type(file_path: str) -> dict:
    """Detect MIME type and categorize the uploaded file."""
    activity.logger.info(f"[detect_file_type] Analyzing: {file_path}")
    await asyncio.sleep(0.3)  # simulate I/O

    filename = os.path.basename(file_path)
    mime_type, _ = mimetypes.guess_type(filename)
    mime_type = mime_type or "application/octet-stream"
    ext = os.path.splitext(filename)[1].lower()

    # Fallback category detection by extension
    category = CATEGORY_MAP.get(mime_type, "unknown")
    if category == "unknown":
        if ext in (".pdf",):
            category = "pdf"
        elif ext in (".docx",):
            category = "docx"
        elif ext in (".txt", ".md", ".csv"):
            category = "text"
        elif ext in (".png", ".jpg", ".jpeg", ".tiff", ".gif", ".webp"):
            category = "image"

    # Simulate file size (use real size if file exists, otherwise fake it)
    try:
        file_size = os.path.getsize(file_path)
    except OSError:
        file_size = 1_240_000  # ~1.2MB stub

    result = {
        "filename": filename,
        "mime_type": mime_type,
        "category": category,
        "extension": ext,
        "file_size": file_size,
        "detected_at": datetime.utcnow().isoformat(),
    }
    activity.logger.info(f"[detect_file_type] Result: {category} ({mime_type})")
    return result


# ─── Raw Document Storage ───────────────────────────────────────────────────

@activity.defn
async def store_document_s3(doc_id: str, file_path: str, content_type: str) -> str:
    """Upload raw document to S3 (stub: simulates LocalStack upload)."""
    activity.logger.info(f"[store_document_s3] Uploading {file_path} as {doc_id}")
    await asyncio.sleep(0.5)  # simulate network I/O

    s3_key = f"documents/{doc_id}/{os.path.basename(file_path)}"
    activity.logger.info(f"[store_document_s3] Stored at s3://docmgr-raw-documents/{s3_key}")
    return s3_key


@activity.defn
async def store_document_filesystem(doc_id: str, file_path: str) -> str:
    """Copy raw document to managed filesystem location (stub)."""
    activity.logger.info(f"[store_document_filesystem] Storing {file_path} for {doc_id}")
    await asyncio.sleep(0.3)

    dest_dir = f"/app/storage/documents/{doc_id}"
    dest_path = f"{dest_dir}/{os.path.basename(file_path)}"
    # In real impl: os.makedirs(dest_dir, exist_ok=True); shutil.copy2(...)
    activity.logger.info(f"[store_document_filesystem] Stored at {dest_path}")
    return dest_path


# ─── Text Extraction ────────────────────────────────────────────────────────

@activity.defn
async def extract_text_from_pdf(file_path: str) -> dict:
    """Extract text from PDF using PyPDF2 (stub)."""
    activity.logger.info(f"[extract_text_from_pdf] Processing: {file_path}")

    # Simulate page-by-page extraction with heartbeats
    page_count = 5
    for i in range(page_count):
        await asyncio.sleep(0.4)
        activity.heartbeat(f"Page {i + 1}/{page_count}")

    extracted_text = (
        f"[Simulated PDF text extracted from {os.path.basename(file_path)}]\n\n"
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
        "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
        "Quarterly revenue increased by 12% year-over-year, driven by strong "
        "performance in the enterprise segment.\n\n"
        "Section 2: Financial Overview\n"
        "Total assets reached $4.2 billion, marking a significant milestone."
    )

    return {
        "text": extracted_text,
        "page_count": page_count,
        "word_count": len(extracted_text.split()),
        "method": "PyPDF2",
    }


@activity.defn
async def extract_text_from_docx(file_path: str) -> dict:
    """Extract text from DOCX using python-docx (stub)."""
    activity.logger.info(f"[extract_text_from_docx] Processing: {file_path}")
    await asyncio.sleep(0.5)

    extracted_text = (
        f"[Simulated DOCX text extracted from {os.path.basename(file_path)}]\n\n"
        "This document contains project specifications and requirements. "
        "The deliverables include a comprehensive analysis of market trends "
        "and a detailed implementation roadmap for Q3."
    )

    return {
        "text": extracted_text,
        "paragraph_count": 8,
        "table_count": 2,
        "word_count": len(extracted_text.split()),
    }


@activity.defn
async def extract_text_plain(file_path: str) -> dict:
    """Read plain text file directly (stub)."""
    activity.logger.info(f"[extract_text_plain] Reading: {file_path}")
    await asyncio.sleep(0.2)

    # Try to read the real file if it exists
    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            text = f.read()
    except (OSError, IOError):
        text = f"[Simulated plain text content from {os.path.basename(file_path)}]"

    return {
        "text": text,
        "word_count": len(text.split()),
        "char_count": len(text),
    }


# ─── OCR Activities ─────────────────────────────────────────────────────────

@activity.defn
async def extract_images_from_pdf(file_path: str, doc_id: str) -> dict:
    """Extract embedded images from PDF pages (stub)."""
    activity.logger.info(f"[extract_images_from_pdf] Extracting images from: {file_path}")
    await asyncio.sleep(0.8)

    image_paths = [
        f"/app/storage/images/{doc_id}/page_1.png",
        f"/app/storage/images/{doc_id}/page_3.png",
    ]

    return {
        "image_paths": image_paths,
        "image_count": len(image_paths),
        "doc_id": doc_id,
    }


@activity.defn
async def ocr_single_image(image_path: str) -> dict:
    """OCR a single image with Tesseract (stub)."""
    activity.logger.info(f"[ocr_single_image] Running OCR on: {image_path}")
    await asyncio.sleep(1.0)  # OCR is slow

    return {
        "text": f"[OCR text extracted from {os.path.basename(image_path)}] "
                "Invoice #INV-2024-0847 — Total: $12,450.00 — Due: 2024-03-15",
        "confidence": 94.7,
        "language": "eng",
    }


@activity.defn
async def ocr_batch_images(image_paths: list) -> dict:
    """OCR multiple images in batch (stub)."""
    activity.logger.info(f"[ocr_batch_images] Processing {len(image_paths)} images")

    results = []
    for i, path in enumerate(image_paths):
        await asyncio.sleep(0.6)
        activity.heartbeat(f"Image {i + 1}/{len(image_paths)}")
        results.append({
            "path": path,
            "text": f"[OCR page {i + 1}] Extracted content from embedded image.",
            "confidence": 91.2 + i * 1.5,
        })

    combined_text = "\n\n".join(r["text"] for r in results)
    avg_confidence = sum(r["confidence"] for r in results) / len(results) if results else 0

    return {
        "combined_text": combined_text,
        "per_image": results,
        "total_images": len(image_paths),
        "avg_confidence": round(avg_confidence, 1),
    }


# ─── Text Storage ───────────────────────────────────────────────────────────

@activity.defn
async def store_text_s3(doc_id: str, text: str) -> str:
    """Store extracted text to S3 (stub)."""
    activity.logger.info(f"[store_text_s3] Storing text for {doc_id}")
    await asyncio.sleep(0.3)

    s3_key = f"text/{doc_id}/extracted.txt"
    activity.logger.info(f"[store_text_s3] Stored at s3://docmgr-extracted-text/{s3_key}")
    return s3_key


@activity.defn
async def store_text_filesystem(doc_id: str, text: str) -> str:
    """Store extracted text to filesystem (stub)."""
    activity.logger.info(f"[store_text_filesystem] Storing text for {doc_id}")
    await asyncio.sleep(0.2)

    dest_path = f"/app/storage/text/{doc_id}/extracted.txt"
    return dest_path


@activity.defn
async def store_text_elasticsearch(
    doc_id: str, text: str, filename: str, file_type: str, metadata: dict
) -> str:
    """Index extracted text in Elasticsearch (stub)."""
    activity.logger.info(f"[store_text_elasticsearch] Indexing {doc_id} in ES")
    await asyncio.sleep(0.4)

    es_doc_id = f"es-{doc_id}"
    activity.logger.info(
        f"[store_text_elasticsearch] Indexed {len(text.split())} words "
        f"for {filename} → {es_doc_id}"
    )
    return es_doc_id


# ─── Image Storage ──────────────────────────────────────────────────────────

@activity.defn
async def store_images_s3(doc_id: str, image_paths: list) -> dict:
    """Store extracted images to S3 (stub)."""
    activity.logger.info(f"[store_images_s3] Storing {len(image_paths)} images for {doc_id}")
    await asyncio.sleep(0.4)

    s3_keys = [
        f"images/{doc_id}/{os.path.basename(p)}" for p in image_paths
    ]
    return {"s3_keys": s3_keys, "count": len(s3_keys)}


# ─── Metadata Indexing ──────────────────────────────────────────────────────

@activity.defn
async def index_document_metadata(
    doc_id: str,
    filename: str,
    file_type: dict,
    s3_key: str,
    fs_path: str,
    text_stats: dict,
) -> str:
    """Index full document metadata in Elasticsearch (stub)."""
    activity.logger.info(f"[index_document_metadata] Indexing metadata for {doc_id}")
    await asyncio.sleep(0.3)

    es_doc_id = f"meta-{doc_id}"
    activity.logger.info(
        f"[index_document_metadata] Metadata indexed: "
        f"{filename} | {file_type.get('category', 'unknown')} | "
        f"{text_stats.get('word_count', 0)} words → {es_doc_id}"
    )
    return es_doc_id


# ─── All activities list (for worker registration) ─────────────────────────

ALL_ACTIVITIES = [
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
]
