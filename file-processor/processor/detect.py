"""Detect MIME type — cross-platform.

Strategy (in order):
 1. python-magic  (libmagic bindings) — most accurate, reads file bytes
 2. python-magic-bin (bundles libmagic DLL on Windows) — same API
 3. stdlib mimetypes — extension-based fallback, works everywhere
"""

from __future__ import annotations

import mimetypes
from pathlib import Path

# Try to import libmagic; works on Linux/macOS with libmagic installed,
# or on any OS if python-magic-bin is installed.
try:
    import magic  # python-magic or python-magic-bin

    def _detect_via_magic(filepath: Path) -> str:
        m = magic.Magic(mime=True)
        return m.from_file(str(filepath))

    _HAS_MAGIC = True
except (ImportError, OSError):
    _HAS_MAGIC = False

    def _detect_via_magic(filepath: Path) -> str:  # type: ignore[misc]
        raise RuntimeError("libmagic not available")


def _detect_via_stdlib(filepath: Path) -> str:
    """Extension-based detection using Python's built-in mimetypes."""
    mime, _ = mimetypes.guess_type(str(filepath))
    if mime:
        return mime

    # Last resort: peek at the first few bytes for common signatures
    sig = filepath.read_bytes()[:8]
    if sig[:5] == b"%PDF-":
        return "application/pdf"
    if sig[:4] == b"\x89PNG":
        return "image/png"
    if sig[:2] == b"\xff\xd8":
        return "image/jpeg"
    if sig[:4] == b"RIFF" and len(sig) >= 8:
        return "image/webp"

    return "application/octet-stream"


def detect_mime(filepath: Path) -> str:
    """Return the MIME type string for *filepath*.

    Uses libmagic when available (byte-level detection), otherwise
    falls back to extension + signature sniffing so the CLI works
    out of the box on Windows without extra DLLs.

    Examples:
        application/pdf, image/png, text/plain, image/jpeg
    """
    if _HAS_MAGIC:
        try:
            return _detect_via_magic(filepath)
        except Exception:
            pass  # fall through to stdlib

    return _detect_via_stdlib(filepath)
