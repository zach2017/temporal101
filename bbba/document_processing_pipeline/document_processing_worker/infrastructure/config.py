"""
Centralised, ENV-driven configuration.

Every external reference (Temporal, S3, timeouts) lives here so the rest
of the codebase never hard-codes connection strings or bucket names.
"""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Immutable value object – loaded once at process start."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ── Temporal ──────────────────────────────────────────────
    temporal_host: str = "localhost:7233"
    temporal_namespace: str = "default"
    temporal_task_queue: str = "document-intake-queue"
    temporal_pdf_task_queue: str = "pdf-extraction-queue"
    temporal_ocr_task_queue: str = "image-ocr-queue"
    temporal_conversion_task_queue: str = "document-conversion-queue"

    # ── S3 ────────────────────────────────────────────────────
    s3_endpoint_url: str | None = None
    s3_bucket_name: str = "document-processing"
    s3_access_key_id: str = ""
    s3_secret_access_key: str = ""
    s3_region: str = "us-east-1"

    # ── Worker tuning ─────────────────────────────────────────
    max_concurrent_activities: int = 5
    activity_heartbeat_timeout_seconds: int = 120
    activity_start_to_close_timeout_seconds: int = 600

    # ── Derived helpers ───────────────────────────────────────
    def text_bucket_key(self, document_name: str) -> str:
        return f"{document_name}/extracted_text.json"

    def images_bucket_prefix(self, document_name: str) -> str:
        return f"{document_name}/extracted_images/"


settings = Settings()
