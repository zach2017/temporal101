"""
Storage handlers — fetch, store_text, store_image.
All writes use chunked / streaming I/O to avoid memory spikes.
"""

import os
import shutil
import tempfile
import logging
from abc import ABC, abstractmethod
from pathlib import Path

import boto3
from botocore.config import Config as BotoConfig
import requests

from models import StorageType

log = logging.getLogger(__name__)

_CHUNK = 8192  # 8 KB I/O chunk


# ═══════════════════════════════════════════════════════════════════════════════
# Interface
# ═══════════════════════════════════════════════════════════════════════════════

class StorageHandler(ABC):

    @abstractmethod
    def fetch_pdf(self, file_name: str, location: str) -> str:
        """Download/copy PDF to a local temp path; return that path."""

    @abstractmethod
    def store_text_file(self, local_txt_path: str, file_name: str,
                        location: str) -> str:
        """Upload/copy a *text file on disk* (not an in-memory string).
        Returns the remote storage path."""

    @abstractmethod
    def store_image(self, local_path: str, image_name: str,
                    file_name: str, location: str) -> str:
        """Upload/copy one image file. Returns remote storage path."""


# ═══════════════════════════════════════════════════════════════════════════════
# S3
# ═══════════════════════════════════════════════════════════════════════════════

class S3StorageHandler(StorageHandler):

    def __init__(self):
        self._s3 = boto3.client(
            "s3",
            config=BotoConfig(
                max_pool_connections=4,
                retries={"max_attempts": 3, "mode": "adaptive"},
            ),
        )

    @staticmethod
    def _split(location: str):
        path = location.replace("s3://", "")
        parts = path.split("/", 1)
        return parts[0], (parts[1] if len(parts) > 1 else "")

    def fetch_pdf(self, file_name: str, location: str) -> str:
        bucket, prefix = self._split(location)
        key = f"{prefix}/{file_name}".lstrip("/")
        tmp = os.path.join(tempfile.mkdtemp(prefix="s3dl_"), file_name)
        log.info("S3 GET s3://%s/%s → %s", bucket, key, tmp)
        self._s3.download_file(bucket, key, tmp)
        return tmp

    def store_text_file(self, local_txt_path: str, file_name: str,
                        location: str) -> str:
        bucket, prefix = self._split(location)
        txt_key = f"{prefix}/{Path(file_name).stem}.txt".lstrip("/")
        log.info("S3 PUT text → s3://%s/%s", bucket, txt_key)
        # multipart upload handles large text files automatically
        self._s3.upload_file(local_txt_path, bucket, txt_key,
                             ExtraArgs={"ContentType": "text/plain; charset=utf-8"})
        return f"s3://{bucket}/{txt_key}"

    def store_image(self, local_path: str, image_name: str,
                    file_name: str, location: str) -> str:
        bucket, prefix = self._split(location)
        img_key = f"{prefix}/{Path(file_name).stem}_images/{image_name}".lstrip("/")
        self._s3.upload_file(local_path, bucket, img_key)
        return f"s3://{bucket}/{img_key}"


# ═══════════════════════════════════════════════════════════════════════════════
# NFS (local / network mount)
# ═══════════════════════════════════════════════════════════════════════════════

class NFSStorageHandler(StorageHandler):

    def fetch_pdf(self, file_name: str, location: str) -> str:
        src = os.path.join(location, file_name)
        if not os.path.isfile(src):
            raise FileNotFoundError(f"PDF not found: {src}")
        tmp = os.path.join(tempfile.mkdtemp(prefix="nfs_"), file_name)
        shutil.copy2(src, tmp)
        log.info("NFS cp %s → %s", src, tmp)
        return tmp

    def store_text_file(self, local_txt_path: str, file_name: str,
                        location: str) -> str:
        dest = os.path.join(location, Path(file_name).stem + ".txt")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copy2(local_txt_path, dest)
        log.info("NFS text → %s", dest)
        return dest

    def store_image(self, local_path: str, image_name: str,
                    file_name: str, location: str) -> str:
        img_dir = os.path.join(location, Path(file_name).stem + "_images")
        os.makedirs(img_dir, exist_ok=True)
        dest = os.path.join(img_dir, image_name)
        shutil.copy2(local_path, dest)
        return dest


# ═══════════════════════════════════════════════════════════════════════════════
# URL (HTTP GET to fetch, POST to store)
# ═══════════════════════════════════════════════════════════════════════════════

class URLStorageHandler(StorageHandler):

    def __init__(self, timeout: int = 180):
        self._timeout = timeout

    def fetch_pdf(self, file_name: str, location: str) -> str:
        url = (location if location.endswith(file_name)
               else f"{location.rstrip('/')}/{file_name}")
        tmp = os.path.join(tempfile.mkdtemp(prefix="url_"), file_name)
        log.info("URL GET %s → %s", url, tmp)
        with requests.get(url, timeout=self._timeout, stream=True) as r:
            r.raise_for_status()
            with open(tmp, "wb") as f:
                for chunk in r.iter_content(_CHUNK):
                    f.write(chunk)
        return tmp

    def store_text_file(self, local_txt_path: str, file_name: str,
                        location: str) -> str:
        txt_name = Path(file_name).stem + ".txt"
        url = f"{location.rstrip('/')}/{txt_name}"
        log.info("URL POST text → %s", url)
        # Stream the file instead of reading it all into memory
        with open(local_txt_path, "rb") as fh:
            resp = requests.post(
                url, data=_iter_file(fh),
                headers={"Content-Type": "text/plain; charset=utf-8"},
                timeout=self._timeout,
            )
        resp.raise_for_status()
        return url

    def store_image(self, local_path: str, image_name: str,
                    file_name: str, location: str) -> str:
        url = f"{location.rstrip('/')}/{file_name}"
        log.info("URL POST image %s → %s", image_name, url)
        with open(local_path, "rb") as img:
            resp = requests.post(
                url,
                files={"file": (image_name, img, "application/octet-stream")},
                timeout=self._timeout,
            )
        resp.raise_for_status()
        return url


def _iter_file(fh, chunk_size: int = _CHUNK):
    """Yield a file in chunks for streaming POST."""
    while True:
        data = fh.read(chunk_size)
        if not data:
            break
        yield data


# ═══════════════════════════════════════════════════════════════════════════════
# Factory
# ═══════════════════════════════════════════════════════════════════════════════

_REGISTRY = {
    StorageType.S3:  S3StorageHandler,
    StorageType.NFS: NFSStorageHandler,
    StorageType.URL: URLStorageHandler,
}


def get_handler(storage_type: str) -> StorageHandler:
    try:
        st = StorageType(storage_type)
    except ValueError:
        raise ValueError(
            f"Unknown storage type '{storage_type}'. "
            f"Expected one of: {[e.value for e in StorageType]}"
        )
    return _REGISTRY[st]()
