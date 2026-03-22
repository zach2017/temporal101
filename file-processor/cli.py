#!/usr/bin/env python3
"""
file-processor CLI — Detect file types via python-magic (libmagic),
then dispatch to the appropriate handler.

PDF handling uses generators (yield) throughout to keep memory usage
as low as possible when extracting text and OCR-ing images.
"""

import sys
from pathlib import Path

import click

from processor.dispatcher import dispatch_file


@click.command()
@click.argument("filepath", type=click.Path(exists=True, path_type=Path))
@click.option(
    "--output-dir", "-o",
    type=click.Path(path_type=Path),
    default=None,
    help="Directory to write extracted images and text. Defaults to <filepath>_output/",
)
@click.option("--verbose", "-v", is_flag=True, help="Print extra info while processing.")
def main(filepath: Path, output_dir: Path | None, verbose: bool) -> None:
    """Detect MIME type of FILEPATH and process it accordingly.

    Supported types:
      • PDF  — extract text (pdfplumber) + extract & OCR images (Pillow + pytesseract)
      • Images (JPEG/PNG/TIFF/BMP/WEBP) — OCR via pytesseract
      • Plain text / CSV / JSON — echo content summary
    """
    if output_dir is None:
        output_dir = filepath.parent / f"{filepath.stem}_output"

    output_dir.mkdir(parents=True, exist_ok=True)

    click.echo(f"📂 Input : {filepath}")
    click.echo(f"📁 Output: {output_dir}")
    click.echo()

    try:
        for message in dispatch_file(filepath, output_dir, verbose=verbose):
            click.echo(message)
    except Exception as exc:
        click.secho(f"✖ Error: {exc}", fg="red", err=True)
        sys.exit(1)

    click.echo()
    click.secho("✔ Done.", fg="green")


if __name__ == "__main__":
    main()
