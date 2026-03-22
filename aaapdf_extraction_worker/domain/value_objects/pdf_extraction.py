"""
Domain value objects – immutable data carriers with no infrastructure coupling.

These define the *language* of the PDF-extraction bounded context.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class PdfFileReference:
    """Identifies a single PDF waiting to be processed."""

    file_name: str  # e.g. "invoice_2024.pdf"
    file_location: str  # absolute path or URI to the source file


@dataclass(frozen=True)
class ExtractedText:
    """Text payload extracted from all pages of a PDF."""

    document_name: str
    pages: dict[int, str] = field(default_factory=dict)  # page_number → text

    @property
    def full_text(self) -> str:
        return "\n\n".join(
            f"--- Page {p} ---\n{t}" for p, t in sorted(self.pages.items())
        )


@dataclass(frozen=True)
class ExtractedImage:
    """Metadata for a single image pulled from a PDF page."""

    document_name: str
    page_number: int
    image_index: int  # order on the page
    image_bytes: bytes
    extension: str  # "png", "jpeg", …

    @property
    def s3_object_key(self) -> str:
        return (
            f"{self.document_name}/extracted_images/"
            f"page_{self.page_number}_img_{self.image_index}.{self.extension}"
        )


@dataclass(frozen=True)
class ImageOcrRequest:
    """Request to OCR a single image stored in S3."""

    s3_bucket: str
    s3_key: str
    document_name: str
    page_number: int
    image_index: int


@dataclass(frozen=True)
class ImageOcrResult:
    """Result from the OCR worker for one image."""

    document_name: str
    page_number: int
    image_index: int
    extracted_text: str


@dataclass(frozen=True)
class PdfExtractionResult:
    """Aggregate result returned at workflow completion."""

    document_name: str
    text_s3_key: str
    image_s3_keys: list[str]
    ocr_results: list[ImageOcrResult]
