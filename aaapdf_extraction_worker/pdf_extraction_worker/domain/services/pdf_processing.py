"""
Domain service – pure PDF processing logic with no infrastructure awareness.

Uses PyMuPDF (fitz) which handles text + image extraction without external
binaries like poppler or ghostscript.
"""

from __future__ import annotations

import fitz  # pymupdf

from domain.value_objects.pdf_extraction import (
    ExtractedImage,
    ExtractedText,
    PdfFileReference,
)


class PdfProcessingService:
    """Stateless domain service that knows *how* to process a PDF."""

    @staticmethod
    def extract_text(pdf_ref: PdfFileReference) -> ExtractedText:
        """Extract text from every page of the PDF."""
        doc = fitz.open(pdf_ref.file_location)
        pages: dict[int, str] = {}
        try:
            for page_num in range(len(doc)):
                page = doc[page_num]
                text = page.get_text("text")
                if text.strip():
                    pages[page_num + 1] = text
        finally:
            doc.close()

        stem = _stem(pdf_ref.file_name)
        return ExtractedText(document_name=stem, pages=pages)

    @staticmethod
    def extract_images(pdf_ref: PdfFileReference) -> list[ExtractedImage]:
        """Extract every embedded image from the PDF."""
        doc = fitz.open(pdf_ref.file_location)
        images: list[ExtractedImage] = []
        stem = _stem(pdf_ref.file_name)

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
                            document_name=stem,
                            page_number=page_num + 1,
                            image_index=img_index,
                            image_bytes=base_image["image"],
                            extension=base_image["ext"],
                        )
                    )
        finally:
            doc.close()

        return images


def _stem(file_name: str) -> str:
    """Return the file name without its extension."""
    if "." in file_name:
        return file_name.rsplit(".", 1)[0]
    return file_name
