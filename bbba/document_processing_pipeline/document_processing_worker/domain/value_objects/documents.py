"""
Domain value objects – immutable data carriers with no infrastructure coupling.

These define the *language* of the document-processing bounded context.
Every worker, workflow, and activity speaks in terms of these types.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum


# ── Document Category ─────────────────────────────────────────


class DocumentCategory(StrEnum):
    """High-level classification derived from MIME type."""

    PDF = "pdf"
    IMAGE = "image"
    OFFICE_DOCUMENT = "office_document"  # docx, xlsx, pptx, odt, ods, odp
    PLAIN_TEXT = "plain_text"            # txt, csv, tsv, log, md, rst
    RICH_TEXT = "rich_text"              # rtf
    HTML = "html"                        # html, xhtml
    EBOOK = "ebook"                      # epub
    EMAIL = "email"                      # eml, msg
    UNKNOWN = "unknown"


# ── Core File Reference ──────────────────────────────────────


@dataclass(frozen=True)
class DocumentFileReference:
    """
    Identifies a single document waiting to be processed.

    This is the canonical input to every workflow in the pipeline.
    ``file_type`` is the detected MIME type (e.g. "application/pdf").
    """

    file_name: str        # e.g. "invoice_2024.pdf"
    file_location: str    # absolute path or URI
    file_type: str = ""   # MIME type, e.g. "application/pdf"

    @property
    def stem(self) -> str:
        """File name without extension."""
        if "." in self.file_name:
            return self.file_name.rsplit(".", 1)[0]
        return self.file_name

    @property
    def extension(self) -> str:
        """Lowercase file extension without the dot."""
        if "." in self.file_name:
            return self.file_name.rsplit(".", 1)[1].lower()
        return ""


# ── MIME Detection Result ─────────────────────────────────────


@dataclass(frozen=True)
class MimeDetectionResult:
    """Output of the MIME-type detection activity."""

    file_name: str
    file_location: str
    mime_type: str          # e.g. "application/pdf", "image/png"
    category: str           # DocumentCategory value
    encoding: str = ""      # charset for text types


# ── Extracted Text ────────────────────────────────────────────


@dataclass(frozen=True)
class ExtractedText:
    """Text payload extracted from a document (any type)."""

    document_name: str
    source_mime_type: str
    pages: dict[int, str] = field(default_factory=dict)  # page_number → text
    full_text: str = ""     # for non-paged documents

    @property
    def combined_text(self) -> str:
        if self.pages:
            return "\n\n".join(
                f"--- Page {p} ---\n{t}" for p, t in sorted(self.pages.items())
            )
        return self.full_text


# ── Extracted Image ───────────────────────────────────────────


@dataclass(frozen=True)
class ExtractedImage:
    """Metadata for a single image pulled from a PDF page."""

    document_name: str
    page_number: int
    image_index: int
    image_bytes: bytes
    extension: str          # "png", "jpeg", …

    @property
    def s3_object_key(self) -> str:
        return (
            f"{self.document_name}/extracted_images/"
            f"page_{self.page_number}_img_{self.image_index}.{self.extension}"
        )


# ── Image OCR ────────────────────────────────────────────────


@dataclass(frozen=True)
class ImageOcrRequest:
    """Request to OCR a single image – works for PDF-extracted images
    AND standalone image documents."""

    s3_bucket: str
    s3_key: str
    document_name: str
    page_number: int = 0
    image_index: int = 0


@dataclass(frozen=True)
class ImageOcrResult:
    """Result from the OCR worker for one image."""

    document_name: str
    page_number: int
    image_index: int
    extracted_text: str


# ── Document Conversion ──────────────────────────────────────


@dataclass(frozen=True)
class ConversionRequest:
    """Request to convert a non-PDF/non-image document to text."""

    file_name: str
    file_location: str
    mime_type: str
    category: str           # DocumentCategory value
    document_name: str


@dataclass(frozen=True)
class ConversionResult:
    """Text output from the document conversion worker."""

    document_name: str
    source_mime_type: str
    extracted_text: str
    page_count: int = 1


# ── Pipeline Result ───────────────────────────────────────────


@dataclass(frozen=True)
class DocumentProcessingResult:
    """Aggregate result returned at workflow completion."""

    document_name: str
    source_mime_type: str
    category: str
    text_s3_key: str
    image_s3_keys: list[str] = field(default_factory=list)
    ocr_results: list[dict] = field(default_factory=list)
