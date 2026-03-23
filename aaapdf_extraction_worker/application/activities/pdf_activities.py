"""
Temporal Activities – each function is a single retryable unit of work.

Activities are the *only* place where side-effects (I/O, S3, filesystem)
are permitted.  Workflows orchestrate them but never do I/O directly.
"""

from __future__ import annotations

import json
from dataclasses import asdict

import structlog
from temporalio import activity

from domain.services.pdf_processing import PdfProcessingService
from domain.value_objects.pdf_extraction import (
    ExtractedImage,
    ImageOcrRequest,
    PdfFileReference,
)
from infrastructure.s3.gateway import S3Gateway

logger = structlog.get_logger()


# ─── PDF Text Extraction ─────────────────────────────────────


@activity.defn(name="extract_text_from_pdf")
async def extract_text_from_pdf(payload: dict) -> dict:
    """
    Extract text from every page of a PDF.

    Input:  {"file_name": str, "file_location": str}
    Output: {"document_name": str, "pages": {page_num: text, ...}}
    """
    pdf_ref = PdfFileReference(**payload)
    activity.heartbeat(f"Extracting text from {pdf_ref.file_name}")

    logger.info(
        "activity.extract_text.start",
        file_name=pdf_ref.file_name,
        file_location=pdf_ref.file_location,
    )

    extracted = PdfProcessingService.extract_text(pdf_ref)

    logger.info(
        "activity.extract_text.done",
        document_name=extracted.document_name,
        page_count=len(extracted.pages),
    )

    return {
        "document_name": extracted.document_name,
        "pages": {str(k): v for k, v in extracted.pages.items()},
    }


# ─── PDF Image Extraction ────────────────────────────────────


@activity.defn(name="extract_images_from_pdf")
async def extract_images_from_pdf(payload: dict) -> list[dict]:
    """
    Extract all embedded images from a PDF.

    Input:  {"file_name": str, "file_location": str}
    Output: list of serialisable image metadata dicts (bytes excluded).
    """
    pdf_ref = PdfFileReference(**payload)
    activity.heartbeat(f"Extracting images from {pdf_ref.file_name}")

    logger.info("activity.extract_images.start", file_name=pdf_ref.file_name)

    images: list[ExtractedImage] = PdfProcessingService.extract_images(pdf_ref)

    results = []
    for img in images:
        results.append(
            {
                "document_name": img.document_name,
                "page_number": img.page_number,
                "image_index": img.image_index,
                "extension": img.extension,
                "s3_object_key": img.s3_object_key,
                "image_bytes_len": len(img.image_bytes),
                # bytes travel via S3, not through Temporal payloads
                "_image_bytes": img.image_bytes,
            }
        )

    logger.info("activity.extract_images.done", image_count=len(results))
    return results


# ─── S3 Persistence ──────────────────────────────────────────


@activity.defn(name="store_extracted_text_to_s3")
async def store_extracted_text_to_s3(payload: dict) -> str:
    """
    Upload extracted text JSON to S3.

    Input:  {"document_name": str, "pages": {page_num: text}}
    Output: S3 key of the stored object.
    """
    document_name = payload["document_name"]
    pages = payload["pages"]

    activity.heartbeat(f"Uploading text for {document_name}")
    logger.info("activity.store_text.start", document_name=document_name)

   # gw = S3Gateway()
   # gw.ensure_bucket_exists()
   # s3_key = gw.upload_extracted_text(document_name, pages)

    #logger.info("activity.store_text.done", s3_key=s3_key)
    #return s3_key


@activity.defn(name="store_image_to_s3")
async def store_image_to_s3(payload: dict) -> str:
    """
    Upload a single extracted image to S3.

    Input:  {"s3_object_key": str, "image_bytes": bytes, "extension": str}
    Output: S3 key of the stored object.
    """
  
    activity.heartbeat(f"Uploading image here ")

   # gw = S3Gateway()
   # gw.ensure_bucket_exists()
   #gw.upload_image(s3_key, image_bytes, extension)

    logger.info("activity.store_image.done")
    return"s3_key"


# ─── Build OCR Requests ──────────────────────────────────────


@activity.defn(name="build_ocr_requests")
async def build_ocr_requests(payload: dict) -> list[dict]:
    """
    Create OCR request payloads for every extracted image.

    Input:  {"document_name": str, "image_s3_keys": [str, ...], "s3_bucket": str,
             "image_metadata": [{"page_number": int, "image_index": int}, ...]}
    Output: list of ImageOcrRequest dicts ready for the OCR child-workflow.
    """
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

    logger.info(
        "activity.build_ocr_requests.done",
        document_name=document_name,
        count=len(requests),
    )
    return requests
