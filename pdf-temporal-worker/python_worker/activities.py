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

from temporalio import activity
from temporalio.exceptions import ApplicationError

from models import (
    PdfProcessingRequest,
    PdfProcessingResult,
    ExtractedImage,
)
from storage import get_handler, StorageHandler
from pdf_processor import yield_pages, page_count

log = logging.getLogger(__name__)

# How many chars of text to include inline in the result payload.
# Everything beyond this is only in the stored .txt file.
_INLINE_TEXT_LIMIT = 4_000

# Accepted MIME-style extensions for safety check
_VALID_PDF_EXTENSIONS = (".pdf",)

# Maximum file size we're willing to process (2 GB)
_MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 * 1024


# ──────────────────────────────────────────────
#  Validation helpers
# ──────────────────────────────────────────────

def _validate_request(request: PdfProcessingRequest) -> None:
    """
    Validate the incoming request before doing any real work.
    Raises ApplicationError (non-retryable) for bad input so Temporal
    does not pointlessly retry.
    """
    if not request.file_name:
        raise ApplicationError(
            "file_name is required but was empty or None.",
            type="INVALID_ARGUMENT",
            non_retryable=True,
        )

    if not request.file_name.lower().endswith(_VALID_PDF_EXTENSIONS):
        raise ApplicationError(
            f"file_name must end with .pdf, got: {request.file_name!r}",
            type="INVALID_ARGUMENT",
            non_retryable=True,
        )

    if not request.location:
        raise ApplicationError(
            "location is required but was empty or None.",
            type="INVALID_ARGUMENT",
            non_retryable=True,
        )

    if not request.storage_type:
        raise ApplicationError(
            "storage_type is required but was empty or None.",
            type="INVALID_ARGUMENT",
            non_retryable=True,
        )


def _validate_local_pdf(path: str, context: str = "") -> None:
    """
    Confirm a local PDF file exists, is readable, has size > 0,
    and is not suspiciously large.

    Parameters
    ----------
    path : str
        Path to the local file.
    context : str
        Extra context for the error message (e.g. the original file name).
    """
    label = f" (source: {context})" if context else ""

    if not path:
        raise ApplicationError(
            f"Local PDF path is empty or None{label}.",
            type="INVALID_PATH",
            non_retryable=True,
        )

    if not os.path.exists(path):
        raise ApplicationError(
            f"PDF file does not exist at path: {path}{label}. "
            "Check that the file has been uploaded to the correct storage "
            "location and that the file_name / location fields are correct.",
            type="FILE_NOT_FOUND",
            non_retryable=True,
        )

    if not os.path.isfile(path):
        raise ApplicationError(
            f"Path exists but is not a file (maybe a directory?): {path}{label}.",
            type="INVALID_PATH",
            non_retryable=True,
        )

    file_size = os.path.getsize(path)

    if file_size == 0:
        raise ApplicationError(
            f"PDF file is empty (0 bytes): {path}{label}.",
            type="EMPTY_FILE",
            non_retryable=True,
        )

    if file_size > _MAX_FILE_SIZE_BYTES:
        size_mb = file_size / (1024 * 1024)
        limit_mb = _MAX_FILE_SIZE_BYTES / (1024 * 1024)
        raise ApplicationError(
            f"PDF file is too large ({size_mb:.1f} MB, limit is {limit_mb:.0f} MB): "
            f"{path}{label}.",
            type="FILE_TOO_LARGE",
            non_retryable=True,
        )

    if not os.access(path, os.R_OK):
        raise ApplicationError(
            f"PDF file exists but is not readable (permission denied): {path}{label}.",
            type="PERMISSION_DENIED",
            non_retryable=True,
        )

    # Quick magic-byte check: PDF files start with "%PDF"
    try:
        with open(path, "rb") as f:
            header = f.read(4)
        if header != b"%PDF":
            raise ApplicationError(
                f"File does not appear to be a valid PDF (bad header): {path}{label}.",
                type="INVALID_FILE_FORMAT",
                non_retryable=True,
            )
    except OSError as exc:
        raise ApplicationError(
            f"Could not read PDF header: {path}{label} — {exc}",
            type="IO_ERROR",
            non_retryable=False,  # might be transient (NFS hiccup, etc.)
        )


# ──────────────────────────────────────────────
#  Activities
# ──────────────────────────────────────────────

