"""
Infrastructure adapter – shared filesystem storage.

Replaces the S3 gateway.  All reads and writes go to the shared
volume mounted at /files.  Directory structure:

    /files/
    ├── <source documents dropped here>
    └── output/
        └── <document_name>/
            ├── extracted_text.json
            └── extracted_images/
                ├── page_1_img_0.png
                └── page_2_img_1.jpeg
"""

from __future__ import annotations

import json
import os
import shutil
from pathlib import Path

import structlog

from infrastructure.config import settings

logger = structlog.get_logger()


class FileStorageGateway:
    """Maps domain operations to filesystem reads and writes."""

    def __init__(self) -> None:
        self._base = Path(settings.shared_files_path)

    # ── Directory management ──────────────────────────────────

    def ensure_output_dirs(self, document_name: str) -> None:
        """Create the output directory tree for a document."""
        output = Path(settings.output_dir(document_name))
        images = Path(settings.images_output_dir(document_name))
        output.mkdir(parents=True, exist_ok=True)
        images.mkdir(parents=True, exist_ok=True)
        logger.info("storage.dirs_created",
                     output=str(output), images=str(images))

    # ── Text storage ──────────────────────────────────────────

    def save_extracted_text(
        self,
        document_name: str,
        pages: dict[int, str] | None = None,
        full_text: str = "",
        source_mime_type: str = "",
    ) -> str:
        """Write extracted text as JSON. Returns the file path."""
        self.ensure_output_dirs(document_name)
        path = settings.text_output_path(document_name)

        payload = json.dumps(
            {
                "document_name": document_name,
                "source_mime_type": source_mime_type,
                "pages": pages or {},
                "full_text": full_text,
            },
            ensure_ascii=False,
            indent=2,
        )

        Path(path).write_text(payload, encoding="utf-8")
        logger.info("storage.text_saved", path=path, size=len(payload))
        return path

    # ── Image storage ─────────────────────────────────────────

    def save_image(
        self, document_name: str, image_bytes: bytes,
        page_number: int, image_index: int, extension: str,
    ) -> str:
        """Write an extracted image. Returns the file path."""
        self.ensure_output_dirs(document_name)
        filename = f"page_{page_number}_img_{image_index}.{extension}"
        path = os.path.join(
            settings.images_output_dir(document_name), filename
        )

        Path(path).write_bytes(image_bytes)
        logger.info("storage.image_saved", path=path, size=len(image_bytes))
        return path

    def save_source_image(
        self, document_name: str, source_path: str, extension: str,
    ) -> str:
        """Copy a source image file into the output directory. Returns path."""
        self.ensure_output_dirs(document_name)
        dest = os.path.join(
            settings.images_output_dir(document_name),
            f"source_image.{extension}",
        )
        shutil.copy2(source_path, dest)
        logger.info("storage.source_image_copied", src=source_path, dest=dest)
        return dest

    # ── Reading ───────────────────────────────────────────────

    def read_image_bytes(self, image_path: str) -> bytes:
        """Read an image file from the shared filesystem."""
        logger.info("storage.reading_image", path=image_path)
        return Path(image_path).read_bytes()
