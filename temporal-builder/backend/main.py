"""
Temporal Builder API
Accepts a YAML interface definition → generates Temporal workers & clients.
"""

import os
import uuid
import shutil
import yaml
import json
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from jinja2 import Environment, FileSystemLoader

# ── Config ────────────────────────────────────────────────────
GENERATED_DIR = Path(os.getenv("GENERATED_DIR", "/app/generated"))
GENERATED_DIR.mkdir(parents=True, exist_ok=True)
TEMPLATES_DIR = Path(__file__).parent / "templates"

app = FastAPI(title="Temporal Builder API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

jinja_env = Environment(
    loader=FileSystemLoader(str(TEMPLATES_DIR)),
    trim_blocks=True,
    lstrip_blocks=True,
)


# ── Helpers ───────────────────────────────────────────────────

def pascal_to_snake(name: str) -> str:
    import re
    s = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name)
    return s.lower().replace("-", "_")


def snake_to_pascal(name: str) -> str:
    return "".join(w.capitalize() for w in name.split("_"))


PYTHON_TYPE_MAP = {
    "string": "str",
    "int": "int",
    "float": "float",
    "bool": "bool",
    "datetime": "datetime",
    "duration": "str",
    "null": "None",
    "any": "Any",
}


def resolve_type(t: str) -> str:
    """Map schema type expressions → Python type hints."""
    if t in PYTHON_TYPE_MAP:
        return PYTHON_TYPE_MAP[t]
    if t.startswith("list<"):
        inner = t[5:-1]
        return f"list[{resolve_type(inner)}]"
    if t.startswith("map<"):
        inner = t[4:-1]
        parts = [p.strip() for p in inner.split(",", 1)]
        return f"dict[{resolve_type(parts[0])}, {resolve_type(parts[1])}]"
    if t.startswith("optional<"):
        inner = t[9:-1]
        return f"Optional[{resolve_type(inner)}]"
    return t  # Custom type reference


def parse_duration_to_seconds(dur: str) -> str:
    """Convert '30s', '5m', '2h', '24h' → timedelta expression."""
    if not dur:
        return "None"
    val = dur.strip()
    if val.endswith("ms"):
        return f"timedelta(milliseconds={val[:-2]})"
    if val.endswith("s"):
        return f"timedelta(seconds={val[:-1]})"
    if val.endswith("m"):
        return f"timedelta(minutes={val[:-1]})"
    if val.endswith("h"):
        return f"timedelta(hours={val[:-1]})"
    if val.endswith("d"):
        return f"timedelta(days={val[:-1]})"
    return f"timedelta(seconds={val})"


# ── Server-side Validation ─────────────────────────────────

import re

def validate_config(config: dict) -> list[str]:
    """Validate the YAML config and return a list of error messages."""
    errors = []
    meta = config.get("metadata", {})

    if not meta.get("name"):
        errors.append("metadata.name is required")
    if not meta.get("namespace"):
        errors.append("metadata.namespace is required")
    if not meta.get("default_task_queue"):
        errors.append("metadata.default_task_queue is required")

    # Validate activities
    act_names = set()
    for act in config.get("activities", []):
        name = act.get("name", "")
        if not name:
            errors.append("Activity missing name")
        elif name in act_names:
            errors.append(f"Duplicate activity name: {name}")
        else:
            act_names.add(name)

        mode = act.get("mode", "sync")
        if mode not in ("sync", "async"):
            errors.append(f"Activity '{name}': invalid mode '{mode}'")

        timeout = act.get("start_to_close_timeout", "")
        if timeout and not re.match(r'^\d+(ms|s|m|h|d)$', timeout):
            errors.append(f"Activity '{name}': invalid timeout format '{timeout}'")

    # Validate workflows
    wf_names = set()
    for wf in config.get("workflows", []):
        name = wf.get("name", "")
        if not name:
            errors.append("Workflow missing name")
        elif name in wf_names:
            errors.append(f"Duplicate workflow name: {name}")
        else:
            wf_names.add(name)

        if wf.get("mode") == "cron" and not wf.get("cron_schedule"):
            errors.append(f"Workflow '{name}': cron mode requires cron_schedule")

        for step in wf.get("steps", []):
            if not step.get("id"):
                errors.append(f"Workflow '{name}': step missing id")
            if step.get("kind") == "activity":
                ref = step.get("activity", "")
                if ref and ref not in act_names:
                    errors.append(f"Workflow '{name}': step references undefined activity '{ref}'")

    # Validate workers
    for w in config.get("workers", []):
        if not w.get("name"):
            errors.append("Worker missing name")
        if not w.get("task_queue"):
            errors.append(f"Worker '{w.get('name', '?')}': task_queue is required")

    # Validate clients
    for c in config.get("clients", []):
        if not c.get("name"):
            errors.append("Client missing name")
        if not c.get("target"):
            errors.append(f"Client '{c.get('name', '?')}': target is required")

    return errors


