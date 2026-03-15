"""
Hello World worker registration.

Exposes a `register` dict consumed by the worker registry.
"""

from workers.hello_world.activities import say_hello_long_running
from workers.hello_world.workflows import HelloWorldWorkflow

register = {
    "task_queue": "hello-world-queue",
    "workflows": [HelloWorldWorkflow],
    "activities": [say_hello_long_running],
}
