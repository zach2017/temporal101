"""PDF handler — extract text and images using generators to minimise memory.

Text extraction  → pdfplumber  (page-by-page yield)
Image extraction → PyMuPDF/fitz (page-by-page yield)
OCR on images    → pytesseract + Pillow
"""

from __future__ import annotations

import io
from collections.abc import Generator
from pathlib import Path

import fitz  # PyMuPDF
import pdfplumber
import pytesseract
from PIL import Image


# ---------------------------------------------------------------------------
# Public entry point (generator)
# ---------------------------------------------------------------------------

def handle_pdf(
    filepath: Path,
    output_dir: Path,
    *,
    verbose: bool = False,
) -> Generator[str, None, None]:
    """Orchestrate PDF processing, yielding status messages."""
    yield "📄 Processing PDF …"

    # --- text extraction ---
    text_out = output_dir / "extracted_text.txt"
    page_count = 0
    with text_out.open("w", encoding="utf-8") as fh:
        for page_num, page_text in extract_text(filepath):
            fh.write(f"\n--- Page {page_num} ---\n{page_text}\n")
            page_count = page_num
            if verbose:
                yield f"   ✏️  Extracted text from page {page_num}"

    yield f"   ✅ Text saved → {text_out}  ({page_count} pages)"

    # --- image extraction + OCR ---
    img_dir = output_dir / "images"
    img_dir.mkdir(exist_ok=True)
    ocr_out = output_dir / "ocr_text.txt"

    img_count = 0
    with ocr_out.open("w", encoding="utf-8") as fh:
        for img_index, img_path, ocr_text in extract_and_ocr_images(filepath, img_dir):
            img_count += 1
            fh.write(f"\n--- Image {img_index} ({img_path.name}) ---\n{ocr_text}\n")
            if verbose:
                yield f"   🖼️  Image {img_index} → {img_path.name}"

    if img_count:
        yield f"   ✅ {img_count} images extracted → {img_dir}"
        yield f"   ✅ OCR text saved → {ocr_out}"
    else:
        yield "   ℹ️  No embedded images found."
        ocr_out.unlink(missing_ok=True)


# ---------------------------------------------------------------------------
# Generator: page-by-page text extraction via pdfplumber
# ---------------------------------------------------------------------------

def extract_text(filepath: Path) -> Generator[tuple[int, str], None, None]:
    """Yield (page_number, text) tuples — one page at a time.

    pdfplumber is used because it handles complex layouts, tables, and
    whitespace better than many alternatives.  Opening the PDF in a
    context manager ensures resources are freed promptly.
    """
    with pdfplumber.open(filepath) as pdf:
        for idx, page in enumerate(pdf.pages, start=1):
            text = page.extract_text() or ""
            yield idx, text
            # Page object is discarded on next iteration → low memory


# ---------------------------------------------------------------------------
# Generator: image extraction (PyMuPDF) + OCR (pytesseract)
# ---------------------------------------------------------------------------

def extract_and_ocr_images(
    filepath: Path,
    img_dir: Path,
) -> Generator[tuple[int, Path, str], None, None]:
    """Yield (image_index, saved_path, ocr_text) for every image in the PDF.

    PyMuPDF (fitz) gives us raw image bytes per page, which we convert
    with Pillow and then OCR with Tesseract.  Each image is processed
    and yielded individually so we never hold more than one in memory.
    """
    doc = fitz.open(filepath)
    img_index = 0

    try:
        for page_num in range(len(doc)):
            page = doc.load_page(page_num)
            image_list = page.get_images(full=True)

            for img_info in image_list:
                xref = img_info[0]

                # Extract raw image bytes
                base_image = doc.extract_image(xref)
                if base_image is None:
                    continue

                image_bytes = base_image["image"]
                ext = base_image.get("ext", "png")
                img_index += 1

                # Save to disk
                img_path = img_dir / f"page{page_num + 1}_img{img_index}.{ext}"
                img_path.write_bytes(image_bytes)

                # OCR the image
                ocr_text = _ocr_image_bytes(image_bytes)

                yield img_index, img_path, ocr_text

                # Explicitly free large buffers
                del image_bytes, base_image
    finally:
        doc.close()


# ---------------------------------------------------------------------------
# Internal: OCR a single image from its bytes
# ---------------------------------------------------------------------------

def _ocr_image_bytes(data: bytes) -> str:
    """Run Tesseract OCR on raw image bytes and return the recognised text."""
    with Image.open(io.BytesIO(data)) as img:
        # Convert to RGB if needed (Tesseract doesn't handle all modes)
        if img.mode not in ("L", "RGB"):
            img = img.convert("RGB")
        text: str = pytesseract.image_to_string(img)
    return text.strip()