# ── Routes ────────────────────────────────────────────────────

@app.get("/api/health")
async def health():
    return {"status": "ok", "timestamp": datetime.utcnow().isoformat()}


@app.post("/api/generate")
async def generate(request: Request):
    """
    Accept a YAML config body, generate Temporal worker + client code,
    store in a project folder, return metadata + download links.
    """
    body = await request.body()
    try:
        config = yaml.safe_load(body.decode("utf-8"))
    except yaml.YAMLError as e:
        raise HTTPException(status_code=400, detail=f"Invalid YAML: {e}")

    if not config:
        raise HTTPException(status_code=400, detail="Empty config")

    # ── Server-side validation ───────────────────────────────
    errors = validate_config(config)
    if errors:
        raise HTTPException(status_code=422, detail=f"Validation failed: {'; '.join(errors)}")

    # ── Create project folder ────────────────────────────────
    project_id = datetime.utcnow().strftime("%Y%m%d_%H%M%S") + "_" + uuid.uuid4().hex[:8]
    project_name = config.get("metadata", {}).get("name", "untitled")
    project_dir = GENERATED_DIR / project_id
    project_dir.mkdir(parents=True, exist_ok=True)

    # Save the source YAML
    (project_dir / "config.yaml").write_text(yaml.dump(config, default_flow_style=False))

    generated_files = []

    try:
        # ── 1. Generate dataclass types ──────────────────────
        types_list = config.get("types", [])
        if types_list:
            code = generate_types(types_list)
            fpath = project_dir / "types.py"
            fpath.write_text(code)
            generated_files.append({"name": "types.py", "kind": "types", "path": str(fpath)})

        # ── 2. Generate activities ───────────────────────────
        activities = config.get("activities", [])
        if activities:
            code = generate_activities(activities, config)
            fpath = project_dir / "activities.py"
            fpath.write_text(code)
            generated_files.append({"name": "activities.py", "kind": "activities", "path": str(fpath)})

        # ── 3. Generate workflows ────────────────────────────
        workflows = config.get("workflows", [])
        if workflows:
            code = generate_workflows(workflows, config)
            fpath = project_dir / "workflows.py"
            fpath.write_text(code)
            generated_files.append({"name": "workflows.py", "kind": "workflows", "path": str(fpath)})

        # ── 4. Generate workers ──────────────────────────────
        workers = config.get("workers", [])
        for w in workers:
            code = generate_worker(w, config)
            fname = f"worker_{pascal_to_snake(w['name']).replace('-','_')}.py"
            fpath = project_dir / fname
            fpath.write_text(code)
            generated_files.append({"name": fname, "kind": "worker", "path": str(fpath)})

        # ── 5. Generate clients ──────────────────────────────
        clients = config.get("clients", [])
        for c in clients:
            code = generate_client(c, config)
            fname = f"client_{pascal_to_snake(c['name']).replace('-','_')}.py"
            fpath = project_dir / fname
            fpath.write_text(code)
            generated_files.append({"name": fname, "kind": "client", "path": str(fpath)})

        # ── 6. Generate requirements.txt ─────────────────────
        req = "temporalio>=1.7.0\npydantic>=2.0\npyyaml>=6.0\n"
        (project_dir / "requirements.txt").write_text(req)
        generated_files.append({"name": "requirements.txt", "kind": "config", "path": str(project_dir / "requirements.txt")})

        # ── 7. Generate docker-compose for the temporal app ──
        dc = generate_app_docker_compose(workers, project_name)
        (project_dir / "docker-compose.yml").write_text(dc)
        generated_files.append({"name": "docker-compose.yml", "kind": "config", "path": str(project_dir / "docker-compose.yml")})

        # ── 8. Generate Dockerfile ───────────────────────────
        df = generate_app_dockerfile()
        (project_dir / "Dockerfile").write_text(df)
        generated_files.append({"name": "Dockerfile", "kind": "config", "path": str(project_dir / "Dockerfile")})

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Generation failed: {e}")

    return JSONResponse({
        "project_id": project_id,
        "project_name": project_name,
        "files": [
            {
                "name": f["name"],
                "kind": f["kind"],
                "download_url": f"/api/projects/{project_id}/files/{f['name']}",
            }
            for f in generated_files
        ],
        "download_all_url": f"/api/projects/{project_id}/download",
        "created_at": datetime.utcnow().isoformat(),
    })


