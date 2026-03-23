"""
Temporal Activities – PDF extraction + filesystem persistence.
Runs on ``pdf-extraction-queue``.
"""

from __future__ import annotations

from dataclasses import asdict

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from domain.services.pdf_processing import PdfProcessingService
from domain.value_objects.documents import DocumentFileReference, ExtractedImage
from infrastructure.file_validation import validate_file_exists, FileNotFoundError
from infrastructure.storage import FileStorageGateway

logger = structlog.get_logger()


@activity.defn(name="extract_text_from_pdf")
async def extract_text_from_pdf(payload: dict) -> dict:
    file_location = payload["file_location"]
    try:
        validate_file_exists(file_location, context="extract_text_from_pdf")
    except FileNotFoundError as e:
        raise ApplicationError(str(e), type="FileNotFoundError", non_retryable=True)

    doc_ref = DocumentFileReference(
        file_name=payload["file_name"],
        file_location=file_location,
        file_type=payload.get("file_type", "application/pdf"),
    )
    activity.heartbeat(f"Extracting text from {doc_ref.file_name}")
    extracted = PdfProcessingService.extract_text(doc_ref)
    logger.info("activity.extract_text.done",
                document_name=extracted.document_name,
                page_count=len(extracted.pages))
    return {
        "document_name": extracted.document_name,
        "source_mime_type": extracted.source_mime_type,
        "pages": {str(k): v for k, v in extracted.pages.items()},
    }


@activity.defn(name="extract_images_from_pdf")
async def extract_images_from_pdf(payload: dict) -> list[dict]:
    file_location = payload["file_location"]
    try:
        validate_file_exists(file_location, context="extract_images_from_pdf")
    except FileNotFoundError as e:
        raise ApplicationError(str(e), type="FileNotFoundError", non_retryable=True)

    doc_ref = DocumentFileReference(
        file_name=payload["file_name"],
        file_location=file_location,
        file_type=payload.get("file_type", "application/pdf"),
    )
    activity.heartbeat(f"Extracting images from {doc_ref.file_name}")
    images: list[ExtractedImage] = PdfProcessingService.extract_images(doc_ref)

    results = []
    for img in images:
        results.append({
            "document_name": img.document_name,
            "page_number": img.page_number,
            "image_index": img.image_index,
            "extension": img.extension,
            "image_bytes_len": len(img.image_bytes),
            "_image_bytes": img.image_bytes,
        })
    logger.info("activity.extract_images.done", image_count=len(results))
    return results


@activity.defn(name="store_extracted_text")
async def store_extracted_text(payload: dict) -> str:
    """Write extracted text JSON to the shared filesystem. Returns file path."""
    document_name = payload["document_name"]
    activity.heartbeat(f"Saving text for {document_name}")

    gw = FileStorageGateway()
    path = gw.save_extracted_text(
        document_name,
        pages=payload.get("pages"),
        full_text=payload.get("full_text", ""),
        source_mime_type=payload.get("source_mime_type", ""),
    )
    logger.info("activity.store_text.done", path=path)
    return path


@activity.defn(name="store_extracted_image")
async def store_extracted_image(payload: dict) -> str:
    """Write an extracted image to the shared filesystem. Returns file path."""
    document_name = payload["document_name"]
    activity.heartbeat(f"Saving image for {document_name}")

    gw = FileStorageGateway()
    path = gw.save_image(
        document_name=document_name,
        image_bytes=payload["image_bytes"],
        page_number=payload["page_number"],
        image_index=payload["image_index"],
        extension=payload["extension"],
    )
    logger.info("activity.store_image.done", path=path)
    return path


@activity.defn(name="build_ocr_requests")
async def build_ocr_requests(payload: dict) -> list[dict]:
    """Build OCR request payloads using filesystem paths."""
    document_name = payload["document_name"]
    image_paths = payload["image_paths"]
    image_metadata = payload["image_metadata"]

    requests = []
    for path, meta in zip(image_paths, image_metadata):
        requests.append({
            "image_path": path,
            "document_name": document_name,
            "page_number": meta["page_number"],
            "image_index": meta["image_index"],
        })
    logger.info("activity.build_ocr_requests.done", count=len(requests))
    return requests
