# Temporal Extraction Pipeline

A Python Temporal project with dedicated workers, workflows, activities, clients,
and tests for each dataclass: **BucketType**, **ExtractionDetails**, and **ExtractionOutput**.

## Project Structure

```
temporal_project/
├── models/              # Dataclasses: BucketType, ExtractionDetails, ExtractionOutput
│   └── __init__.py
├── activities/          # @activity.defn functions for each dataclass
│   └── __init__.py
├── workflows/           # @workflow.defn classes for each dataclass
│   ├── __init__.py
│   ├── bucket_type_workflow.py
│   ├── extraction_details_workflow.py
│   └── extraction_output_workflow.py
├── workers/             # One worker process per dataclass
│   ├── bucket_type_worker.py
│   ├── extraction_details_worker.py
│   └── extraction_output_worker.py
├── clients/             # Client scripts to trigger workflows
│   ├── bucket_type_client.py
│   ├── extraction_details_client.py
│   └── extraction_output_client.py
├── tests/               # Pytest suite (activities + workflow integration)
│   ├── conftest.py
│   ├── test_models.py
│   ├── test_activities.py
│   └── test_workflows.py
├── requirements.txt
└── pyproject.toml
```

## Setup

```bash
pip install -r requirements.txt
```

## Running Tests

```bash
# All tests (model + activity unit tests run without a server;
# workflow tests use the built-in time-skipping test server)
pytest -v

# Just activity tests (no server needed)
pytest tests/test_activities.py -v

# Just workflow integration tests
pytest tests/test_workflows.py -v
```

## Running Workers (requires Temporal Server on localhost:7233)

```bash
# In separate terminals:
python -m workers.extraction_details_worker
python -m workers.extraction_output_worker
python -m workers.bucket_type_worker
```

## Running Clients (requires matching worker to be running)

```bash
python -m clients.extraction_details_client
python -m clients.extraction_output_client
python -m clients.bucket_type_client
```

## Architecture

| Dataclass          | Task Queue                       | Workflows                                            |
|--------------------|----------------------------------|------------------------------------------------------|
| ExtractionDetails  | extraction-details-task-queue    | ExtractionDetailsWorkflow                            |
| ExtractionOutput   | extraction-output-task-queue     | ExtractionOutputWorkflow, MergeExtractionOutputsWorkflow |
| BucketType         | bucket-type-task-queue           | BucketTypeWorkflow                                   |