@app.get("/api/projects")
async def list_projects():
    """List all generated projects."""
    projects = []
    if GENERATED_DIR.exists():
        for d in sorted(GENERATED_DIR.iterdir(), reverse=True):
            if d.is_dir():
                config_path = d / "config.yaml"
                name = d.name
                project_name = name
                if config_path.exists():
                    try:
                        c = yaml.safe_load(config_path.read_text())
                        project_name = c.get("metadata", {}).get("name", name)
                    except Exception:
                        pass
                files = [f.name for f in d.iterdir() if f.is_file()]
                projects.append({
                    "project_id": d.name,
                    "project_name": project_name,
                    "files": files,
                    "download_url": f"/api/projects/{d.name}/download",
                })
    return {"projects": projects}


@app.get("/api/projects/{project_id}/files/{filename}")
async def download_file(project_id: str, filename: str):
    """Download a single generated file."""
    fpath = GENERATED_DIR / project_id / filename
    if not fpath.exists():
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(fpath, filename=filename)


@app.get("/api/projects/{project_id}/download")
async def download_project(project_id: str):
    """Download entire project as zip."""
    project_dir = GENERATED_DIR / project_id
    if not project_dir.exists():
        raise HTTPException(status_code=404, detail="Project not found")
    zip_path = GENERATED_DIR / f"{project_id}"
    shutil.make_archive(str(zip_path), "zip", str(project_dir))
    return FileResponse(f"{zip_path}.zip", filename=f"{project_id}.zip", media_type="application/zip")


# ══════════════════════════════════════════════════════════════
#  CODE GENERATION FUNCTIONS
# ══════════════════════════════════════════════════════════════

def generate_types(types_list: list) -> str:
    lines = [
        '"""Auto-generated Temporal type definitions."""',
        "",
        "from __future__ import annotations",
        "from dataclasses import dataclass, field",
        "from typing import Any, Optional",
        "from datetime import datetime",
        "from enum import Enum",
        "",
    ]

    for t in types_list:
        kind = t.get("kind", "struct")
        name = t["name"]

        if kind == "enum":
            lines.append(f"class {name}(str, Enum):")
            desc = t.get("description")
            if desc:
                lines.append(f'    """{desc}"""')
            for v in t.get("values", []):
                lines.append(f'    {v["name"]} = "{v.get("value", v["name"])}"')
            lines.append("")

        elif kind == "alias":
            alias_of = resolve_type(t.get("alias_of", "Any"))
            lines.append(f"{name} = {alias_of}")
            lines.append("")

        else:  # struct
            lines.append("@dataclass")
            lines.append(f"class {name}:")
            desc = t.get("description")
            if desc:
                lines.append(f'    """{desc}"""')
            fields = t.get("fields", [])
            # Required fields first, then optional
            required = [f for f in fields if f.get("required", True)]
            optional = [f for f in fields if not f.get("required", True)]
            for fld in required:
                py_type = resolve_type(fld["type"])
                lines.append(f'    {fld["name"]}: {py_type}')
            for fld in optional:
                py_type = resolve_type(fld["type"])
                default = fld.get("default")
                if default is not None:
                    lines.append(f'    {fld["name"]}: {py_type} = {repr(default)}')
                else:
                    lines.append(f'    {fld["name"]}: Optional[{py_type}] = None')
            if not fields:
                lines.append("    pass")
            lines.append("")

    return "\n".join(lines)


