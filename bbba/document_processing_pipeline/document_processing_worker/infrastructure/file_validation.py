"""
Shared file validation utility.

Every activity that touches the filesystem calls ``validate_file_exists``
before doing any work.  This gives clear, actionable error messages with
the exact path being searched, and fails the activity immediately so
Temporal can retry or surface the error.
"""

from __future__ import annotations

import os
from pathlib import Path

import structlog

logger = structlog.get_logger()


class FileNotFoundError(Exception):
    """Raised when a required file does not exist at the expected location."""
    pass


def validate_file_exists(file_location: str, context: str = "") -> Path:
    """
    Validate that a file exists and is readable.

    Logs the exact path being checked, the working directory, and
    a directory listing of the parent folder on failure.

    Args:
        file_location: Absolute or relative path to the file.
        context: Human-readable label for log messages (e.g. activity name).

    Returns:
        Resolved ``Path`` object on success.

    Raises:
        FileNotFoundError: If the file does not exist or is not readable.
    """
    path = Path(file_location)
    resolved = path.resolve()
    cwd = Path.cwd().resolve()

    logger.info(
        "file_validation.checking",
        context=context,
        file_location=file_location,
        resolved_path=str(resolved),
        cwd=str(cwd),
        exists=path.exists(),
        is_file=path.is_file() if path.exists() else False,
    )

    if not path.exists():
        # Log parent directory contents to help diagnose mount issues
        parent = resolved.parent
        parent_contents = []
        if parent.exists():
            try:
                parent_contents = sorted(
                    [str(p.name) for p in parent.iterdir()][:50]
                )
            except PermissionError:
                parent_contents = ["<permission denied>"]

        logger.error(
            "file_validation.NOT_FOUND",
            context=context,
            file_location=file_location,
            resolved_path=str(resolved),
            cwd=str(cwd),
            parent_dir=str(parent),
            parent_exists=parent.exists(),
            parent_contents=parent_contents,
        )

        raise FileNotFoundError(
            f"[{context}] File not found: '{file_location}' "
            f"(resolved to '{resolved}'). "
            f"Working directory: '{cwd}'. "
            f"Parent directory '{parent}' "
            f"{'contains: ' + ', '.join(parent_contents[:10]) if parent_contents else 'does not exist'}."
        )

    if not path.is_file():
        logger.error(
            "file_validation.NOT_A_FILE",
            context=context,
            file_location=file_location,
            resolved_path=str(resolved),
            is_dir=path.is_dir(),
        )
        raise FileNotFoundError(
            f"[{context}] Path exists but is not a file: '{file_location}' "
            f"(resolved to '{resolved}'). Is directory: {path.is_dir()}."
        )

    if not os.access(path, os.R_OK):
        logger.error(
            "file_validation.NOT_READABLE",
            context=context,
            file_location=file_location,
            resolved_path=str(resolved),
        )
        raise FileNotFoundError(
            f"[{context}] File exists but is not readable: '{file_location}' "
            f"(resolved to '{resolved}'). Check file permissions."
        )

    logger.info(
        "file_validation.OK",
        context=context,
        resolved_path=str(resolved),
        size_bytes=path.stat().st_size,
    )

    return resolved
