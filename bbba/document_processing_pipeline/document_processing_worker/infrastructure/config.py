"""
Centralised, ENV-driven configuration.

All output is written to the shared filesystem at SHARED_FILES_PATH
(default: /files).  No S3 or object storage required.
"""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ── Temporal ──────────────────────────────────────────────
    temporal_host: str = "localhost:7233"
    temporal_namespace: str = "default"
    temporal_task_queue: str = "document-intake-queue"
    temporal_tika_task_queue: str = "tika-detection-queue"
    temporal_pdf_task_queue: str = "pdf-extraction-queue"
    temporal_ocr_task_queue: str = "image-ocr-queue"
    temporal_conversion_task_queue: str = "document-conversion-queue"

    # ── Shared Filesystem ─────────────────────────────────────
    # All workers mount this volume.  Source files are read from
    # here and all output (extracted text, images) is written here.
    shared_files_path: str = "/files"

    # ── Worker tuning ─────────────────────────────────────────
    max_concurrent_activities: int = 5
    activity_heartbeat_timeout_seconds: int = 120
    activity_start_to_close_timeout_seconds: int = 600
    activity_max_retries: int = 2

    # ── Derived paths ─────────────────────────────────────────
    def output_dir(self, document_name: str) -> str:
        """Root output directory for a processed document."""
        return f"{self.shared_files_path}/output/{document_name}"

    def text_output_path(self, document_name: str) -> str:
        """Path for the extracted text JSON file."""
        return f"{self.output_dir(document_name)}/extracted_text.json"

    def images_output_dir(self, document_name: str) -> str:
        """Directory for extracted images."""
        return f"{self.output_dir(document_name)}/extracted_images"


settings = Settings()