def generate_activities(activities: list, config: dict) -> str:
    retry_policies = {r["name"]: r for r in config.get("retry_policies", [])}

    lines = [
        '"""Auto-generated Temporal activity stubs."""',
        "",
        "from __future__ import annotations",
        "from datetime import timedelta",
        "from typing import Any",
        "from temporalio import activity",
        "from temporalio.common import RetryPolicy",
        "",
        "# Import generated types",
        "try:",
        "    from .types import *",
        "except ImportError:",
        "    from types import *",
        "",
    ]

    # Generate retry policy constants
    for rp_name, rp in retry_policies.items():
        var_name = rp_name.upper().replace("-", "_") + "_RETRY"
        lines.append(f"{var_name} = RetryPolicy(")
        if rp.get("initial_interval"):
            lines.append(f"    initial_interval={parse_duration_to_seconds(rp['initial_interval'])},")
        if rp.get("backoff_coefficient"):
            lines.append(f"    backoff_coefficient={rp['backoff_coefficient']},")
        if rp.get("max_interval"):
            lines.append(f"    maximum_interval={parse_duration_to_seconds(rp['max_interval'])},")
        if rp.get("max_attempts"):
            lines.append(f"    maximum_attempts={rp['max_attempts']},")
        nre = rp.get("non_retryable_error_types", [])
        if nre:
            lines.append(f"    non_retryable_error_types={nre},")
        lines.append(")")
        lines.append("")

    # Generate activity functions
    for act in activities:
        name = act["name"]
        mode = act.get("mode", "sync")
        desc = act.get("description", "")
        in_type = resolve_type(act.get("input", {}).get("type", "Any"))
        out_type = resolve_type(act.get("output", {}).get("type", "Any"))

        lines.append("")
        lines.append(f"@activity.defn")
        if mode == "async":
            lines.append(f"async def {name}(input: {in_type}) -> {out_type}:")
        else:
            lines.append(f"async def {name}(input: {in_type}) -> {out_type}:")

        lines.append(f'    """')
        lines.append(f"    {desc}")
        lines.append(f"    Mode: {mode}")
        if mode == "async":
            lines.append(f"    NOTE: This is an async activity — heartbeat regularly.")
        lines.append(f'    """')

        if mode == "async":
            lines.append(f"    # Heartbeat periodically for long-running work")
            lines.append(f"    activity.heartbeat('started')")
            lines.append(f"")

        lines.append(f"    # ╔══════════════════════════════════════════╗")
        lines.append(f"    # ║  TODO: Implement {name}")
        lines.append(f"    # ╚══════════════════════════════════════════╝")
        lines.append(f"    raise NotImplementedError('{name} not yet implemented')")
        lines.append("")

    return "\n".join(lines)


