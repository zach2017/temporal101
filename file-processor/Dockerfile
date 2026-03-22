FROM python:3.13-slim

# ── System deps: Tesseract OCR + libmagic ────────────────────────────
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        libmagic1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ── Python deps (cached layer) ───────────────────────────────────────
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── Application code ─────────────────────────────────────────────────
COPY . .

ENTRYPOINT ["python", "cli.py"]
CMD ["--help"]
