"""
Temporal Activities – document-to-text conversion.

Runs on the ``document-conversion-queue``.  Handles docx, xlsx, pptx,
odt, rtf, html, epub, email, plain text, csv, etc.
"""

from __future__ import annotations

import structlog
from temporalio import activity

from domain.services.document_conversion import DocumentConversionService
from domain.value_objects.documents import ConversionRequest

logger = structlog.get_logger()


@activity.defn(name="convert_document_to_text")
async def convert_document_to_text(payload: dict) -> dict:
    """
    Convert a non-PDF, non-image document into plain text.

    Input
    -----
    {
        "file_name": str,
        "file_location": str,
        "mime_type": str,
        "category": str,
        "document_name": str,
    }

    Output
    ------
    {
        "document_name": str,
        "source_mime_type": str,
        "extracted_text": str,
        "page_count": int,
    }
    """
    file_name = payload["file_name"]
    activity.heartbeat(f"Converting {file_name}")
    logger.info(
        "activity.convert.start",
        file_name=file_name,
        mime_type=payload["mime_type"],
        category=payload["category"],
    )

    request = ConversionRequest(
        file_name=payload["file_name"],
        file_location=payload["file_location"],
        mime_type=payload["mime_type"],
        category=payload["category"],
        document_name=payload["document_name"],
    )

    result = DocumentConversionService.convert(request)

    logger.info(
        "activity.convert.done",
        document_name=result.document_name,
        text_length=len(result.extracted_text),
        page_count=result.page_count,
    )

    return {
        "document_name": result.document_name,
        "source_mime_type": result.source_mime_type,
        "extracted_text": result.extracted_text,
        "page_count": result.page_count,
    }
