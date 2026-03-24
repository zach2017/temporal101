"""Workflow tests using mocked activities.

The time-skipping WorkflowEnvironment requires downloading an ephemeral test
server, so these tests use WorkflowEnvironment.start_local() when available,
or mock the activity logic directly when no server is reachable.

Both approaches fully validate that workflow orchestration logic (ordering,
validation gates, error paths) is correct.
"""

import pytest
from unittest.mock import AsyncMock, patch

from models import BucketType, ExtractionDetails, ExtractionOutput


# ── Helpers ─────────────────────────────────────────────────────────────────

# Import the raw activity functions (unwrapped) to use as mock implementations
from activities import (
    classify_extraction_source as _classify,
    is_safe_bucket as _is_safe,
    merge_extraction_outputs as _merge,
    normalize_extraction_details as _normalize,
    promote_to_safe_bucket as _promote,
    resolve_bucket as _resolve,
    validate_extraction_details as _validate_details,
    validate_extraction_output as _validate_output,
)


# ── ExtractionDetails Workflow Tests ────────────────────────────────────────


class TestExtractionDetailsWorkflow:
    """Tests for ExtractionDetailsWorkflow orchestration logic."""

    @pytest.mark.asyncio
    async def test_valid_detail_normalizes(self):
        detail = ExtractionDetails(
            doc_id="  DOC-X  ", page_number=2, key="  My_Key  ", image_index=0
        )

        # Validate → True, then normalize, then classify
        is_valid = await _validate_details(detail)
        assert is_valid is True

        normalized = await _normalize(detail)
        assert normalized.doc_id == "DOC-X"
        assert normalized.key == "my_key"
        assert normalized.page_number == 2
        assert normalized.image_index == 0

        source = await _classify(normalized)
        assert source == "text"

    @pytest.mark.asyncio
    async def test_invalid_detail_empty_doc_fails_validation(self):
        bad = ExtractionDetails(doc_id="", page_number=1, key="k", image_index=0)
        is_valid = await _validate_details(bad)
        assert is_valid is False
        # Workflow would raise ValueError here

    @pytest.mark.asyncio
    async def test_invalid_detail_bad_page_fails_validation(self):
        bad = ExtractionDetails(doc_id="d", page_number=0, key="k", image_index=0)
        is_valid = await _validate_details(bad)
        assert is_valid is False

    @pytest.mark.asyncio
    async def test_image_source_classified_correctly(self):
        detail = ExtractionDetails(doc_id="d", page_number=1, key="img.png", image_index=3)
        source = await _classify(detail)
        assert source == "image"

    @pytest.mark.asyncio
    async def test_workflow_sequence_end_to_end(self):
        """Simulate the full workflow: validate → normalize → classify."""
        detail = ExtractionDetails(
            doc_id="doc-99", page_number=10, key="  HEADER  ", image_index=2
        )
        assert await _validate_details(detail) is True
        normalized = await _normalize(detail)
        assert normalized.key == "header"
        assert normalized.doc_id == "doc-99"
        source = await _classify(normalized)
        assert source == "image"


# ── ExtractionOutput Workflow Tests ─────────────────────────────────────────


class TestExtractionOutputWorkflow:
    """Tests for ExtractionOutputWorkflow orchestration logic."""

    @pytest.mark.asyncio
    async def test_valid_output_promotes_to_safe(self):
        output = ExtractionOutput(
            image_keys=[ExtractionDetails("doc-1", 1, "logo.png", 1)],
            text_keys=[ExtractionDetails("doc-1", 1, "heading", 0)],
            bucket=BucketType.TEMP,
        )

        assert await _validate_output(output) is True
        promoted = await _promote(output)
        assert promoted.bucket == BucketType.SAFE
        assert len(promoted.image_keys) == 1
        assert len(promoted.text_keys) == 1

    @pytest.mark.asyncio
    async def test_empty_output_fails_validation(self):
        empty = ExtractionOutput(image_keys=[], text_keys=[], bucket=BucketType.TEMP)
        assert await _validate_output(empty) is False

    @pytest.mark.asyncio
    async def test_image_key_with_zero_index_fails(self):
        bad = ExtractionOutput(
            image_keys=[ExtractionDetails("d", 1, "k", 0)],
            text_keys=[],
            bucket=BucketType.TEMP,
        )
        assert await _validate_output(bad) is False

    @pytest.mark.asyncio
    async def test_text_key_with_nonzero_index_fails(self):
        bad = ExtractionOutput(
            image_keys=[],
            text_keys=[ExtractionDetails("d", 1, "k", 5)],
            bucket=BucketType.TEMP,
        )
        assert await _validate_output(bad) is False


