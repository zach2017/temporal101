"""
Shared domain models for the PDF-to-Text Temporal service.
Field names use snake_case — the Java client maps via Jackson PropertyNamingStrategy.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


class StorageType(str, Enum):
    S3 = "S3"
    NFS = "NFS"
    URL = "URL"


@dataclass
class PdfProcessingRequest:
    """Inbound request — mirrors Java PdfProcessingRequest."""
    file_name: str
    storage_type: str          # StorageType value as string
    location: str              # S3 URI, NFS dir, or base URL
    extract_images: bool = True


@dataclass
class ExtractedImage:
    """Metadata for one stored image (no pixel data held in memory)."""
    image_name: str
    page_number: int
    storage_path: str


@dataclass
class PdfProcessingResult:
    """Outbound result — mirrors Java PdfProcessingResult.
    NOTE: text_content is intentionally left empty for large files;
    the full text lives at text_storage_path."""
    file_name: str
    storage_type: str
    text_storage_path: str
    page_count: int = 0
    image_count: int = 0
    text_content: str = ""     # summary / first-N-chars only for huge PDFs
    images: List[ExtractedImage] = field(default_factory=list)
    success: bool = True
    error_message: Optional[str] = None
