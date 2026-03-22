FROM python:slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libmupdf-dev libfreetype6 libharfbuzz0b libjbig2dec0 \
        libopenjp2-7 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENTRYPOINT ["python", "worker.py"]
CMD ["--host", "temporal:7233"]
