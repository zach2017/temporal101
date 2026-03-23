"""
Temporal Activities – document-to-text conversion.
Runs on ``document-conversion-queue``.
"""

from __future__ import annotations

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from domain.services.document_conversion import DocumentConversionService
from domain.value_objects.documents import ConversionRequest
from infrastructure.file_validation import validate_file_exists, FileNotFoundError

logger = structlog.get_logger()


@activity.defn(name="convert_document_to_text")
async def convert_document_to_text(payload: dict) -> dict:
    file_location = payload["file_location"]

    try:
        validate_file_exists(file_location, context="convert_document_to_text")
    except FileNotFoundError as e:
        raise ApplicationError(str(e), type="FileNotFoundError", non_retryable=True)

    activity.heartbeat(f"Converting {payload['file_name']}")
    logger.info("activity.convert.start",
                file_name=payload["file_name"],
                file_location=file_location,
                mime_type=payload["mime_type"],
                category=payload["category"])

    request = ConversionRequest(
        file_name=payload["file_name"],
        file_location=file_location,
        mime_type=payload["mime_type"],
        category=payload["category"],
        document_name=payload["document_name"],
    )
    result = DocumentConversionService.convert(request)

    logger.info("activity.convert.done",
                document_name=result.document_name,
                text_length=len(result.extracted_text))
    return {
        "document_name": result.document_name,
        "source_mime_type": result.source_mime_type,
        "extracted_text": result.extracted_text,
        "page_count": result.page_count,
    }