def generate_workflows(workflows: list, config: dict) -> str:
    lines = [
        '"""Auto-generated Temporal workflow definitions."""',
        "",
        "from __future__ import annotations",
        "from datetime import timedelta",
        "from typing import Any, Optional",
        "from temporalio import workflow",
        "from temporalio.common import RetryPolicy",
        "",
        "# Import activities for type-safe execution",
        "with workflow.unsafe.imports_passed_through():",
        "    try:",
        "        from .activities import *",
        "        from .types import *",
        "    except ImportError:",
        "        from activities import *",
        "        from types import *",
        "",
    ]

    activities_map = {a["name"]: a for a in config.get("activities", [])}
    signals_map = {s["name"]: s for s in config.get("signals", [])}
    queries_map = {q["name"]: q for q in config.get("queries", [])}
    updates_map = {u["name"]: u for u in config.get("updates", [])}
    default_tq = config.get("metadata", {}).get("default_task_queue", "default")

    for wf in workflows:
        wf_name = wf["name"]
        mode = wf.get("mode", "async")
        in_type = resolve_type(wf.get("input", {}).get("type", "Any"))
        out_type = resolve_type(wf.get("output", {}).get("type", "Any"))
        desc = wf.get("description", "")

        lines.append("")
        lines.append(f"@workflow.defn")
        lines.append(f"class {wf_name}:")
        lines.append(f'    """')
        lines.append(f"    {desc}")
        lines.append(f"    Mode: {mode}")
        if mode == "cron":
            lines.append(f"    Cron: {wf.get('cron_schedule', '')}")
        lines.append(f'    """')
        lines.append(f"")

        # Init
        lines.append(f"    def __init__(self) -> None:")
        lines.append(f"        self._status: str = 'pending'")
        lines.append(f"        self._result: Optional[Any] = None")
        lines.append(f"")

        # Run method
        lines.append(f"    @workflow.run")
        lines.append(f"    async def run(self, input: {in_type}) -> {out_type}:")

        # Generate step execution code
        steps = wf.get("steps", [])
        if steps:
            for step in steps:
                sid = step["id"]
                kind = step.get("kind", "activity")
                dep = step.get("depends_on", [])

                lines.append(f"")
                lines.append(f"        # ── Step: {sid} ({kind}) ──")

                if kind == "activity":
                    act_name = step.get("activity", sid)
                    act_def = activities_map.get(act_name, {})
                    out_var = step.get("output_var", f"{sid}_result")
                    stc = act_def.get("start_to_close_timeout", "60s")
                    hb = act_def.get("heartbeat_timeout")
                    tq = act_def.get("task_queue")

                    lines.append(f"        {out_var} = await workflow.execute_activity(")
                    lines.append(f"            {act_name},")
                    lines.append(f"            input,")
                    lines.append(f"            start_to_close_timeout={parse_duration_to_seconds(stc)},")
                    if hb:
                        lines.append(f"            heartbeat_timeout={parse_duration_to_seconds(hb)},")
                    if tq:
                        lines.append(f'            task_queue="{tq}",')
                    lines.append(f"        )")

                    # Error handling
                    on_err = step.get("on_error", {})
                    if on_err.get("strategy") == "fallback":
                        fb = on_err.get("fallback_step", "")
                        lines.append(f"        # On error → fallback to step '{fb}'")

                elif kind == "child_workflow":
                    child_wf = step.get("workflow", sid)
                    out_var = step.get("output_var", f"{sid}_result")
                    lines.append(f"        {out_var} = await workflow.execute_child_workflow(")
                    lines.append(f"            {child_wf}.run,")
                    lines.append(f"            input,")
                    lines.append(f'            id=f"{step.get("child_id_pattern", child_wf + "-{{}}")}",')
                    lines.append(f"        )")

                elif kind == "timer":
                    dur = step.get("duration", "1m")
                    lines.append(f"        await workflow.sleep({parse_duration_to_seconds(dur)})")

                elif kind == "local_activity":
                    fn_name = step.get("local_activity_fn", sid)
                    out_var = step.get("output_var", f"{sid}_result")
                    lines.append(f"        {out_var} = await workflow.execute_local_activity(")
                    lines.append(f"            {fn_name},")
                    lines.append(f"            input,")
                    lines.append(f"            start_to_close_timeout=timedelta(seconds=30),")
                    lines.append(f"        )")

                elif kind == "parallel":
                    lines.append(f"        # Parallel execution")
                    lines.append(f"        # TODO: wire branches for step '{sid}'")
                    lines.append(f"        pass")

                elif kind == "condition":
                    expr = step.get("expression", "True")
                    lines.append(f"        if {expr}:")
                    lines.append(f"            pass  # then_steps")
                    lines.append(f"        else:")
                    lines.append(f"            pass  # else_steps")

                elif kind == "signal_wait":
                    sig = step.get("signal", "")
                    out_var = step.get("output_var", f"{sid}_result")
                    lines.append(f"        {out_var} = await workflow.wait_condition(")
                    lines.append(f"            lambda: self._{sig}_received is not None")
                    lines.append(f"        )")

                elif kind == "continue_as_new":
                    lines.append(f"        workflow.continue_as_new(input)")
                    lines.append(f"        return  # unreachable")

            lines.append(f"")
            lines.append(f"        self._status = 'completed'")
            lines.append(f"        return self._result")
        else:
            lines.append(f"        # No steps defined — implement workflow logic here")
            lines.append(f"        raise NotImplementedError('{wf_name} not yet implemented')")

        # Signal handlers
        for sh in wf.get("signal_handlers", []):
            sig_name = sh.get("signal", "")
            sig_def = signals_map.get(sig_name, {})
            payload_type = resolve_type(sig_def.get("payload", {}).get("type", "Any"))
            lines.append(f"")
            lines.append(f"    @workflow.signal")
            lines.append(f"    async def {sig_name}(self, payload: {payload_type}) -> None:")
            lines.append(f'        """Handle signal: {sig_name}"""')
            lines.append(f"        self._{sig_name}_received = payload")

        # Query handlers
        for qh in wf.get("query_handlers", []):
            q_name = qh.get("query", "")
            q_def = queries_map.get(q_name, {})
            out_type_q = resolve_type(q_def.get("output", {}).get("type", "str"))
            lines.append(f"")
            lines.append(f"    @workflow.query")
            lines.append(f"    def {q_name}(self) -> {out_type_q}:")
            lines.append(f'        """Query handler: {q_name}"""')
            lines.append(f"        return self._status")

        # Update handlers
        for uh in wf.get("update_handlers", []):
            u_name = uh.get("update", "")
            u_def = updates_map.get(u_name, {})
            u_in = resolve_type(u_def.get("input", {}).get("type", "Any"))
            u_out = resolve_type(u_def.get("output", {}).get("type", "Any"))
            lines.append(f"")
            lines.append(f"    @workflow.update")
            lines.append(f"    async def {u_name}(self, input: {u_in}) -> {u_out}:")
            lines.append(f'        """Update handler: {u_name}"""')
            lines.append(f"        raise NotImplementedError()")
            if u_def.get("has_validator"):
                lines.append(f"")
                lines.append(f"    @{u_name}.validator")
                lines.append(f"    def validate_{u_name}(self, input: {u_in}) -> None:")
                lines.append(f"        # Raise ApplicationError to reject the update")
                lines.append(f"        pass")

        lines.append("")

    return "\n".join(lines)


