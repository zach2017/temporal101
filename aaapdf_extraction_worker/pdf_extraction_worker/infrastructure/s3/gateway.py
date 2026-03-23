"""
Infrastructure adapter – S3 object storage gateway.

All boto3 interaction is isolated here so the domain layer stays pure.
"""

from __future__ import annotations

import json

import boto3
from botocore.config import Config as BotoConfig

from infrastructure.config import settings


def _s3_client():
    """Build a boto3 S3 client from ENV-driven settings."""
    kwargs: dict = {
        "service_name": "s3",
        "region_name": settings.s3_region,
        "aws_access_key_id": settings.s3_access_key_id,
        "aws_secret_access_key": settings.s3_secret_access_key,
        "config": BotoConfig(signature_version="s3v4"),
    }
    if settings.s3_endpoint_url:
        kwargs["endpoint_url"] = settings.s3_endpoint_url
    return boto3.client(**kwargs)


class S3Gateway:
    """Thin wrapper that maps domain concepts to S3 operations."""

    def __init__(self) -> None:
        self._client = _s3_client()
        self._bucket = settings.s3_bucket_name

    # ── public API ────────────────────────────────────────────

    def ensure_bucket_exists(self) -> None:
        """Create the bucket if it does not already exist."""
        try:
            self._client.head_bucket(Bucket=self._bucket)
        except self._client.exceptions.ClientError:
            self._client.create_bucket(Bucket=self._bucket)

    def upload_extracted_text(
        self, document_name: str, pages: dict[int, str]
    ) -> str:
        """Persist extracted text as a JSON object. Returns the S3 key."""
        key = settings.text_bucket_key(document_name)
        payload = json.dumps(
            {"document_name": document_name, "pages": pages},
            ensure_ascii=False,
            indent=2,
        )
        self._client.put_object(
            Bucket=self._bucket,
            Key=key,
            Body=payload.encode("utf-8"),
            ContentType="application/json",
        )
        return key

    def upload_image(
        self, s3_key: str, image_bytes: bytes, extension: str
    ) -> str:
        """Upload a single extracted image. Returns the S3 key."""
        content_type = _extension_to_mime(extension)
        self._client.put_object(
            Bucket=self._bucket,
            Key=s3_key,
            Body=image_bytes,
            ContentType=content_type,
        )
        return s3_key

    def download_image_bytes(self, s3_key: str) -> bytes:
        """Download an image from S3 – used by the OCR worker."""
        response = self._client.get_object(Bucket=self._bucket, Key=s3_key)
        return response["Body"].read()


def _extension_to_mime(ext: str) -> str:
    mapping = {
        "png": "image/png",
        "jpeg": "image/jpeg",
        "jpg": "image/jpeg",
        "tiff": "image/tiff",
        "bmp": "image/bmp",
        "gif": "image/gif",
    }
    return mapping.get(ext.lower(), "application/octet-stream")
