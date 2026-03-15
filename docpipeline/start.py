"""
Start both the Flask upload app and the Temporal worker
in the same process using threads.
"""

import threading
import time
import asyncio
import os
from app import main as flask_main
from worker import run_worker


def start_worker():
    """Run Temporal worker in a background thread."""
    time.sleep(3)  # let Flask start first
    print("[start.py] Starting Temporal worker...")
    asyncio.run(run_worker())


def main():
    print("=" * 60)
    print("  Document Pipeline — Flask + Temporal Worker")
    print("=" * 60)

    # Start worker in background thread
    worker_thread = threading.Thread(target=start_worker, daemon=True)
    worker_thread.start()

    # Start Flask in main thread
    flask_main()


if __name__ == "__main__":
    main()
