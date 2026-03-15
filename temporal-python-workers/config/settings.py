"""
Centralized configuration for Temporal workers.
All settings can be overridden via environment variables.
"""

from pydantic_settings import BaseSettings


class TemporalSettings(BaseSettings):
    """Temporal server connection settings."""

    host: str = "localhost"
    port: int = 7233
    namespace: str = "default"
    tls_enabled: bool = False

    @property
    def server_url(self) -> str:
        return f"{self.host}:{self.port}"

    class Config:
        env_prefix = "TEMPORAL_"


class WorkerSettings(BaseSettings):
    """General worker settings."""

    max_concurrent_activities: int = 10
    max_concurrent_workflows: int = 10
    log_level: str = "INFO"

    class Config:
        env_prefix = "WORKER_"


settings = TemporalSettings()
worker_settings = WorkerSettings()
