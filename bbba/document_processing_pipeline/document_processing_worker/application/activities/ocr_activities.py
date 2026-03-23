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

from infrastructure.s3.gateway import S3Gateway

logger = structlog.get_logger()


@activity.defn(name="ocr_extract_text_from_image")
async def ocr_extract_text_from_image(payload: dict) -> dict:
    """
    Download an image from S3 and run OCR on it.

    Input
    -----
    {
        "s3_bucket": str,
        "s3_key": str,
        "document_name": str,
        "page_number": int,   # 0 for standalone images
        "image_index": int,   # 0 for standalone images
    }

    Output
    ------
    {
        "document_name": str,
        "page_number": int,
        "image_index": int,
        "extracted_text": str,
    }
    """
    s3_key = payload["s3_key"]
    document_name = payload["document_name"]
    page_number = payload.get("page_number", 0)
    image_index = payload.get("image_index", 0)

    activity.heartbeat(f"OCR processing {s3_key}")
    logger.info("activity.ocr.start", s3_key=s3_key)

    gw = S3Gateway()
    image_bytes = gw.download_bytes(s3_key)

    # ── TODO: Replace with real OCR engine ────────────────────
    #
    # Example with Tesseract:
    #   from PIL import Image
    #   import pytesseract, io
    #   img = Image.open(io.BytesIO(image_bytes))
    #   extracted_text = pytesseract.image_to_string(img)
    #
    # Example with Amazon Textract:
    #   import boto3
    #   textract = boto3.client("textract")
    #   response = textract.detect_document_text(
    #       Document={"Bytes": image_bytes}
    #   )
    #   extracted_text = "\n".join(
    #       block["Text"] for block in response["Blocks"]
    #       if block["BlockType"] == "LINE"
    #   )

    extracted_text = (
        f"[STUB] OCR placeholder for {document_name} "
        f"page={page_number} image={image_index} "
        f"({len(image_bytes)} bytes)"
    )

    logger.info("activity.ocr.done", s3_key=s3_key, text_length=len(extracted_text))

    return {
        "document_name": document_name,
        "page_number": page_number,
        "image_index": image_index,
        "extracted_text": extracted_text,
    }


@activity.defn(name="upload_image_for_ocr")
async def upload_image_for_ocr(payload: dict) -> str:
    """
    Upload a standalone image file to S3 so the OCR activity can fetch it.

    Input:  {"file_location": str, "s3_key": str, "extension": str}
    Output: S3 key.
    """
    file_location = payload["file_location"]
    s3_key = payload["s3_key"]
    extension = payload["extension"]

    activity.heartbeat(f"Uploading image for OCR: {s3_key}")

    gw = S3Gateway()
    gw.ensure_bucket_exists()

    with open(file_location, "rb") as f:
        image_bytes = f.read()

    gw.upload_image(s3_key, image_bytes, extension)

    logger.info("activity.upload_image_for_ocr.done", s3_key=s3_key)
    return s3_key