def generate_worker(worker: dict, config: dict) -> str:
    name = worker["name"]
    tq = worker.get("task_queue", config.get("metadata", {}).get("default_task_queue", "default"))
    ns = worker.get("namespace", config.get("metadata", {}).get("namespace", "default"))
    act_list = worker.get("activities", [])
    wf_list = worker.get("workflows", [])
    max_ca = worker.get("max_concurrent_activities", 200)
    max_cwt = worker.get("max_concurrent_workflow_tasks", 200)
    shutdown = worker.get("graceful_shutdown_timeout", "30s")

    lines = [
        f'"""Auto-generated Temporal worker: {name}"""',
        "",
        "import asyncio",
        "import signal",
        "import logging",
        "from datetime import timedelta",
        "from temporalio.client import Client",
        "from temporalio.worker import Worker",
        "",
        "# Import activities & workflows",
        "try:",
        "    from .activities import (",
    ]
    for a in act_list:
        lines.append(f"        {a},")
    lines.append("    )")
    lines.append("    from .workflows import (")
    for w in wf_list:
        lines.append(f"        {w},")
    lines.append("    )")
    lines.append("except ImportError:")
    lines.append("    from activities import (")
    for a in act_list:
        lines.append(f"        {a},")
    lines.append("    )")
    lines.append("    from workflows import (")
    for w in wf_list:
        lines.append(f"        {w},")
    lines.append("    )")
    lines.append("")

    lines.append(f'TASK_QUEUE = "{tq}"')
    lines.append(f'NAMESPACE = "{ns}"')
    lines.append(f"MAX_CONCURRENT_ACTIVITIES = {max_ca}")
    lines.append(f"MAX_CONCURRENT_WORKFLOW_TASKS = {max_cwt}")
    lines.append("")
    lines.append("logger = logging.getLogger(__name__)")
    lines.append("")
    lines.append("")
    lines.append("async def run_worker():")
    lines.append(f'    """Start the {name} worker."""')
    lines.append("    logger.info(f'Connecting to Temporal at localhost:7233, namespace={NAMESPACE}')")
    lines.append("    client = await Client.connect(")
    lines.append('        "localhost:7233",')
    lines.append("        namespace=NAMESPACE,")
    lines.append("    )")
    lines.append("")
    lines.append("    worker = Worker(")
    lines.append("        client,")
    lines.append("        task_queue=TASK_QUEUE,")
    lines.append("        activities=[")
    for a in act_list:
        lines.append(f"            {a},")
    lines.append("        ],")
    lines.append("        workflows=[")
    for w in wf_list:
        lines.append(f"            {w},")
    lines.append("        ],")
    lines.append(f"        max_concurrent_activities=MAX_CONCURRENT_ACTIVITIES,")
    lines.append(f"        max_concurrent_workflow_tasks=MAX_CONCURRENT_WORKFLOW_TASKS,")
    lines.append("    )")
    lines.append("")
    lines.append(f"    logger.info(f'Worker \"{name}\" started on queue={{TASK_QUEUE}}')")

    # Graceful shutdown
    lines.append("")
    lines.append("    shutdown_event = asyncio.Event()")
    lines.append("")
    lines.append("    def _signal_handler():")
    lines.append("        logger.info('Shutdown signal received')")
    lines.append("        shutdown_event.set()")
    lines.append("")
    lines.append("    loop = asyncio.get_event_loop()")
    lines.append("    for sig in (signal.SIGINT, signal.SIGTERM):")
    lines.append("        loop.add_signal_handler(sig, _signal_handler)")
    lines.append("")
    lines.append("    async with worker:")
    lines.append("        await shutdown_event.wait()")
    lines.append(f"        logger.info('Draining in-flight work (timeout: {shutdown})')")
    lines.append("")
    lines.append("")
    lines.append('if __name__ == "__main__":')
    lines.append("    logging.basicConfig(level=logging.INFO)")
    lines.append("    asyncio.run(run_worker())")
    lines.append("")

    return "\n".join(lines)


