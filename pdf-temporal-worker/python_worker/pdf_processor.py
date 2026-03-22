"""
Generator-based PDF processor.

Key design: process ONE page at a time via ``yield_pages()``.
The caller writes text and images incrementally — at no point is the
full document buffered in memory.

Memory profile for a 500-page / 2 GB PDF stays ≈ single-page size.
"""

import os
import logging
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Generator

import fitz  # PyMuPDF

log = logging.getLogger(__name__)

# Max image bytes to extract per image (skip huge embedded bitmaps)
_MAX_IMAGE_BYTES = 20 * 1024 * 1024  # 20 MB


@dataclass
class PageImage:
    """One extracted image written to disk — only the *path* is kept."""
    local_path: str
    image_name: str


@dataclass
class PageResult:
    """What the generator yields for every page."""
    page_number: int
    text: str
    images: list[PageImage] = field(default_factory=list)


def yield_pages(
    pdf_path: str,
    extract_images: bool = True,
    image_dir: str | None = None,
) -> Generator[PageResult, None, None]:
    """
    Open *pdf_path* and **yield one PageResult per page**.

    Images are flushed to *image_dir* immediately so pixel buffers
    are freed before advancing to the next page.
    """
    if image_dir is None:
        image_dir = os.path.join(
            tempfile.mkdtemp(prefix="pdfimg_"),
            Path(pdf_path).stem + "_images",
        )

    doc = fitz.open(pdf_path)
    try:
        for page_idx in range(len(doc)):
            page = doc.load_page(page_idx)

            # ── text (get_text returns a new str; page ref dropped at end) ──
            text = page.get_text("text") or ""

            # ── images ──
            images: list[PageImage] = []
            if extract_images:
                images = _extract_page_images(
                    doc, page, page_idx, image_dir,
                )

            yield PageResult(
                page_number=page_idx + 1,
                text=text,
                images=images,
            )

            # Explicitly drop references so GC can reclaim page memory
            del page, text, images
    finally:
        doc.close()


def page_count(pdf_path: str) -> int:
    """Quick page-count without loading content."""
    doc = fitz.open(pdf_path)
    n = len(doc)
    doc.close()
    return n


# ── private helpers ──────────────────────────────────────────────────────────

def _extract_page_images(
    doc: fitz.Document,
    page: fitz.Page,
    page_idx: int,
    image_dir: str,
) -> list[PageImage]:
    """Extract images for a single page; write to disk immediately."""
    results: list[PageImage] = []
    for img_idx, img_info in enumerate(page.get_images(full=True)):
        xref = img_info[0]
        try:
            base = doc.extract_image(xref)
        except Exception:
            log.debug("Skip unreadable image xref=%s page=%d", xref, page_idx + 1)
            continue

        raw: bytes = base.get("image", b"")
        if not raw or len(raw) > _MAX_IMAGE_BYTES:
            log.debug("Skip oversized/empty image xref=%s (%d bytes)",
                      xref, len(raw))
            continue

        ext = base.get("ext", "png")
        name = f"page{page_idx + 1}_img{img_idx + 1}.{ext}"
        os.makedirs(image_dir, exist_ok=True)
        path = os.path.join(image_dir, name)

        with open(path, "wb") as fh:
            fh.write(raw)

        results.append(PageImage(local_path=path, image_name=name))

        # free pixel buffer immediately
        del raw

    return results
