"""Auto-configure pytesseract across Windows, Linux, and macOS.

pytesseract shells out to the ``tesseract`` binary.  On Linux/macOS it is
normally on $PATH after ``apt install`` / ``brew install``, but on Windows
the UB-Mannheim installer drops it into Program Files without touching PATH.

Import and call ``configure()`` once at startup — before any OCR work.

You can also override the location with the environment variable
``TESSERACT_CMD`` (full path to the binary) or ``TESSERACT_PREFIX``
(directory that contains the binary).
"""

from __future__ import annotations

import os
import platform
import shutil
from pathlib import Path

import pytesseract


# ── platform-specific candidate paths ────────────────────────────────

def _windows_candidates() -> list[Path]:
    """Return common Tesseract install locations on Windows."""
    pf = os.environ.get("PROGRAMFILES", r"C:\Program Files")
    pf86 = os.environ.get("PROGRAMFILES(X86)", r"C:\Program Files (x86)")
    local = os.environ.get("LOCALAPPDATA", "")
    choco = os.environ.get("ChocolateyInstall", r"C:\ProgramData\chocolatey")
    home = os.environ.get("USERPROFILE", "")

    return [
        # UB-Mannheim default installer
        Path(pf) / "Tesseract-OCR" / "tesseract.exe",
        Path(pf86) / "Tesseract-OCR" / "tesseract.exe",
        Path(local) / "Tesseract-OCR" / "tesseract.exe",
        # Root-of-drive installs
        Path(r"C:\Tesseract-OCR\tesseract.exe"),
        Path(r"C:\tesseract\tesseract.exe"),
        Path(r"D:\Tesseract-OCR\tesseract.exe"),
        Path(r"D:\tesseract\tesseract.exe"),
        # Chocolatey
        Path(choco) / "bin" / "tesseract.exe",
        # Scoop
        Path(home) / "scoop" / "apps" / "tesseract" / "current" / "tesseract.exe",
    ]


def _linux_candidates() -> list[Path]:
    """Return common Tesseract install locations on Linux."""
    return [
        Path("/usr/bin/tesseract"),
        Path("/usr/local/bin/tesseract"),
        Path("/snap/bin/tesseract"),
        # Linuxbrew / Homebrew on Linux
        Path("/home/linuxbrew/.linuxbrew/bin/tesseract"),
        Path(os.environ.get("HOME", "")) / ".linuxbrew" / "bin" / "tesseract",
    ]


def _macos_candidates() -> list[Path]:
    """Return common Tesseract install locations on macOS."""
    return [
        Path("/usr/local/bin/tesseract"),          # Intel Homebrew
        Path("/opt/homebrew/bin/tesseract"),        # Apple Silicon Homebrew
    ]


# ── public API ───────────────────────────────────────────────────────

def configure() -> str:
    """Locate the tesseract binary and configure pytesseract.

    Resolution order:
      1. ``TESSERACT_CMD`` env var  (full path to binary)
      2. ``TESSERACT_PREFIX`` env var  (directory containing binary)
      3. Already on ``$PATH`` / ``%PATH%``
      4. Platform-specific well-known locations

    Returns:
        The absolute path to the tesseract binary that was configured.

    Raises:
        FileNotFoundError with an OS-specific install hint if tesseract
        cannot be found anywhere.
    """
    system = platform.system()
    exe_name = "tesseract.exe" if system == "Windows" else "tesseract"

    # 1. Explicit env var — full path
    env_cmd = os.environ.get("TESSERACT_CMD", "").strip()
    if env_cmd and Path(env_cmd).is_file():
        pytesseract.pytesseract.tesseract_cmd = env_cmd
        return env_cmd

    # 2. Explicit env var — directory
    env_prefix = os.environ.get("TESSERACT_PREFIX", "").strip()
    if env_prefix:
        candidate = Path(env_prefix) / exe_name
        if candidate.is_file():
            pytesseract.pytesseract.tesseract_cmd = str(candidate)
            return str(candidate)

    # 3. Already on PATH
    on_path = shutil.which("tesseract")
    if on_path:
        pytesseract.pytesseract.tesseract_cmd = on_path
        return on_path

    # 4. Platform-specific well-known locations
    if system == "Windows":
        candidates = _windows_candidates()
    elif system == "Darwin":
        candidates = _macos_candidates()
    else:
        candidates = _linux_candidates()

    for candidate in candidates:
        if candidate.is_file():
            pytesseract.pytesseract.tesseract_cmd = str(candidate)
            return str(candidate)

    # Nothing found — raise with a helpful message
    raise FileNotFoundError(_install_hint(system))


# ── helpers ──────────────────────────────────────────────────────────

def _install_hint(system: str) -> str:
    base = "Tesseract OCR not found."
    hints = {
        "Windows": (
            f"{base}\n"
            "  Install: https://github.com/UB-Mannheim/tesseract/wiki\n"
            "  Then either:\n"
            "    - Add the install folder to your PATH, or\n"
            "    - Set TESSERACT_CMD=C:\\Program Files\\Tesseract-OCR\\tesseract.exe"
        ),
        "Darwin": (
            f"{base}\n"
            "  Install:  brew install tesseract\n"
            "  Or set:   export TESSERACT_CMD=/path/to/tesseract"
        ),
        "Linux": (
            f"{base}\n"
            "  Install:  sudo apt-get install tesseract-ocr   (Debian/Ubuntu)\n"
            "            sudo dnf install tesseract            (Fedora/RHEL)\n"
            "  Or set:   export TESSERACT_CMD=/path/to/tesseract"
        ),
    }
    return hints.get(system, f"{base}\n  Set TESSERACT_CMD to the full path of the tesseract binary.")
