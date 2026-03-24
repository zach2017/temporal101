"""Temporal activities for BucketType, ExtractionDetails, and ExtractionOutput."""

import logging
from typing import List

from temporalio import activity

from models import BucketType, ExtractionDetails, ExtractionOutput

logger = logging.getLogger(__name__)


# ── ExtractionDetails Activities ────────────────────────────────────────────


@activity.defn
async def validate_extraction_details(details: ExtractionDetails) -> bool:
    """Validate a single ExtractionDetails record."""
    logger.info(f"Validating extraction detail: doc={details.doc_id}, page={details.page_number}")
    if not details.doc_id or not details.key:
        return False
    if details.page_number < 1:
        return False
    if details.image_index < 0:
        return False
    return True


@activity.defn
async def normalize_extraction_details(details: ExtractionDetails) -> ExtractionDetails:
    """Normalize key casing and strip whitespace."""
    logger.info(f"Normalizing extraction detail: {details.key}")
    return ExtractionDetails(
        doc_id=details.doc_id.strip(),
        page_number=details.page_number,
        key=details.key.strip().lower(),
        image_index=details.image_index,
    )


@activity.defn
async def classify_extraction_source(details: ExtractionDetails) -> str:
    """Return 'image' or 'text' based on image_index."""
    source = "text" if details.image_index == 0 else "image"
    logger.info(f"Classified {details.key} as source={source}")
    return source


# ── ExtractionOutput Activities ─────────────────────────────────────────────


@activity.defn
async def validate_extraction_output(output: ExtractionOutput) -> bool:
    """Validate a full ExtractionOutput."""
    logger.info(
        f"Validating extraction output: "
        f"{len(output.image_keys)} images, {len(output.text_keys)} texts, "
        f"bucket={output.bucket.name}"
    )
    if not output.image_keys and not output.text_keys:
        return False
    for d in output.image_keys:
        if d.image_index < 1:
            return False
    for d in output.text_keys:
        if d.image_index != 0:
            return False
    return True


@activity.defn
async def promote_to_safe_bucket(output: ExtractionOutput) -> ExtractionOutput:
    """Promote an extraction output from TEMP to SAFE bucket."""
    logger.info("Promoting extraction output to SAFE bucket")
    return ExtractionOutput(
        image_keys=output.image_keys,
        text_keys=output.text_keys,
        bucket=BucketType.SAFE,
    )


@activity.defn
async def merge_extraction_outputs(outputs: List[ExtractionOutput]) -> ExtractionOutput:
    """Merge multiple ExtractionOutputs into one (uses first output's bucket)."""
    logger.info(f"Merging {len(outputs)} extraction outputs")
    all_image_keys: List[ExtractionDetails] = []
    all_text_keys: List[ExtractionDetails] = []
    bucket = outputs[0].bucket if outputs else BucketType.TEMP
    for out in outputs:
        all_image_keys.extend(out.image_keys)
        all_text_keys.extend(out.text_keys)
    return ExtractionOutput(
        image_keys=all_image_keys,
        text_keys=all_text_keys,
        bucket=bucket,
    )


# ── BucketType Activities ──────────────────────────────────────────────────


@activity.defn
async def resolve_bucket(bucket_name: str) -> str:
    """Resolve a bucket name string to a BucketType enum name."""
    logger.info(f"Resolving bucket: {bucket_name}")
    try:
        bt = BucketType[bucket_name.upper()]
        return bt.name
    except KeyError:
        raise ValueError(f"Unknown bucket type: {bucket_name}")


@activity.defn
async def is_safe_bucket(bucket_name: str) -> bool:
    """Check whether the given bucket name is SAFE."""
    logger.info(f"Checking if bucket is SAFE: {bucket_name}")
    return bucket_name.upper() == BucketType.SAFE.name
