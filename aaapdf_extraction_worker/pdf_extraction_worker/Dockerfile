FROM python:3.12-slim AS base

WORKDIR /app

# System deps for pymupdf
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libmupdf-dev \
        libfreetype6 \
        libharfbuzz0b \
        libjpeg62-turbo \
        libopenjp2-7 && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# ── PDF Worker (default) ──
FROM base AS pdf-worker
CMD ["python", "-m", "worker.pdf_worker"]

# ── OCR Worker ──
FROM base AS ocr-worker
CMD ["python", "-m", "worker.ocr_worker"]