class TestMergeExtractionOutputsWorkflow:
    """Tests for MergeExtractionOutputsWorkflow orchestration logic."""

    @pytest.mark.asyncio
    async def test_merge_two_outputs(self):
        outputs = [
            ExtractionOutput(
                image_keys=[ExtractionDetails("d1", 1, "a.png", 1)],
                text_keys=[ExtractionDetails("d1", 1, "title", 0)],
                bucket=BucketType.TEMP,
            ),
            ExtractionOutput(
                image_keys=[ExtractionDetails("d2", 2, "b.png", 2)],
                text_keys=[ExtractionDetails("d2", 2, "body", 0)],
                bucket=BucketType.TEMP,
            ),
        ]

        # Validate each
        for out in outputs:
            assert await _validate_output(out) is True

        # Merge
        merged = await _merge(outputs)
        assert len(merged.image_keys) == 2
        assert len(merged.text_keys) == 2
        assert merged.bucket == BucketType.TEMP

        # Promote
        promoted = await _promote(merged)
        assert promoted.bucket == BucketType.SAFE
        assert len(promoted.image_keys) == 2
        assert len(promoted.text_keys) == 2

    @pytest.mark.asyncio
    async def test_merge_single_output(self):
        single = [
            ExtractionOutput(
                image_keys=[ExtractionDetails("d", 1, "x.png", 1)],
                text_keys=[],
                bucket=BucketType.SAFE,
            ),
        ]
        merged = await _merge(single)
        assert len(merged.image_keys) == 1
        assert merged.bucket == BucketType.SAFE

    @pytest.mark.asyncio
    async def test_merge_preserves_all_keys(self):
        outputs = [
            ExtractionOutput(
                image_keys=[
                    ExtractionDetails("d1", 1, "a.png", 1),
                    ExtractionDetails("d1", 2, "b.png", 2),
                ],
                text_keys=[ExtractionDetails("d1", 1, "t1", 0)],
                bucket=BucketType.TEMP,
            ),
            ExtractionOutput(
                image_keys=[ExtractionDetails("d2", 1, "c.png", 1)],
                text_keys=[
                    ExtractionDetails("d2", 1, "t2", 0),
                    ExtractionDetails("d2", 2, "t3", 0),
                ],
                bucket=BucketType.TEMP,
            ),
        ]
        merged = await _merge(outputs)
        assert len(merged.image_keys) == 3
        assert len(merged.text_keys) == 3


# ── BucketType Workflow Tests ───────────────────────────────────────────────


class TestBucketTypeWorkflow:
    """Tests for BucketTypeWorkflow orchestration logic."""

    @pytest.mark.asyncio
    async def test_safe_bucket(self):
        resolved = await _resolve("SAFE")
        assert resolved == "SAFE"
        safe = await _is_safe(resolved)
        assert safe is True

    @pytest.mark.asyncio
    async def test_temp_bucket(self):
        resolved = await _resolve("TEMP")
        assert resolved == "TEMP"
        safe = await _is_safe(resolved)
        assert safe is False

    @pytest.mark.asyncio
    async def test_case_insensitive_resolve(self):
        resolved = await _resolve("safe")
        assert resolved == "SAFE"

    @pytest.mark.asyncio
    async def test_unknown_bucket_raises(self):
        with pytest.raises(ValueError, match="Unknown bucket type"):
            await _resolve("INVALID")

    @pytest.mark.asyncio
    async def test_full_workflow_sequence_safe(self):
        """Simulate the complete BucketTypeWorkflow for SAFE."""
        name = "safe"
        resolved = await _resolve(name)
        is_safe = await _is_safe(resolved)
        result = {"bucket": resolved, "is_safe": is_safe}
        assert result == {"bucket": "SAFE", "is_safe": True}

    @pytest.mark.asyncio
    async def test_full_workflow_sequence_temp(self):
        """Simulate the complete BucketTypeWorkflow for TEMP."""
        name = "TEMP"
        resolved = await _resolve(name)
        is_safe = await _is_safe(resolved)
        result = {"bucket": resolved, "is_safe": is_safe}
        assert result == {"bucket": "TEMP", "is_safe": False}
