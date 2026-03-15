"""
Flask app — document upload + Temporal workflow tracking.

Routes:
  GET  /              → Upload page (HTML)
  POST /upload        → Upload file & start workflow
  GET  /status/<id>   → Query workflow status (JSON)
  GET  /workflows     → List recent workflows (JSON)
"""

import asyncio
import os
import uuid
import json
import threading
from datetime import datetime
from flask import Flask, request, jsonify, render_template, Response
from flask_cors import CORS
from temporalio.client import Client

from workflows import DocumentProcessingWorkflow

app = Flask(__name__)
CORS(app)

UPLOAD_DIR = os.environ.get("UPLOAD_DIR", "/app/uploads")
TEMPORAL_ADDRESS = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
TASK_QUEUE = "document-processing"

os.makedirs(UPLOAD_DIR, exist_ok=True)

# In-memory workflow tracker
workflow_registry: dict = {}


def get_client():
    """Create a new Temporal client (call from async context)."""
    return Client.connect(TEMPORAL_ADDRESS)


def run_async(coro):
    """Run an async coroutine from sync Flask context."""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(coro)
    finally:
        loop.close()


# ── Routes ──────────────────────────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html")


@app.route("/upload", methods=["POST"])
def upload_document():
    """Accept file upload, save it, start a Temporal workflow."""
    if "file" not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"error": "Empty filename"}), 400

    # Generate doc ID
    doc_id = request.form.get("doc_id") or f"doc-{uuid.uuid4().hex[:8]}"

    # Save uploaded file
    safe_name = file.filename.replace("/", "_").replace("\\", "_")
    file_path = os.path.join(UPLOAD_DIR, f"{doc_id}_{safe_name}")
    file.save(file_path)

    # Start Temporal workflow
    async def start_workflow():
        client = await get_client()
        handle = await client.start_workflow(
            DocumentProcessingWorkflow.run,
            args=[file_path, doc_id],
            id=f"doc-{doc_id}",
            task_queue=TASK_QUEUE,
        )
        return handle.id

    try:
        workflow_id = run_async(start_workflow())
    except Exception as e:
        return jsonify({"error": f"Failed to start workflow: {str(e)}"}), 500

    # Track it
    entry = {
        "doc_id": doc_id,
        "workflow_id": workflow_id,
        "filename": safe_name,
        "file_path": file_path,
        "uploaded_at": datetime.utcnow().isoformat(),
        "status": "started",
    }
    workflow_registry[doc_id] = entry

    return jsonify({
        "success": True,
        "doc_id": doc_id,
        "workflow_id": workflow_id,
        "filename": safe_name,
        "message": f"Workflow {workflow_id} started for {safe_name}",
    })


@app.route("/status/<doc_id>")
def get_status(doc_id):
    """Query the Temporal workflow status via query API."""
    entry = workflow_registry.get(doc_id)
    workflow_id = f"doc-{doc_id}"

    async def query_workflow():
        client = await get_client()
        handle = client.get_workflow_handle(workflow_id)

        try:
            status = await handle.query(DocumentProcessingWorkflow.get_status)
            return status
        except Exception as e:
            # Try to get workflow description as fallback
            try:
                desc = await handle.describe()
                return {
                    "status": str(desc.status).split(".")[-1].lower(),
                    "current_step": "unknown",
                    "steps_completed": [],
                    "progress": 0,
                    "error": str(e) if "not found" not in str(e).lower() else None,
                }
            except Exception:
                return {"status": "not_found", "error": str(e)}

    try:
        status = run_async(query_workflow())
    except Exception as e:
        status = {"status": "error", "error": str(e)}

    response = {
        "doc_id": doc_id,
        "workflow_id": workflow_id,
        **status,
    }
    if entry:
        response["filename"] = entry["filename"]
        response["uploaded_at"] = entry["uploaded_at"]

    return jsonify(response)


@app.route("/result/<doc_id>")
def get_result(doc_id):
    """Get the final workflow result."""
    workflow_id = f"doc-{doc_id}"

    async def get_workflow_result():
        client = await get_client()
        handle = client.get_workflow_handle(workflow_id)
        return await handle.result()

    try:
        result = run_async(get_workflow_result())
        return jsonify({"doc_id": doc_id, "result": result})
    except Exception as e:
        return jsonify({"doc_id": doc_id, "error": str(e)}), 404


@app.route("/workflows")
def list_workflows():
    """List all tracked workflows."""
    return jsonify({
        "workflows": list(workflow_registry.values()),
        "count": len(workflow_registry),
    })


@app.route("/health")
def health():
    return jsonify({"status": "ok", "service": "docpipeline"})


# ── Main ────────────────────────────────────────────────────────────────────

def main():
    host = os.environ.get("FLASK_HOST", "0.0.0.0")
    port = int(os.environ.get("FLASK_PORT", 5555))
    print(f"Starting Flask app on {host}:{port}")
    app.run(host=host, port=port, debug=False)


if __name__ == "__main__":
    main()
