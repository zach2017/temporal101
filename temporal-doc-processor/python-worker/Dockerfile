FROM python:3.13-slim

WORKDIR /app

# System deps for PyMuPDF
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p /app/storage/uploads /app/storage/documents

EXPOSE 8080

CMD ["python", "entrypoint.py"]
