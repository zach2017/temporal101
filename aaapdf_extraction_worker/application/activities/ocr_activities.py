"""
Temporal Activities for the Image-OCR worker (STUB).

This worker runs on the `image-ocr-queue` task queue and is responsible
for extracting text from images stored in S3.

Replace the placeholder implementation with your preferred OCR engine
(Tesseract, Amazon Textract, Google Vision, Azure AI, etc.).
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
        "page_number": int,
        "image_index": int,
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
    page_number = payload["page_number"]
    image_index = payload["image_index"]

    activity.heartbeat(f"OCR processing {s3_key}")
    logger.info("activity.ocr.start", s3_key=s3_key)

    # ── Download image from S3 ────────────────────────────────
    gw = S3Gateway()
    image_bytes = gw.download_image_bytes(s3_key)

    # ── TODO: Replace with real OCR engine ────────────────────
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
        f"page {page_number} image {image_index}"
    )

    logger.info(
        "activity.ocr.done",
        s3_key=s3_key,
        text_length=len(extracted_text),
    )

    return {
        "document_name": document_name,
        "page_number": page_number,
        "image_index": image_index,
        "extracted_text": extracted_text,
    }
