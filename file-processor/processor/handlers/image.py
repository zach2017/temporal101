"""Image handler — OCR a standalone image file with Tesseract."""

from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

import pytesseract
from PIL import Image


def handle_image(
    filepath: Path,
    output_dir: Path,
    *,
    verbose: bool = False,
) -> Generator[str, None, None]:
    """OCR a single image file, yielding status messages."""
    yield f"🖼️  Processing image: {filepath.name}"

    ocr_text = ocr_image_file(filepath)
    out_path = output_dir / f"{filepath.stem}_ocr.txt"
    out_path.write_text(ocr_text, encoding="utf-8")

    char_count = len(ocr_text)
    yield f"   ✅ OCR complete — {char_count} characters extracted → {out_path}"

    if verbose and ocr_text:
        preview = ocr_text[:200].replace("\n", " ")
        yield f"   📝 Preview: {preview}{'…' if char_count > 200 else ''}"


def ocr_image_file(filepath: Path) -> str:
    """Open an image from disk and return Tesseract OCR text.

    The image is opened inside a context manager so memory is
    released as soon as OCR finishes.
    """
    with Image.open(filepath) as img:
        if img.mode not in ("L", "RGB"):
            img = img.convert("RGB")
        text: str = pytesseract.image_to_string(img)
    return text.strip()
