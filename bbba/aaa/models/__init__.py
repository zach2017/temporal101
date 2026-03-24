"""Dataclass models used across Temporal workflows, activities, and workers."""

from dataclasses import dataclass
from enum import Enum, auto
from typing import List


class BucketType(Enum):
    SAFE = auto()
    TEMP = auto()


@dataclass
class ExtractionDetails:
    """Single extraction record — one key pulled from a page."""
    doc_id: str
    page_number: int  # 1 based
    key: str
    image_index: int  # 1 based, zero if text is the source


@dataclass
class ExtractionOutput:
    """Full result of an extraction run."""
    image_keys: List[ExtractionDetails]
    text_keys: List[ExtractionDetails]
    bucket: BucketType
