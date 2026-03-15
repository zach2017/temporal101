FROM python:3.13-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy worker source
COPY activities.py .
COPY processing_activities.py .
COPY workflows.py .
COPY async_workflow.py .
COPY long_running_workflow.py .
COPY worker.py .

CMD ["python", "worker.py"]
