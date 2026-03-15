from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class DocumentResult:
    document_id: str = ""
    original_file_name: str = ""
    text_file_path: str = ""
    status: str = ""
    extracted_char_count: int = 0
    workflow_id: str = ""
