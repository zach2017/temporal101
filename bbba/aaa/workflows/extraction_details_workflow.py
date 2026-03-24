"""Temporal workflow for ExtractionDetails operations."""

from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from activities import (
        classify_extraction_source,
        normalize_extraction_details,
        validate_extraction_details,
    )
    from models import ExtractionDetails


@workflow.defn
class ExtractionDetailsWorkflow:
    """Validate, normalize, and classify a single ExtractionDetails record."""

    @workflow.run
    async def run(self, details: ExtractionDetails) -> ExtractionDetails:
        # Step 1: Validate
        is_valid = await workflow.execute_activity(
            validate_extraction_details,
            details,
            start_to_close_timeout=timedelta(seconds=10),
        )
        if not is_valid:
            raise ValueError(
                f"Invalid extraction details: doc_id={details.doc_id}, "
                f"page={details.page_number}, key={details.key}"
            )

        # Step 2: Normalize
        normalized = await workflow.execute_activity(
            normalize_extraction_details,
            details,
            start_to_close_timeout=timedelta(seconds=10),
        )

        # Step 3: Classify source
        source = await workflow.execute_activity(
            classify_extraction_source,
            normalized,
            start_to_close_timeout=timedelta(seconds=10),
        )

        workflow.logger.info(
            f"ExtractionDetails processed: doc={normalized.doc_id}, "
            f"key={normalized.key}, source={source}"
        )
        return normalized