@activity.defn(name="fetch_pdf")
async def fetch_pdf_activity(request: PdfProcessingRequest) -> str:
    """Pull PDF from remote storage to a local temp path."""
    log.info(
        "fetch_pdf_start",
        extra={
            "file_name": request.file_name,
            "location": request.location,
            "storage_type": request.storage_type,
        },
    )

    # ── validate request fields ──
    _validate_request(request)

    # ── fetch via storage handler ──
    handler = get_handler(request.storage_type)

    # Build the source path the same way the handler would, so we can
    # detect the "already local" case *before* calling shutil.copy2.
    src = os.path.join(request.location, request.file_name)
    src_resolved = os.path.realpath(src)

    if os.path.isfile(src_resolved):
        # File is already on local disk — no copy needed.
        # Create a distinct working copy so the processing activity can
        # safely delete it without destroying the original.
        try:
            tmp_fd, tmp_path = tempfile.mkstemp(
                suffix=".pdf", prefix="fetch_"
            )
            os.close(tmp_fd)
            shutil.copy2(src_resolved, tmp_path)
            local = tmp_path
            log.info(
                "fetch_pdf_local_copy",
                extra={
                    "source": src_resolved,
                    "working_copy": tmp_path,
                },
            )
        except Exception as exc:
            _rm(tmp_path)
            raise ApplicationError(
                f"Failed to create working copy of local PDF: {exc}",
                type="IO_ERROR",
                non_retryable=False,
            )
    else:
        # File is remote — delegate to the storage handler.
        try:
            local = handler.fetch_pdf(request.file_name, request.location)
        except FileNotFoundError:
            raise ApplicationError(
                f"PDF not found in {request.storage_type} storage: "
                f"file_name={request.file_name!r}, location={request.location!r}. "
                "Please verify the file has been uploaded and the path is correct.",
                type="FILE_NOT_FOUND",
                non_retryable=True,
            )
        except PermissionError:
            raise ApplicationError(
                f"Permission denied when fetching PDF from {request.storage_type} storage: "
                f"file_name={request.file_name!r}, location={request.location!r}. "
                "Check that the worker has read access to this storage location.",
                type="PERMISSION_DENIED",
                non_retryable=True,
            )
        except Exception as exc:
            log.exception("fetch_pdf_unexpected_error")
            raise ApplicationError(
                f"Unexpected error fetching PDF: {exc}",
                type="FETCH_ERROR",
                non_retryable=False,
            )

    # ── validate the downloaded file ──
    _validate_local_pdf(local, context=request.file_name)

    log.info("fetch_pdf_complete", extra={"local_path": local})
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

    log.info(
        "process_pdf_start",
        extra={
            "file_name": req.file_name,
            "local_pdf_path": local_pdf_path,
            "storage_type": req.storage_type,
        },
    )

    # ── validate inputs ──
    _validate_request(req)
    _validate_local_pdf(local_pdf_path, context=req.file_name)

    handler: StorageHandler = get_handler(req.storage_type)

    # ── get page count safely ──
    try:
        total_pages = page_count(local_pdf_path)
    except Exception as exc:
        _rm(local_pdf_path)
        raise ApplicationError(
            f"Failed to read PDF page count: {exc}. "
            f"The file may be corrupted or password-protected: {req.file_name!r}.",
            type="PDF_READ_ERROR",
            non_retryable=True,
        )

    if total_pages == 0:
        log.warning("process_pdf_empty", extra={"file_name": req.file_name})
        _rm(local_pdf_path)
        return PdfProcessingResult(
            file_name=req.file_name,
            storage_type=req.storage_type,
            text_storage_path="",
            page_count=0,
            image_count=0,
            text_content="",
            images=[],
            success=True,
        )

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
                    try:
                        dest = handler.store_image(
                            img.local_path,
                            img.image_name,
                            req.file_name,
                            req.location,
                        )
                        stored_images.append(
                            ExtractedImage(
                                image_name=img.image_name,
                                page_number=page.page_number,
                                storage_path=dest,
                            )
                        )
                    except Exception as exc:
                        log.warning(
                            "store_image_failed",
                            extra={
                                "image": img.image_name,
                                "page": page.page_number,
                                "error": str(exc),
                            },
                        )
                        # Continue processing — one failed image shouldn't
                        # kill the entire PDF extraction.
                    finally:
                        _rm(img.local_path)

                activity.heartbeat(
                    f"page:{page.page_number}/{total_pages} "
                    f"imgs:{len(stored_images)}"
                )

                log.debug(
                    "process_pdf_page_done",
                    extra={
                        "page": page.page_number,
                        "total_pages": total_pages,
                        "images_on_page": len(page.images),
                        "file_name": req.file_name,
                    },
                )

        # ── upload the completed text file ──
        try:
            text_storage_path = handler.store_text_file(
                txt_path, req.file_name, req.location,
            )
        except Exception as exc:
            log.exception("store_text_file_failed")
            raise ApplicationError(
                f"Failed to upload extracted text to {req.storage_type} storage: {exc}",
                type="STORAGE_ERROR",
                non_retryable=False,  # storage might recover on retry
            )
        activity.heartbeat("text_stored")

    finally:
        _rm(txt_path)
        _rm(local_pdf_path)

    inline_text = "".join(text_head)
    if len(inline_text) > _INLINE_TEXT_LIMIT:
        inline_text = inline_text[:_INLINE_TEXT_LIMIT] + "\n…[truncated]"

    log.info(
        "process_pdf_complete",
        extra={
            "file_name": req.file_name,
            "pages": total_pages,
            "images": len(stored_images),
            "text_storage_path": text_storage_path,
        },
    )

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


# ──────────────────────────────────────────────
#  Cleanup helper
# ──────────────────────────────────────────────

def _rm(path: str | None) -> None:
    """
    Silently remove a file or directory.
    Accepts None safely so callers don't need to guard.
    """
    if not path:
        return
    try:
        if os.path.isfile(path):
            os.remove(path)
        elif os.path.isdir(path):
            shutil.rmtree(path)
    except OSError as exc:
        log.debug("cleanup_failed", extra={"path": path, "error": str(exc)})