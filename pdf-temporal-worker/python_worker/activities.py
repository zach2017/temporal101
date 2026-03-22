"""
Temporal Activity definitions.

Memory strategy
───────────────
* ``process_pdf_activity`` iterates the page-generator one page at a time.
* Extracted text is **appended to a temp file on disk** — never accumulated
  in a Python string.
* Each page's images are flushed to storage immediately, then the local
  copies are deleted before advancing to the next page.
* Peak RAM ≈ one page of text + one page's worth of images.
"""

import os
import shutil
import tempfile
import logging
from pathlib import Path

from temporalio import activity

from models import (
    PdfProcessingRequest, PdfProcessingResult, ExtractedImage,
)
from storage import get_handler, StorageHandler
from pdf_processor import yield_pages, page_count

log = logging.getLogger(__name__)

# How many chars of text to include inline in the result payload.
# Everything beyond this is only in the stored .txt file.
_INLINE_TEXT_LIMIT = 4_000


@activity.defn(name="fetch_pdf")
async def fetch_pdf_activity(request: PdfProcessingRequest) -> str:
    """Pull PDF from remote storage to a local temp path."""
    handler = get_handler(request.storage_type)
    local = handler.fetch_pdf(request.file_name, request.location)
    activity.heartbeat(f"fetched:{local}")
    return local


@activity.defn(name="process_and_store_pdf")
async def process_and_store_activity(
    local_pdf_path: str,
    request_dict: dict,
) -> PdfProcessingResult:
    """
    Single-pass streaming activity:
      for each page ──► append text to temp .txt file
                     ──► store images to remote storage immediately
    Then upload the finished .txt file once.
    """
    req = PdfProcessingRequest(**request_dict)
    handler: StorageHandler = get_handler(req.storage_type)

    total_pages = page_count(local_pdf_path)
    stored_images: list[ExtractedImage] = []
    text_head: list[str] = []       # first N chars kept for inline result
    head_chars = 0

    # ── temp file for full extracted text (never held in RAM) ──
    txt_fd, txt_path = tempfile.mkstemp(suffix=".txt", prefix="pdftext_")
    try:
        with os.fdopen(txt_fd, "w", encoding="utf-8", buffering=8192) as txt_fh:

            for page in yield_pages(
                local_pdf_path,
                extract_images=req.extract_images,
            ):
                # ── append text ──
                header = f"--- Page {page.page_number} ---\n"
                txt_fh.write(header)
                txt_fh.write(page.text)
                txt_fh.write("\n\n")

                # keep a small prefix for the inline result field
                if head_chars < _INLINE_TEXT_LIMIT:
                    snippet = header + page.text + "\n\n"
                    text_head.append(snippet)
                    head_chars += len(snippet)

                # ── store images immediately, then delete local copy ──
                for img in page.images:
                    dest = handler.store_image(
                        img.local_path, img.image_name,
                        req.file_name, req.location,
                    )
                    stored_images.append(ExtractedImage(
                        image_name=img.image_name,
                        page_number=page.page_number,
                        storage_path=dest,
                    ))
                    _rm(img.local_path)

                activity.heartbeat(
                    f"page:{page.page_number}/{total_pages} "
                    f"imgs:{len(stored_images)}"
                )

        # ── upload the completed text file ──
        text_storage_path = handler.store_text_file(
            txt_path, req.file_name, req.location,
        )
        activity.heartbeat("text_stored")

    finally:
        _rm(txt_path)
        _rm(local_pdf_path)

    inline_text = "".join(text_head)
    if len(inline_text) > _INLINE_TEXT_LIMIT:
        inline_text = inline_text[:_INLINE_TEXT_LIMIT] + "\n…[truncated]"

    return PdfProcessingResult(
        file_name=req.file_name,
        storage_type=req.storage_type,
        text_storage_path=text_storage_path,
        page_count=total_pages,
        image_count=len(stored_images),
        text_content=inline_text,
        images=stored_images,
        success=True,
    )


def _rm(path: str) -> None:
    try:
        if os.path.isfile(path):
            os.remove(path)
        elif os.path.isdir(path):
            shutil.rmtree(path)
    except OSError:
        pass
