"""Route a file to the correct handler based on its detected MIME type."""

from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

from processor.detect import detect_mime
from processor.handlers.image import handle_image
from processor.handlers.pdf import handle_pdf
from processor.handlers.text import handle_text

# Map MIME prefixes / exact types → handler generators
_IMAGE_MIMES = {
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/bmp",
    "image/webp",
    "image/gif",
}

_TEXT_MIMES = {
    "text/plain",
    "text/csv",
    "text/html",
    "text/xml",
    "application/json",
    "application/xml",
}


def dispatch_file(
    filepath: Path,
    output_dir: Path,
    *,
    verbose: bool = False,
) -> Generator[str, None, None]:
    """Detect the MIME type and yield status messages from the handler.

    Using a generator (yield) keeps the whole pipeline lazy —
    large PDFs never need to buffer everything in RAM at once.
    """
    mime = detect_mime(filepath)
    yield f"🔍 Detected MIME type: {mime}"

    if mime == "application/pdf":
        yield from handle_pdf(filepath, output_dir, verbose=verbose)

    elif mime in _IMAGE_MIMES:
        yield from handle_image(filepath, output_dir, verbose=verbose)

    elif mime in _TEXT_MIMES:
        yield from handle_text(filepath, verbose=verbose)

    else:
        yield f"⚠️  No handler registered for MIME type '{mime}'. Skipping."
