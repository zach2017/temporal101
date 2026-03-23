"""
Temporal Activities – MIME type detection.

Runs on the ``document-intake-queue``.  Detects the MIME type
of an incoming file and classifies it into a DocumentCategory.
"""

from __future__ import annotations

import structlog
from temporalio import activity

from domain.services.mime_detection import MimeDetectionService
from domain.value_objects.documents import DocumentCategory

logger = structlog.get_logger()


@activity.defn(name="detect_mime_type")
async def detect_mime_type(payload: dict) -> dict:
    """
    Detect MIME type and document category.

    Input:  {"file_name": str, "file_location": str, "file_type": str}
    Output: {"file_name", "file_location", "mime_type", "category", "encoding"}
    """
    file_name = payload["file_name"]
    file_location = payload["file_location"]
    file_type_hint = payload.get("file_type", "")

    activity.heartbeat(f"Detecting MIME for {file_name}")
    logger.info("activity.detect_mime.start", file_name=file_name)

    svc = MimeDetectionService()

    # Use provided file_type hint if already set, otherwise detect
    if file_type_hint and file_type_hint != "application/octet-stream":
        mime_type = file_type_hint
        encoding = ""
    else:
        mime_type, encoding = svc.detect_mime(file_location, file_name)

    category = svc.categorise(mime_type)

    logger.info(
        "activity.detect_mime.done",
        file_name=file_name,
        mime_type=mime_type,
        category=category.value,
    )

    return {
        "file_name": file_name,
        "file_location": file_location,
        "mime_type": mime_type,
        "category": category.value,
        "encoding": encoding,
    }
