"""
Temporal Activities – Image-to-Text (OCR).
Runs on ``image-ocr-queue``.  Reads images from the shared /files volume.
"""

from __future__ import annotations

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from infrastructure.file_validation import validate_file_exists, FileNotFoundError
from infrastructure.storage import FileStorageGateway

logger = structlog.get_logger()


@activity.defn(name="ocr_extract_text_from_image")
async def ocr_extract_text_from_image(payload: dict) -> dict:
    image_path = payload["image_path"]
    document_name = payload["document_name"]
    page_number = payload.get("page_number", 0)
    image_index = payload.get("image_index", 0)

    activity.heartbeat(f"OCR processing {image_path}")

    try:
        validate_file_exists(image_path, context="ocr_extract_text_from_image")
    except FileNotFoundError as e:
        raise ApplicationError(str(e), type="FileNotFoundError", non_retryable=True)

    gw = FileStorageGateway()
    image_bytes = gw.read_image_bytes(image_path)

    logger.info("activity.ocr.start",
                image_path=image_path, size_bytes=len(image_bytes))

    # ── TODO: Replace with real OCR engine ────────────────────
    extracted_text = (
        f"[STUB] OCR placeholder for {document_name} "
        f"page={page_number} image={image_index} "
        f"({len(image_bytes)} bytes)"
    )

    logger.info("activity.ocr.done", text_length=len(extracted_text))
    return {
        "document_name": document_name,
        "page_number": page_number,
        "image_index": image_index,
        "extracted_text": extracted_text,
    }


@activity.defn(name="copy_source_image")
async def copy_source_image(payload: dict) -> str:
    """Copy a source image into the document's output directory. Returns dest path."""
    file_location = payload["file_location"]
    document_name = payload["document_name"]
    extension = payload["extension"]

    activity.heartbeat(f"Copying source image for {document_name}")

    try:
        validate_file_exists(file_location, context="copy_source_image")
    except FileNotFoundError as e:
        raise ApplicationError(str(e), type="FileNotFoundError", non_retryable=True)

    gw = FileStorageGateway()
    dest = gw.save_source_image(document_name, file_location, extension)

    logger.info("activity.copy_source_image.done", src=file_location, dest=dest)
    return dest