def generate_client(client_cfg: dict, config: dict) -> str:
    name = client_cfg["name"]
    ns = client_cfg.get("namespace", config.get("metadata", {}).get("namespace", "default"))
    target = client_cfg.get("target", "localhost:7233")
    allowed_wfs = client_cfg.get("allowed_workflows", [])
    allowed_sigs = client_cfg.get("allowed_signals", [])
    allowed_qs = client_cfg.get("allowed_queries", [])
    allowed_ups = client_cfg.get("allowed_updates", [])
    default_mode = client_cfg.get("default_mode", "async")
    tls = client_cfg.get("tls", {})
    rpc_timeout = client_cfg.get("rpc_timeout", "10s")

    workflows_map = {w["name"]: w for w in config.get("workflows", [])}
    signals_map = {s["name"]: s for s in config.get("signals", [])}

    lines = [
        f'"""Auto-generated Temporal client: {name}"""',
        "",
        "from __future__ import annotations",
        "import asyncio",
        "import logging",
        "from datetime import timedelta",
        "from typing import Any, Optional",
        "from temporalio.client import Client, TLSConfig, WorkflowHandle",
        "",
        "# Import types & workflow classes",
        "try:",
        "    from .types import *",
        "    from .workflows import (",
    ]
    for w in allowed_wfs:
        lines.append(f"        {w},")
    lines.append("    )")
    lines.append("except ImportError:")
    lines.append("    from types import *")
    lines.append("    from workflows import (")
    for w in allowed_wfs:
        lines.append(f"        {w},")
    lines.append("    )")
    lines.append("")

    lines.append("logger = logging.getLogger(__name__)")
    lines.append("")
    lines.append("")
    lines.append(f"class {snake_to_pascal(name.replace('-', '_'))}Client:")
    lines.append(f'    """')
    lines.append(f"    Typed Temporal client: {name}")
    lines.append(f"    Default mode: {default_mode}")
    lines.append(f"    Allowed workflows: {', '.join(allowed_wfs)}")
    lines.append(f'    """')
    lines.append("")

    # Init / connect
    lines.append("    def __init__(self, client: Client):")
    lines.append("        self._client = client")
    lines.append("")
    lines.append("    @classmethod")
    lines.append("    async def connect(cls) -> '{snake_to_pascal(name.replace('-', '_'))}Client':")
    lines.append(f'        """Connect to Temporal server."""')

    if tls.get("enabled"):
        lines.append("        tls_config = TLSConfig(")
        if tls.get("cert_path"):
            lines.append(f'            client_cert=open("{tls["cert_path"]}", "rb").read(),')
        if tls.get("key_path"):
            lines.append(f'            client_private_key=open("{tls["key_path"]}", "rb").read(),')
        if tls.get("ca_path"):
            lines.append(f'            server_root_ca_cert=open("{tls["ca_path"]}", "rb").read(),')
        lines.append("        )")
        lines.append("        client = await Client.connect(")
        lines.append(f'            "{target}",')
        lines.append(f'            namespace="{ns}",')
        lines.append("            tls=tls_config,")
        lines.append("        )")
    else:
        lines.append("        client = await Client.connect(")
        lines.append(f'            "{target}",')
        lines.append(f'            namespace="{ns}",')
        lines.append("        )")

    lines.append("        return cls(client)")
    lines.append("")

    # Generate workflow methods
    for wf_name in allowed_wfs:
        wf_def = workflows_map.get(wf_name, {})
        in_type = resolve_type(wf_def.get("input", {}).get("type", "Any"))
        out_type = resolve_type(wf_def.get("output", {}).get("type", "Any"))
        id_pattern = wf_def.get("id_pattern", f"{wf_name}-{{{{id}}}}")
        tq = wf_def.get("task_queue", config.get("metadata", {}).get("default_task_queue", "default"))

        # Start (async) method
        lines.append(f"    async def start_{pascal_to_snake(wf_name)}(")
        lines.append(f"        self,")
        lines.append(f"        input: {in_type},")
        lines.append(f"        workflow_id: str,")
        lines.append(f"    ) -> WorkflowHandle:")
        lines.append(f'        """Start {wf_name} — returns handle (non-blocking)."""')
        lines.append(f"        return await self._client.start_workflow(")
        lines.append(f"            {wf_name}.run,")
        lines.append(f"            input,")
        lines.append(f"            id=workflow_id,")
        lines.append(f'            task_queue="{tq}",')
        lines.append(f"        )")
        lines.append("")

        # Execute (sync) method
        lines.append(f"    async def execute_{pascal_to_snake(wf_name)}(")
        lines.append(f"        self,")
        lines.append(f"        input: {in_type},")
        lines.append(f"        workflow_id: str,")
        lines.append(f"    ) -> {out_type}:")
        lines.append(f'        """Execute {wf_name} — blocks until result."""')
        lines.append(f"        return await self._client.execute_workflow(")
        lines.append(f"            {wf_name}.run,")
        lines.append(f"            input,")
        lines.append(f"            id=workflow_id,")
        lines.append(f'            task_queue="{tq}",')
        lines.append(f"        )")
        lines.append("")

    # Signal methods
    for sig_name in allowed_sigs:
        sig_def = signals_map.get(sig_name, {})
        payload_type = resolve_type(sig_def.get("payload", {}).get("type", "Any"))
        lines.append(f"    async def signal_{sig_name}(")
        lines.append(f"        self, workflow_id: str, payload: {payload_type}")
        lines.append(f"    ) -> None:")
        lines.append(f'        """Send signal: {sig_name}"""')
        lines.append(f"        handle = self._client.get_workflow_handle(workflow_id)")
        lines.append(f"        await handle.signal('{sig_name}', payload)")
        lines.append("")

    # Query methods
    for q_name in allowed_qs:
        lines.append(f"    async def query_{q_name}(self, workflow_id: str) -> Any:")
        lines.append(f'        """Query: {q_name}"""')
        lines.append(f"        handle = self._client.get_workflow_handle(workflow_id)")
        lines.append(f"        return await handle.query('{q_name}')")
        lines.append("")

    return "\n".join(lines)


def generate_app_docker_compose(workers: list, project_name: str) -> str:
    lines = [
        f"# Auto-generated docker-compose for {project_name}",
        'version: "3.9"',
        "",
        "services:",
    ]
    for w in workers:
        svc = w["name"]
        fname = f"worker_{pascal_to_snake(svc).replace('-','_')}.py"
        replicas = w.get("runtime", {}).get("replicas", 1)
        cpu = w.get("runtime", {}).get("cpu", "500m")
        mem = w.get("runtime", {}).get("memory", "512Mi")
        lines.append(f"  {svc}:")
        lines.append(f"    build: .")
        lines.append(f'    command: python {fname}')
        lines.append(f"    deploy:")
        lines.append(f"      replicas: {replicas}")
        lines.append(f"      resources:")
        lines.append(f"        limits:")
        lines.append(f"          cpus: '{cpu}'")
        lines.append(f"          memory: {mem}")
        env_vars = w.get("runtime", {}).get("env", {})
        if env_vars:
            lines.append(f"    environment:")
            for k, v in env_vars.items():
                lines.append(f'      - {k}={v}')
        lines.append(f"    restart: unless-stopped")
        lines.append("")
    return "\n".join(lines)


def generate_app_dockerfile() -> str:
    return """FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
"""
