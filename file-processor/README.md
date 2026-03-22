# file-processor

A Python CLI that **detects file types via libmagic** (MIME), then dispatches to the right handler. PDF processing uses **generators (`yield`) throughout** to keep memory usage minimal — pages and images are processed one at a time and never buffered in bulk.

## Features

| MIME type | Handler | What it does |
|---|---|---|
| `application/pdf` | `handlers/pdf.py` | Extract text (pdfplumber) **+** extract embedded images (PyMuPDF) **+** OCR each image (Tesseract) |
| `image/*` | `handlers/image.py` | OCR the image with Tesseract |
| `text/*`, `application/json` | `handlers/text.py` | Print line/char summary |

---

## Quick start

### Option A — Local install

```bash
# 1. Install Tesseract + libmagic + Python deps
chmod +x setup.sh
./setup.sh

# 2. Activate venv
source .venv/bin/activate

# 3. Run
python cli.py path/to/file.pdf -o results/
```

### Option B — Docker Compose

```bash
# Put files you want to process in ./data/
mkdir -p data output

# Build
docker compose build

# Process a file
docker compose run file-processor /data/sample.pdf -o /output
```

---

## Manual Tesseract installation

### Ubuntu / Debian
```bash
sudo apt-get update
sudo apt-get install -y tesseract-ocr tesseract-ocr-eng libmagic1
```

### Fedora / RHEL
```bash
sudo dnf install -y tesseract tesseract-langpack-eng file-libs
```

### macOS (Homebrew)
```bash
brew install tesseract libmagic
```

### Windows
Download the installer from [UB-Mannheim/tesseract](https://github.com/UB-Mannheim/tesseract/wiki) and add the install directory to your `PATH`.

---

## CLI usage

```
Usage: cli.py [OPTIONS] FILEPATH

  Detect MIME type of FILEPATH and process it accordingly.

Options:
  -o, --output-dir PATH  Directory for extracted output
  -v, --verbose          Print extra info while processing
  --help                 Show this message and exit.
```

### Examples

```bash
# PDF → text + images + OCR
python cli.py invoice.pdf

# Image → OCR
python cli.py scan.png -o ocr_results/

# Text summary
python cli.py data.csv -v
```

---

## Project structure

```
file-processor/
├── cli.py                       # Click CLI entrypoint
├── processor/
│   ├── detect.py                # MIME detection (python-magic / libmagic)
│   ├── dispatcher.py            # Routes MIME → handler (generator)
│   └── handlers/
│       ├── pdf.py               # extract_text() + extract_and_ocr_images()
│       ├── image.py             # Standalone image OCR
│       └── text.py              # Text / CSV / JSON summary
├── setup.sh                     # One-command local setup
├── Dockerfile
├── docker-compose.yml
├── pyproject.toml
├── requirements.txt
└── README.md
```

## Memory-efficient design

Every handler is a **generator** that `yield`s status strings. The PDF handler in particular:

1. **`extract_text()`** — opens the PDF with `pdfplumber`, iterates pages one at a time, and yields `(page_num, text)`. The previous page's data is discarded each iteration.
2. **`extract_and_ocr_images()`** — uses PyMuPDF to walk through embedded images. Each image is extracted, saved, OCR'd, and yielded before the next one loads. Byte buffers are explicitly deleted after each image.

This means a 500-page PDF with hundreds of images will use roughly the same peak memory as processing a single page.

---

## Dependencies

| Package | Purpose |
|---|---|
| `click` | CLI framework |
| `python-magic` | libmagic bindings for MIME detection |
| `pdfplumber` | PDF text extraction |
| `PyMuPDF` (fitz) | PDF image extraction |
| `pytesseract` | Python wrapper for Tesseract OCR |
| `Pillow` | Image loading / conversion |
