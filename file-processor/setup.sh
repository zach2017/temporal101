#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# setup.sh — Install Tesseract OCR + Python deps locally
# Supports: Ubuntu/Debian, Fedora/RHEL, macOS (Homebrew)
# ============================================================

echo "🔧 file-processor — local setup"
echo "================================"

# -----------------------------------------------------------
# 1. Install Tesseract OCR + libmagic
# -----------------------------------------------------------
install_system_deps() {
    if command -v apt-get &>/dev/null; then
        echo "📦 Detected Debian/Ubuntu — installing via apt …"
        sudo apt-get update
        sudo apt-get install -y tesseract-ocr tesseract-ocr-eng libmagic1

    elif command -v dnf &>/dev/null; then
        echo "📦 Detected Fedora/RHEL — installing via dnf …"
        sudo dnf install -y tesseract tesseract-langpack-eng file-libs

    elif command -v brew &>/dev/null; then
        echo "📦 Detected macOS (Homebrew) — installing via brew …"
        brew install tesseract libmagic

    else
        echo "⚠️  Could not detect package manager."
        echo "   Please install Tesseract OCR manually:"
        echo "     https://github.com/tesseract-ocr/tesseract#installing-tesseract"
        exit 1
    fi
}

# -----------------------------------------------------------
# 2. Verify Tesseract is available
# -----------------------------------------------------------
verify_tesseract() {
    if ! command -v tesseract &>/dev/null; then
        echo "✖ tesseract not found on PATH after install."
        exit 1
    fi
    echo "✅ Tesseract $(tesseract --version 2>&1 | head -1)"
}

# -----------------------------------------------------------
# 3. Create venv & install Python deps
# -----------------------------------------------------------
install_python_deps() {
    VENV_DIR=".venv"
    if [ ! -d "$VENV_DIR" ]; then
        echo "🐍 Creating virtual environment → $VENV_DIR"
        python3 -m venv "$VENV_DIR"
    fi

    echo "🐍 Installing Python dependencies …"
    # shellcheck disable=SC1091
    source "$VENV_DIR/bin/activate"
    pip install --upgrade pip
    pip install -r requirements.txt
    echo "✅ Python deps installed in $VENV_DIR"
}

# -----------------------------------------------------------
# Run
# -----------------------------------------------------------
install_system_deps
verify_tesseract
install_python_deps

echo ""
echo "🎉 Setup complete!"
echo "   Activate the venv:  source .venv/bin/activate"
echo "   Run the CLI:        python cli.py <file>"
