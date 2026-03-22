"""Detect MIME type using libmagic (via python-magic)."""

from pathlib import Path

import magic  # python-magic


def detect_mime(filepath: Path) -> str:
    """Return the MIME type string for *filepath* using libmagic.

    Examples:
        application/pdf, image/png, text/plain, image/jpeg
    """
    mime = magic.Magic(mime=True)
    return mime.from_file(str(filepath))
