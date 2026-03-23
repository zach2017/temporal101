"""
Temporal Activities – PDF text and image extraction + S3 persistence.

Runs on the ``pdf-extraction-queue``.
Every file-touching activity validates the path first and fails
immediately with a clear error if the file is missing.
"""

from __future__ import annotations

from dataclasses import asdict

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from domain.services.pdf_processing import PdfProcessingService
from domain.value_objects.documents import (
    DocumentFileReference,
    ExtractedImage,
    ImageOcrRequest,
)
from infrastructure.file_validation import validate_file_exists, FileNotFoundError
from infrastructure.s3.gateway import S3Gateway

logger = structlog.get_logger()


@activity.defn(name="extract_text_from_pdf")
async def extract_text_from_pdf(payload: dict) -> dict:
    file_name = payload["file_name"]
    file_location = payload["file_location"]

    activity.heartbeat(f"Validating file: {file_location}")

    try:
        validate_file_exists(file_location, context="extract_text_from_pdf")
    except FileNotFoundError as e:
        raise ApplicationError(
            str(e), type="FileNotFoundError", non_retryable=True
        )

    doc_ref = DocumentFileReference(
        file_name=file_name,
        file_location=file_location,
        file_type=payload.get("file_type", "application/pdf"),
    )

    activity.heartbeat(f"Extracting text from {doc_ref.file_name}")
    logger.info("activity.extract_text.start",
                file_name=doc_ref.file_name,
                file_location=file_location)

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
    file_name = payload["file_name"]
    file_location = payload["file_location"]

    activity.heartbeat(f"Validating file: {file_location}")

    try:
        validate_file_exists(file_location, context="extract_images_from_pdf")
    except FileNotFoundError as e:
        raise ApplicationError(
            str(e), type="FileNotFoundError", non_retryable=True
        )

    doc_ref = DocumentFileReference(
        file_name=file_name,
        file_location=file_location,
        file_type=payload.get("file_type", "application/pdf"),
    )

    activity.heartbeat(f"Extracting images from {doc_ref.file_name}")
    logger.info("activity.extract_images.start",
                file_name=doc_ref.file_name,
                file_location=file_location)

    images: list[ExtractedImage] = PdfProcessingService.extract_images(doc_ref)

    results = []
    for img in images:
        results.append({
            "document_name": img.document_name,
            "page_number": img.page_number,
            "image_index": img.image_index,
            "extension": img.extension,
            "s3_object_key": img.s3_object_key,
            "image_bytes_len": len(img.image_bytes),
            "_image_bytes": img.image_bytes,
        })

    logger.info("activity.extract_images.done", image_count=len(results))
    return results


@activity.defn(name="store_extracted_text_to_s3")
async def store_extracted_text_to_s3(payload: dict) -> str:
    document_name = payload["document_name"]
    pages = payload.get("pages", {})
    full_text = payload.get("full_text", "")
    source_mime = payload.get("source_mime_type", "")

    activity.heartbeat(f"Uploading text for {document_name}")
    logger.info("activity.store_text.start", document_name=document_name)

    gw = S3Gateway()
    gw.ensure_bucket_exists()
    s3_key = gw.upload_extracted_text(
        document_name, pages=pages, full_text=full_text,
        source_mime_type=source_mime,
    )

    logger.info("activity.store_text.done", s3_key=s3_key)
    return s3_key


@activity.defn(name="store_image_to_s3")
async def store_image_to_s3(payload: dict) -> str:
    s3_key = payload["s3_object_key"]
    image_bytes = payload["image_bytes"]
    extension = payload["extension"]

    activity.heartbeat(f"Uploading image {s3_key}")

    gw = S3Gateway()
    gw.ensure_bucket_exists()
    gw.upload_image(s3_key, image_bytes, extension)

    logger.info("activity.store_image.done", s3_key=s3_key)
    return s3_key


@activity.defn(name="build_ocr_requests")
async def build_ocr_requests(payload: dict) -> list[dict]:
    document_name = payload["document_name"]
    image_s3_keys = payload["image_s3_keys"]
    image_metadata = payload["image_metadata"]
    s3_bucket = payload["s3_bucket"]

    requests = []
    for key, meta in zip(image_s3_keys, image_metadata):
        req = ImageOcrRequest(
            s3_bucket=s3_bucket,
            s3_key=key,
            document_name=document_name,
            page_number=meta["page_number"],
            image_index=meta["image_index"],
        )
        requests.append(asdict(req))

    logger.info("activity.build_ocr_requests.done", count=len(requests))
    return requests
