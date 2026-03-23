FROM python:3.13-slim AS base

WORKDIR /app

# System deps: libmagic for MIME detection, pymupdf deps
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libmagic1 \
        libmupdf-dev \
        libfreetype6 \
        libharfbuzz0b \
        libjpeg62-turbo \
        libopenjp2-7 && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# ── Intake Worker (MIME detection + routing) ──
FROM base AS intake-worker
CMD ["python", "-m", "worker.intake_worker"]

# ── PDF Extraction Worker ──
FROM base AS pdf-worker
CMD ["python", "-m", "worker.pdf_worker"]

# ── Image OCR Worker ──
FROM base AS ocr-worker
CMD ["python", "-m", "worker.ocr_worker"]

# ── Document Conversion Worker ──
FROM base AS conversion-worker
CMD ["python", "-m", "worker.conversion_worker"]
