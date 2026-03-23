"""
Temporal Activities – Image-to-Text (OCR) worker.

Runs on the ``image-ocr-queue``.  Used by:
  1. PdfExtractionWorkflow – for images extracted from PDFs
  2. DocumentIntakeWorkflow – for standalone image documents

Replace the stub with your preferred OCR engine.
"""

from __future__ import annotations

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from infrastructure.file_validation import validate_file_exists, FileNotFoundError
from infrastructure.s3.gateway import S3Gateway

logger = structlog.get_logger()


@activity.defn(name="ocr_extract_text_from_image")
async def ocr_extract_text_from_image(payload: dict) -> dict:
    s3_key = payload["s3_key"]
    document_name = payload["document_name"]
    page_number = payload.get("page_number", 0)
    image_index = payload.get("image_index", 0)

    activity.heartbeat(f"OCR processing {s3_key}")
    logger.info("activity.ocr.start",
                s3_key=s3_key,
                document_name=document_name)

    gw = S3Gateway()
    image_bytes = gw.download_bytes(s3_key)

    logger.info("activity.ocr.downloaded",
                s3_key=s3_key,
                size_bytes=len(image_bytes))

    # ── TODO: Replace with real OCR engine ────────────────────
    extracted_text = (
        f"[STUB] OCR placeholder for {document_name} "
        f"page={page_number} image={image_index} "
        f"({len(image_bytes)} bytes)"
    )

    logger.info("activity.ocr.done",
                s3_key=s3_key,
                text_length=len(extracted_text))

    return {
        "document_name": document_name,
        "page_number": page_number,
        "image_index": image_index,
        "extracted_text": extracted_text,
    }


@activity.defn(name="upload_image_for_ocr")
async def upload_image_for_ocr(payload: dict) -> str:
    file_location = payload["file_location"]
    s3_key = payload["s3_key"]
    extension = payload["extension"]

    activity.heartbeat(f"Validating image file: {file_location}")

    try:
        validate_file_exists(file_location, context="upload_image_for_ocr")
    except FileNotFoundError as e:
        raise ApplicationError(
            str(e), type="FileNotFoundError", non_retryable=True
        )

    logger.info("activity.upload_image_for_ocr.start",
                file_location=file_location,
                s3_key=s3_key)

    gw = S3Gateway()
    gw.ensure_bucket_exists()

    with open(file_location, "rb") as f:
        image_bytes = f.read()

    gw.upload_image(s3_key, image_bytes, extension)

    logger.info("activity.upload_image_for_ocr.done",
                s3_key=s3_key,
                size_bytes=len(image_bytes))
    return s3_key
