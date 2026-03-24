"""Temporal workflow for ExtractionOutput operations."""

from datetime import timedelta
from typing import List

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from activities import (
        merge_extraction_outputs,
        promote_to_safe_bucket,
        validate_extraction_output,
    )
    from models import ExtractionOutput


@workflow.defn
class ExtractionOutputWorkflow:
    """Validate an ExtractionOutput and promote it to the SAFE bucket."""

    @workflow.run
    async def run(self, output: ExtractionOutput) -> ExtractionOutput:
        # Step 1: Validate
        is_valid = await workflow.execute_activity(
            validate_extraction_output,
            output,
            start_to_close_timeout=timedelta(seconds=10),
        )
        if not is_valid:
            raise ValueError(
                f"Invalid extraction output: "
                f"{len(output.image_keys)} images, {len(output.text_keys)} texts"
            )

        # Step 2: Promote to SAFE
        promoted = await workflow.execute_activity(
            promote_to_safe_bucket,
            output,
            start_to_close_timeout=timedelta(seconds=10),
        )

        workflow.logger.info(
            f"ExtractionOutput promoted: bucket={promoted.bucket.name}, "
            f"images={len(promoted.image_keys)}, texts={len(promoted.text_keys)}"
        )
        return promoted


@workflow.defn
class MergeExtractionOutputsWorkflow:
    """Merge multiple ExtractionOutputs and promote the result."""

    @workflow.run
    async def run(self, outputs: List[ExtractionOutput]) -> ExtractionOutput:
        if not outputs:
            raise ValueError("Cannot merge an empty list of ExtractionOutputs")

        # Step 1: Validate each output
        for i, output in enumerate(outputs):
            is_valid = await workflow.execute_activity(
                validate_extraction_output,
                output,
                start_to_close_timeout=timedelta(seconds=10),
            )
            if not is_valid:
                raise ValueError(f"Invalid extraction output at index {i}")

        # Step 2: Merge
        merged = await workflow.execute_activity(
            merge_extraction_outputs,
            outputs,
            start_to_close_timeout=timedelta(seconds=30),
        )

        # Step 3: Promote
        promoted = await workflow.execute_activity(
            promote_to_safe_bucket,
            merged,
            start_to_close_timeout=timedelta(seconds=10),
        )

        workflow.logger.info(
            f"Merged {len(outputs)} outputs → "
            f"images={len(promoted.image_keys)}, texts={len(promoted.text_keys)}, "
            f"bucket={promoted.bucket.name}"
        )
        return promoted
