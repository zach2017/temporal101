"""Shared pytest fixtures for Temporal tests."""

import pytest

from models import BucketType, ExtractionDetails, ExtractionOutput


# ── ExtractionDetails Fixtures ──────────────────────────────────────────────


@pytest.fixture
def valid_text_detail() -> ExtractionDetails:
    return ExtractionDetails(
        doc_id="doc-001",
        page_number=1,
        key="invoice_total",
        image_index=0,
    )


@pytest.fixture
def valid_image_detail() -> ExtractionDetails:
    return ExtractionDetails(
        doc_id="doc-001",
        page_number=2,
        key="company_logo.png",
        image_index=1,
    )


@pytest.fixture
def unnormalized_detail() -> ExtractionDetails:
    return ExtractionDetails(
        doc_id="  doc-002  ",
        page_number=5,
        key="  Invoice_TOTAL  ",
        image_index=0,
    )


@pytest.fixture
def invalid_detail_empty_doc() -> ExtractionDetails:
    return ExtractionDetails(
        doc_id="",
        page_number=1,
        key="some_key",
        image_index=0,
    )


@pytest.fixture
def invalid_detail_bad_page() -> ExtractionDetails:
    return ExtractionDetails(
        doc_id="doc-003",
        page_number=0,
        key="some_key",
        image_index=0,
    )


# ── ExtractionOutput Fixtures ──────────────────────────────────────────────


@pytest.fixture
def valid_extraction_output(
    valid_image_detail: ExtractionDetails,
    valid_text_detail: ExtractionDetails,
) -> ExtractionOutput:
    return ExtractionOutput(
        image_keys=[valid_image_detail],
        text_keys=[valid_text_detail],
        bucket=BucketType.TEMP,
    )


@pytest.fixture
def empty_extraction_output() -> ExtractionOutput:
    return ExtractionOutput(
        image_keys=[],
        text_keys=[],
        bucket=BucketType.TEMP,
    )


@pytest.fixture
def two_extraction_outputs() -> list[ExtractionOutput]:
    return [
        ExtractionOutput(
            image_keys=[
                ExtractionDetails(doc_id="doc-a", page_number=1, key="img1.png", image_index=1),
            ],
            text_keys=[
                ExtractionDetails(doc_id="doc-a", page_number=1, key="title", image_index=0),
            ],
            bucket=BucketType.TEMP,
        ),
        ExtractionOutput(
            image_keys=[
                ExtractionDetails(doc_id="doc-b", page_number=3, key="img2.png", image_index=2),
            ],
            text_keys=[
                ExtractionDetails(doc_id="doc-b", page_number=3, key="footer", image_index=0),
            ],
            bucket=BucketType.TEMP,
        ),
    ]
