"""
Domain value objects – immutable data carriers with no infrastructure coupling.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum


class DocumentCategory(StrEnum):
    PDF = "pdf"
    IMAGE = "image"
    OFFICE_DOCUMENT = "office_document"
    PLAIN_TEXT = "plain_text"
    RICH_TEXT = "rich_text"
    HTML = "html"
    EBOOK = "ebook"
    EMAIL = "email"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class DocumentFileReference:
    file_name: str
    file_location: str
    file_type: str = ""

    @property
    def stem(self) -> str:
        if "." in self.file_name:
            return self.file_name.rsplit(".", 1)[0]
        return self.file_name

    @property
    def extension(self) -> str:
        if "." in self.file_name:
            return self.file_name.rsplit(".", 1)[1].lower()
        return ""


@dataclass(frozen=True)
class MimeDetectionResult:
    file_name: str
    file_location: str
    mime_type: str
    category: str
    encoding: str = ""


@dataclass(frozen=True)
class ExtractedText:
    document_name: str
    source_mime_type: str
    pages: dict[int, str] = field(default_factory=dict)
    full_text: str = ""

    @property
    def combined_text(self) -> str:
        if self.pages:
            return "\n\n".join(
                f"--- Page {p} ---\n{t}" for p, t in sorted(self.pages.items())
            )
        return self.full_text


@dataclass(frozen=True)
class ExtractedImage:
    document_name: str
    page_number: int
    image_index: int
    image_bytes: bytes
    extension: str


@dataclass(frozen=True)
class ImageOcrRequest:
    """Request to OCR an image stored on the shared filesystem."""
    image_path: str       # absolute path to the image on /files
    document_name: str
    page_number: int = 0
    image_index: int = 0


@dataclass(frozen=True)
class ImageOcrResult:
    document_name: str
    page_number: int
    image_index: int
    extracted_text: str


@dataclass(frozen=True)
class ConversionRequest:
    file_name: str
    file_location: str
    mime_type: str
    category: str
    document_name: str


@dataclass(frozen=True)
class ConversionResult:
    document_name: str
    source_mime_type: str
    extracted_text: str
    page_count: int = 1


@dataclass(frozen=True)
class DocumentProcessingResult:
    document_name: str
    source_mime_type: str
    category: str
    text_output_path: str
    image_output_paths: list[str] = field(default_factory=list)
    ocr_results: list[dict] = field(default_factory=list)
