"""
Domain service – MIME type detection and document categorisation.

Uses python-magic (libmagic) for reliable content-based detection,
falling back to extension-based heuristics when libmagic is unavailable.
"""

from __future__ import annotations

import mimetypes

from domain.value_objects.documents import DocumentCategory

# MIME → category mapping
_MIME_CATEGORY_MAP: dict[str, DocumentCategory] = {
    # PDF
    "application/pdf": DocumentCategory.PDF,
    # Images
    "image/png": DocumentCategory.IMAGE,
    "image/jpeg": DocumentCategory.IMAGE,
    "image/tiff": DocumentCategory.IMAGE,
    "image/bmp": DocumentCategory.IMAGE,
    "image/gif": DocumentCategory.IMAGE,
    "image/webp": DocumentCategory.IMAGE,
    "image/svg+xml": DocumentCategory.IMAGE,
    "image/heic": DocumentCategory.IMAGE,
    "image/heif": DocumentCategory.IMAGE,
    # Office documents
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.openxmlformats-officedocument.presentationml.presentation": DocumentCategory.OFFICE_DOCUMENT,
    "application/msword": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.ms-excel": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.ms-powerpoint": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.oasis.opendocument.text": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.oasis.opendocument.spreadsheet": DocumentCategory.OFFICE_DOCUMENT,
    "application/vnd.oasis.opendocument.presentation": DocumentCategory.OFFICE_DOCUMENT,
    # Plain text
    "text/plain": DocumentCategory.PLAIN_TEXT,
    "text/csv": DocumentCategory.PLAIN_TEXT,
    "text/tab-separated-values": DocumentCategory.PLAIN_TEXT,
    "text/markdown": DocumentCategory.PLAIN_TEXT,
    "text/x-rst": DocumentCategory.PLAIN_TEXT,
    # Rich text
    "text/rtf": DocumentCategory.RICH_TEXT,
    "application/rtf": DocumentCategory.RICH_TEXT,
    # HTML
    "text/html": DocumentCategory.HTML,
    "application/xhtml+xml": DocumentCategory.HTML,
    "text/xml": DocumentCategory.HTML,
    "application/xml": DocumentCategory.HTML,
    # Ebook
    "application/epub+zip": DocumentCategory.EBOOK,
    # Email
    "message/rfc822": DocumentCategory.EMAIL,
    "application/vnd.ms-outlook": DocumentCategory.EMAIL,
}


class MimeDetectionService:
    """Stateless service that detects MIME types and maps to categories."""

    @staticmethod
    def detect_mime(file_path: str, file_name: str) -> tuple[str, str]:
        """
        Detect MIME type and encoding.

        Returns (mime_type, encoding).
        Tries libmagic first, falls back to extension.
        """
        mime_type = ""
        encoding = ""

        # Primary: libmagic content-based detection
        try:
            import magic
            mime_type = magic.from_file(file_path, mime=True)
        except Exception:
            pass

        # Fallback: extension-based
        if not mime_type or mime_type == "application/octet-stream":
            guessed, _ = mimetypes.guess_type(file_name)
            if guessed:
                mime_type = guessed

        # Detect encoding for text types
        if mime_type and mime_type.startswith("text/"):
            try:
                import chardet
                with open(file_path, "rb") as f:
                    raw = f.read(8192)
                result = chardet.detect(raw)
                encoding = result.get("encoding", "") or ""
            except Exception:
                encoding = "utf-8"

        return mime_type or "application/octet-stream", encoding

    @staticmethod
    def categorise(mime_type: str) -> DocumentCategory:
        """Map a MIME type to a DocumentCategory."""
        if mime_type in _MIME_CATEGORY_MAP:
            return _MIME_CATEGORY_MAP[mime_type]

        # Prefix-based fallbacks
        if mime_type.startswith("image/"):
            return DocumentCategory.IMAGE
        if mime_type.startswith("text/"):
            return DocumentCategory.PLAIN_TEXT

        return DocumentCategory.UNKNOWN
