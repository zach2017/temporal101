"""Text handler — summarise plain-text / CSV / JSON files."""

from __future__ import annotations

from collections.abc import Generator
from pathlib import Path


_MAX_PREVIEW = 500  # characters


def handle_text(
    filepath: Path,
    *,
    verbose: bool = False,
) -> Generator[str, None, None]:
    """Read the text file and yield a summary."""
    yield f"📝 Processing text file: {filepath.name}"

    content = filepath.read_text(encoding="utf-8", errors="replace")
    lines = content.splitlines()

    yield f"   ℹ️  {len(lines)} lines, {len(content)} characters"

    if verbose:
        preview = content[:_MAX_PREVIEW].replace("\n", "\\n")
        yield f"   📝 Preview: {preview}{'…' if len(content) > _MAX_PREVIEW else ''}"
