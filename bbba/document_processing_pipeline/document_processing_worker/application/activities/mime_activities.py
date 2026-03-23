"""
Temporal Activities – Python-based MIME type detection (fallback).

Kept as a backup if the Java Tika worker is unavailable.
Not used by default — the intake workflow calls the Tika worker instead.
"""

from __future__ import annotations

import structlog
from temporalio import activity
from temporalio.exceptions import ApplicationError

from domain.services.mime_detection import MimeDetectionService
from infrastructure.file_validation import validate_file_exists, FileNotFoundError

logger = structlog.get_logger()


@activity.defn(name="detect_mime_type")
async def detect_mime_type(payload: dict) -> dict:
    file_name = payload["file_name"]
    file_location = payload["file_location"]
    file_type_hint = payload.get("file_type", "")

    activity.heartbeat(f"Validating file: {file_location}")

    try:
        validate_file_exists(file_location, context="detect_mime_type")
    except FileNotFoundError as e:
        raise ApplicationError(
            str(e), type="FileNotFoundError", non_retryable=True
        )

    logger.info("activity.detect_mime.start",
                file_name=file_name,
                file_location=file_location)

    svc = MimeDetectionService()

    if file_type_hint and file_type_hint != "application/octet-stream":
        mime_type = file_type_hint
        encoding = ""
    else:
        mime_type, encoding = svc.detect_mime(file_location, file_name)

    category = svc.categorise(mime_type)

    logger.info("activity.detect_mime.done",
                file_name=file_name,
                mime_type=mime_type,
                category=category.value)

    return {
        "file_name": file_name,
        "file_location": file_location,
        "mime_type": mime_type,
        "category": category.value,
        "encoding": encoding,
    }
