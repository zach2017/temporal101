import platform
import socket
from datetime import datetime

from temporalio import activity


@activity.defn
async def compose_greeting(name: str) -> str:
    """Activity that composes a greeting message with worker metadata."""
    hostname = socket.gethostname()
    return (
        f"Hello {name} from Python Worker! "
        f"[host={hostname}, python={platform.python_version()}, "
        f"time={datetime.utcnow().isoformat()}Z]"
    )
