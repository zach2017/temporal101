"""
Domain service – pure PDF processing logic with no infrastructure awareness.

Uses PyMuPDF (fitz) which handles text + image extraction without external
binaries like poppler or ghostscript.
"""

from __future__ import annotations

import fitz  # pymupdf

from domain.value_objects.documents import (
    DocumentFileReference,
    ExtractedImage,
    ExtractedText,
)


class PdfProcessingService:
    """Stateless domain service that knows *how* to process a PDF."""

    @staticmethod
    def extract_text(doc_ref: DocumentFileReference) -> ExtractedText:
        """Extract text from every page of the PDF."""
        doc = fitz.open(doc_ref.file_location)
        pages: dict[int, str] = {}
        try:
            for page_num in range(len(doc)):
                page = doc[page_num]
                text = page.get_text("text")
                if text.strip():
                    pages[page_num + 1] = text
        finally:
            doc.close()

        return ExtractedText(
            document_name=doc_ref.stem,
            source_mime_type=doc_ref.file_type or "application/pdf",
            pages=pages,
        )

    @staticmethod
    def extract_images(doc_ref: DocumentFileReference) -> list[ExtractedImage]:
        """Extract every embedded image from the PDF."""
        doc = fitz.open(doc_ref.file_location)
        images: list[ExtractedImage] = []

        try:
            for page_num in range(len(doc)):
                page = doc[page_num]
                image_list = page.get_images(full=True)

                for img_index, img_info in enumerate(image_list):
                    xref = img_info[0]
                    base_image = doc.extract_image(xref)
                    if base_image is None:
                        continue

                    images.append(
                        ExtractedImage(
                            document_name=doc_ref.stem,
                            page_number=page_num + 1,
                            image_index=img_index,
                            image_bytes=base_image["image"],
                            extension=base_image["ext"],
                        )
                    )
        finally:
            doc.close()

        return images
