# Document Processing Worker

Temporal workers for a universal document-to-text extraction pipeline.
Accepts any document type, auto-detects MIME, and routes through the
correct extraction workflow.

See the top-level `docker-compose/README.md` for full architecture
documentation, usage, and deployment instructions.

## Local Development (without Docker)

```bash
pip install -r requirements.txt

# Terminal 1 – Intake worker
python -m worker.intake_worker

# Terminal 2 – PDF worker
python -m worker.pdf_worker

# Terminal 3 – OCR worker
python -m worker.ocr_worker

# Terminal 4 – Conversion worker
python -m worker.conversion_worker

# Submit a document
python -m worker.start_workflow \
    --file-name report.pdf \
    --file-location /path/to/report.pdf
```
