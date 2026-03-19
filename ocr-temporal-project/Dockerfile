FROM eclipse-temurin:17-jre-jammy

# Install Tesseract and common language packs
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-deu \
    tesseract-ocr-fra \
    tesseract-ocr-spa \
    tesseract-ocr-ita \
    tesseract-ocr-por \
    tesseract-ocr-jpn \
    tesseract-ocr-chi-sim \
    tesseract-ocr-kor \
    tesseract-ocr-ara \
    tesseract-ocr-hin \
    tesseract-ocr-rus \
    && rm -rf /var/lib/apt/lists/*

# Create working directories
RUN mkdir -p /app /tmp/ocr-worker

# Copy the worker jar
COPY ocr-worker/target/ocr-worker-1.0.0.jar /app/ocr-worker.jar

# Environment defaults
ENV TEMPORAL_ADDRESS=localhost:7233
ENV TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata
ENV OCR_TEMP_DIR=/tmp/ocr-worker
ENV OCR_TASK_QUEUE=OCR_TASK_QUEUE
ENV OCR_MIN_CONFIDENCE=15.0

WORKDIR /app

ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app/ocr-worker.jar"]
