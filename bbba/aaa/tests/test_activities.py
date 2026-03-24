"""Tests for all activities using temporalio.testing.ActivityEnvironment."""

import pytest
from temporalio.testing import ActivityEnvironment

from activities import (
    classify_extraction_source,
    is_safe_bucket,
    merge_extraction_outputs,
    normalize_extraction_details,
    promote_to_safe_bucket,
    resolve_bucket,
    validate_extraction_details,
    validate_extraction_output,
)
from models import BucketType, ExtractionDetails, ExtractionOutput


# ── ExtractionDetails Activity Tests ────────────────────────────────────────


class TestValidateExtractionDetails:
    @pytest.mark.asyncio
    async def test_valid_text_detail(self, valid_text_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_details, valid_text_detail)
        assert result is True

    @pytest.mark.asyncio
    async def test_valid_image_detail(self, valid_image_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_details, valid_image_detail)
        assert result is True

    @pytest.mark.asyncio
    async def test_empty_doc_id(self, invalid_detail_empty_doc: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_details, invalid_detail_empty_doc)
        assert result is False

    @pytest.mark.asyncio
    async def test_bad_page_number(self, invalid_detail_bad_page: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_details, invalid_detail_bad_page)
        assert result is False

    @pytest.mark.asyncio
    async def test_empty_key(self):
        env = ActivityEnvironment()
        d = ExtractionDetails(doc_id="doc-1", page_number=1, key="", image_index=0)
        result = await env.run(validate_extraction_details, d)
        assert result is False

    @pytest.mark.asyncio
    async def test_negative_image_index(self):
        env = ActivityEnvironment()
        d = ExtractionDetails(doc_id="doc-1", page_number=1, key="k", image_index=-1)
        result = await env.run(validate_extraction_details, d)
        assert result is False


class TestNormalizeExtractionDetails:
    @pytest.mark.asyncio
    async def test_strips_and_lowercases(self, unnormalized_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(normalize_extraction_details, unnormalized_detail)
        assert result.doc_id == "doc-002"
        assert result.key == "invoice_total"
        assert result.page_number == 5
        assert result.image_index == 0

    @pytest.mark.asyncio
    async def test_already_clean(self, valid_text_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(normalize_extraction_details, valid_text_detail)
        assert result.key == "invoice_total"
        assert result.doc_id == "doc-001"


class TestClassifyExtractionSource:
    @pytest.mark.asyncio
    async def test_text_source(self, valid_text_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(classify_extraction_source, valid_text_detail)
        assert result == "text"

    @pytest.mark.asyncio
    async def test_image_source(self, valid_image_detail: ExtractionDetails):
        env = ActivityEnvironment()
        result = await env.run(classify_extraction_source, valid_image_detail)
        assert result == "image"


# ── ExtractionOutput Activity Tests ─────────────────────────────────────────


class TestValidateExtractionOutput:
    @pytest.mark.asyncio
    async def test_valid_output(self, valid_extraction_output: ExtractionOutput):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_output, valid_extraction_output)
        assert result is True

    @pytest.mark.asyncio
    async def test_empty_output(self, empty_extraction_output: ExtractionOutput):
        env = ActivityEnvironment()
        result = await env.run(validate_extraction_output, empty_extraction_output)
        assert result is False

    @pytest.mark.asyncio
    async def test_image_key_with_zero_index_fails(self):
        env = ActivityEnvironment()
        bad = ExtractionOutput(
            image_keys=[ExtractionDetails("d", 1, "k", 0)],
            text_keys=[],
            bucket=BucketType.TEMP,
        )
        result = await env.run(validate_extraction_output, bad)
        assert result is False

    @pytest.mark.asyncio
    async def test_text_key_with_nonzero_index_fails(self):
        env = ActivityEnvironment()
        bad = ExtractionOutput(
            image_keys=[],
            text_keys=[ExtractionDetails("d", 1, "k", 3)],
            bucket=BucketType.TEMP,
        )
        result = await env.run(validate_extraction_output, bad)
        assert result is False


class TestPromoteToSafeBucket:
    @pytest.mark.asyncio
    async def test_promotes_temp_to_safe(self, valid_extraction_output: ExtractionOutput):
        env = ActivityEnvironment()
        assert valid_extraction_output.bucket == BucketType.TEMP
        result = await env.run(promote_to_safe_bucket, valid_extraction_output)
        assert result.bucket == BucketType.SAFE
        assert len(result.image_keys) == len(valid_extraction_output.image_keys)
        assert len(result.text_keys) == len(valid_extraction_output.text_keys)

    @pytest.mark.asyncio
    async def test_safe_stays_safe(self):
        env = ActivityEnvironment()
        already_safe = ExtractionOutput(
            image_keys=[ExtractionDetails("d", 1, "k", 1)],
            text_keys=[],
            bucket=BucketType.SAFE,
        )
        result = await env.run(promote_to_safe_bucket, already_safe)
        assert result.bucket == BucketType.SAFE


class TestMergeExtractionOutputs:
    @pytest.mark.asyncio
    async def test_merge_two(self, two_extraction_outputs: list[ExtractionOutput]):
        env = ActivityEnvironment()
        result = await env.run(merge_extraction_outputs, two_extraction_outputs)
        assert len(result.image_keys) == 2
        assert len(result.text_keys) == 2
        assert result.bucket == BucketType.TEMP

    @pytest.mark.asyncio
    async def test_merge_single(self, valid_extraction_output: ExtractionOutput):
        env = ActivityEnvironment()
        result = await env.run(merge_extraction_outputs, [valid_extraction_output])
        assert len(result.image_keys) == 1
        assert len(result.text_keys) == 1


# ── BucketType Activity Tests ──────────────────────────────────────────────


class TestResolveBucket:
    @pytest.mark.asyncio
    async def test_resolve_safe(self):
        env = ActivityEnvironment()
        result = await env.run(resolve_bucket, "SAFE")
        assert result == "SAFE"

    @pytest.mark.asyncio
    async def test_resolve_temp(self):
        env = ActivityEnvironment()
        result = await env.run(resolve_bucket, "temp")
        assert result == "TEMP"

    @pytest.mark.asyncio
    async def test_resolve_unknown_raises(self):
        env = ActivityEnvironment()
        with pytest.raises(ValueError, match="Unknown bucket type"):
            await env.run(resolve_bucket, "UNKNOWN")


class TestIsSafeBucket:
    @pytest.mark.asyncio
    async def test_safe_is_true(self):
        env = ActivityEnvironment()
        assert await env.run(is_safe_bucket, "SAFE") is True

    @pytest.mark.asyncio
    async def test_temp_is_false(self):
        env = ActivityEnvironment()
        assert await env.run(is_safe_bucket, "TEMP") is False

    @pytest.mark.asyncio
    async def test_case_insensitive(self):
        env = ActivityEnvironment()
        assert await env.run(is_safe_bucket, "safe") is True
