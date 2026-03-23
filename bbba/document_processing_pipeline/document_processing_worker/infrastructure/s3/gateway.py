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

    def ensure_bucket_exists(self) -> None:
        try:
            self._client.head_bucket(Bucket=self._bucket)
        except self._client.exceptions.ClientError:
            self._client.create_bucket(Bucket=self._bucket)

    def upload_extracted_text(
        self, document_name: str, pages: dict[int, str] | None = None,
        full_text: str = "", source_mime_type: str = "",
    ) -> str:
        key = settings.text_bucket_key(document_name)
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
        self._client.put_object(
            Bucket=self._bucket,
            Key=key,
            Body=payload.encode("utf-8"),
            ContentType="application/json",
        )
        return key

    def upload_image(self, s3_key: str, image_bytes: bytes, extension: str) -> str:
        content_type = _extension_to_mime(extension)
        self._client.put_object(
            Bucket=self._bucket,
            Key=s3_key,
            Body=image_bytes,
            ContentType=content_type,
        )
        return s3_key

    def upload_file(self, s3_key: str, file_path: str, content_type: str = "application/octet-stream") -> str:
        """Upload an arbitrary file from disk to S3."""
        with open(file_path, "rb") as f:
            self._client.put_object(
                Bucket=self._bucket,
                Key=s3_key,
                Body=f.read(),
                ContentType=content_type,
            )
        return s3_key

    def download_bytes(self, s3_key: str) -> bytes:
        response = self._client.get_object(Bucket=self._bucket, Key=s3_key)
        return response["Body"].read()


def _extension_to_mime(ext: str) -> str:
    mapping = {
        "png": "image/png", "jpeg": "image/jpeg", "jpg": "image/jpeg",
        "tiff": "image/tiff", "bmp": "image/bmp", "gif": "image/gif",
        "webp": "image/webp", "svg": "image/svg+xml",
    }
    return mapping.get(ext.lower(), "application/octet-stream")
