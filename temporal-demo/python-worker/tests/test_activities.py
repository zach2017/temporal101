"""
Unit tests for the compose_greeting activity.

Activities are regular Python async functions, so they can be tested
both directly (plain async call) and via Temporal's ActivityEnvironment
(which provides activity context like task queue and attempt info).

Test Flow (direct):
  1. Call compose_greeting() as a normal async function
  2. Assert output string format and content

Test Flow (ActivityEnvironment):
  1. Create ActivityEnvironment from conftest fixture
  2. Run activity through env.run() which injects Temporal context
  3. Assert output + verify context was available
"""

import platform
import pytest
from activities import compose_greeting


class TestComposeGreetingDirect:
    """Tests calling the activity function directly (no Temporal context)."""

    @pytest.mark.asyncio
    async def test_greeting_contains_name(self):
        """Activity output should include the provided name."""
        result = await compose_greeting("Alice")
        assert "Hello Alice" in result

    @pytest.mark.asyncio
    async def test_greeting_identifies_python_worker(self):
        """Activity output should identify itself as the Python Worker."""
        result = await compose_greeting("Test")
        assert "Python Worker" in result

    @pytest.mark.asyncio
    async def test_greeting_contains_python_version(self):
        """Activity output should include the Python version."""
        result = await compose_greeting("Test")
        expected_version = platform.python_version()
        assert f"python={expected_version}" in result

    @pytest.mark.asyncio
    async def test_greeting_contains_hostname(self):
        """Activity output should include a hostname."""
        result = await compose_greeting("Test")
        assert "host=" in result

    @pytest.mark.asyncio
    async def test_greeting_contains_timestamp(self):
        """Activity output should include a UTC timestamp."""
        result = await compose_greeting("Test")
        assert "time=" in result
        assert "Z]" in result  # ISO-8601 UTC indicator

    @pytest.mark.asyncio
    async def test_greeting_empty_name(self):
        """Activity should handle empty string name."""
        result = await compose_greeting("")
        assert "Hello " in result
        assert "Python Worker" in result

    @pytest.mark.asyncio
    async def test_greeting_special_characters(self):
        """Activity should preserve special characters in name."""
        result = await compose_greeting("O'Brien <>&")
        assert "O'Brien <>&" in result

    @pytest.mark.asyncio
    async def test_greeting_unicode_name(self):
        """Activity should handle unicode characters."""
        result = await compose_greeting("名前テスト")
        assert "名前テスト" in result


class TestComposeGreetingWithEnvironment:
    """Tests using Temporal's ActivityEnvironment for context injection."""

    @pytest.mark.asyncio
    async def test_activity_runs_in_environment(self, activity_env):
        """Activity should execute successfully within ActivityEnvironment."""
        result = await activity_env.run(compose_greeting, "EnvTest")
        assert "Hello EnvTest" in result
        assert "Python Worker" in result

    @pytest.mark.asyncio
    async def test_activity_returns_string(self, activity_env):
        """Activity return type should be a string."""
        result = await activity_env.run(compose_greeting, "TypeCheck")
        assert isinstance(result, str)
