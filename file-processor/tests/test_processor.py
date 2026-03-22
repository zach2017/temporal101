"""Tests for processor.detect and processor.dispatcher."""

import tempfile
from pathlib import Path

import pytest

from processor.detect import detect_mime
from processor.dispatcher import dispatch_file


# ── detect_mime ──────────────────────────────────────────────


class TestDetectMime:
    def test_plain_text(self, tmp_path: Path):
        f = tmp_path / "hello.txt"
        f.write_text("Hello, world!", encoding="utf-8")
        assert detect_mime(f) == "text/plain"

    def test_json(self, tmp_path: Path):
        f = tmp_path / "data.json"
        f.write_text('{"key": "value"}', encoding="utf-8")
        mime = detect_mime(f)
        # libmagic may return text/plain or application/json for small JSON
        assert mime in ("application/json", "text/plain")

    def test_pdf_magic_bytes(self, tmp_path: Path):
        """A file starting with %PDF should be detected as application/pdf."""
        f = tmp_path / "fake.pdf"
        f.write_bytes(b"%PDF-1.4 fake content")
        assert detect_mime(f) == "application/pdf"

    def test_png_magic_bytes(self, tmp_path: Path):
        """A valid PNG should be detected as image/png."""
        # Create a real 1x1 white PNG via Pillow
        from PIL import Image

        f = tmp_path / "tiny.png"
        img = Image.new("RGB", (1, 1), color=(255, 255, 255))
        img.save(f, format="PNG")
        assert detect_mime(f) == "image/png"


# ── dispatch_file ────────────────────────────────────────────


class TestDispatcher:
    def test_text_file_dispatches(self, tmp_path: Path):
        f = tmp_path / "notes.txt"
        f.write_text("line one\nline two\nline three", encoding="utf-8")
        out = tmp_path / "out"
        out.mkdir()

        messages = list(dispatch_file(f, out))
        # First message should be the MIME detection line
        assert any("text/plain" in m for m in messages)
        # Should mention line count
        assert any("3 lines" in m for m in messages)

    def test_unknown_mime_yields_warning(self, tmp_path: Path):
        """Binary gibberish should trigger the 'no handler' message."""
        f = tmp_path / "mystery.bin"
        f.write_bytes(bytes(range(256)))
        out = tmp_path / "out"
        out.mkdir()

        messages = list(dispatch_file(f, out))
        assert any("No handler" in m or "no handler" in m.lower() for m in messages)


# ── PDF handler (lightweight, no real PDF needed) ────────────


class TestPdfExtractTextGenerator:
    """Verify the generator protocol of extract_text."""

    def test_generator_yields_tuples(self):
        """Create a tiny real PDF with reportlab if available, else skip."""
        pytest.importorskip("pdfplumber")

        # Build a minimal valid PDF in memory with raw bytes
        # (We avoid needing reportlab by using a known minimal PDF)
        minimal_pdf = (
            b"%PDF-1.0\n"
            b"1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
            b"3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R"
            b"/Resources<<>>>>endobj\n"
            b"xref\n0 4\n"
            b"0000000000 65535 f \n"
            b"0000000009 00000 n \n"
            b"0000000058 00000 n \n"
            b"0000000115 00000 n \n"
            b"trailer<</Size 4/Root 1 0 R>>\n"
            b"startxref\n219\n%%EOF"
        )

        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
            tmp.write(minimal_pdf)
            tmp.flush()
            pdf_path = Path(tmp.name)

        from processor.handlers.pdf import extract_text

        pages = list(extract_text(pdf_path))
        # Minimal PDF has 1 page with no text
        assert len(pages) == 1
        page_num, text = pages[0]
        assert page_num == 1
        assert isinstance(text, str)

        pdf_path.unlink()
