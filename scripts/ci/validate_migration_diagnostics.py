#!/usr/bin/env python3
"""Validates CI artifacts required for migration diagnostics policy."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class CheckResult:
    name: str
    status: str
    details: str

    @property
    def is_ok(self) -> bool:
        return self.status == "PASS"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate migration diagnostics artifacts.")
    parser.add_argument("--artifacts-dir", required=True, help="Path to downloaded artifacts root.")
    parser.add_argument("--output", required=True, help="Markdown output path.")
    return parser.parse_args()


def _first_match(base_dir: Path, pattern: str) -> Path | None:
    for path in sorted(base_dir.rglob(pattern)):
        if path.is_file():
            return path
    return None


def _file_non_empty(base_dir: Path, pattern: str) -> CheckResult:
    file_path = _first_match(base_dir, pattern)
    if file_path is None:
        return CheckResult(
            name=f"Artifact `{pattern}` exists",
            status="FAIL",
            details="Required artifact is missing.",
        )
    if file_path.stat().st_size == 0:
        return CheckResult(
            name=f"Artifact `{pattern}` is not empty",
            status="FAIL",
            details=f"Found `{file_path}` but it is empty.",
        )
    return CheckResult(
        name=f"Artifact `{pattern}` is not empty",
        status="PASS",
        details=f"Found `{file_path}`.",
    )


def _migration_gate_metadata(base_dir: Path) -> CheckResult:
    metadata_path = _first_match(base_dir, "quality-instrumented-migration/**/gate-instrumented-migration.json")
    if metadata_path is None:
        return CheckResult(
            name="Migration gate metadata present",
            status="FAIL",
            details="`gate-instrumented-migration.json` was not found.",
        )

    try:
        data = json.loads(metadata_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return CheckResult(
            name="Migration gate metadata valid JSON",
            status="FAIL",
            details=f"Invalid JSON in `{metadata_path}`: {exc}",
        )

    status = data.get("status")
    if status != "success":
        return CheckResult(
            name="Migration gate status is successful",
            status="FAIL",
            details=f"Expected status `success`, got `{status}`.",
        )

    return CheckResult(
        name="Migration gate metadata present",
        status="PASS",
        details=f"Found `{metadata_path}` with successful status.",
    )


def run_checks(artifacts_dir: Path) -> list[CheckResult]:
    return [
        _migration_gate_metadata(artifacts_dir),
        _file_non_empty(
            artifacts_dir,
            "quality-instrumented-migration/**/app/build/reports/androidTests/migration-diagnostics.md",
        ),
        _file_non_empty(
            artifacts_dir,
            "quality-instrumented-migration/**/app/build/reports/androidTests/logcat-migration.txt",
        ),
        _file_non_empty(
            artifacts_dir,
            "quality-instrumented-migration/**/app/build/outputs/androidTest-results/**/*.xml",
        ),
    ]


def build_markdown(checks: list[CheckResult]) -> str:
    passed = sum(1 for item in checks if item.is_ok)
    failed = len(checks) - passed

    lines = [
        "## Migration diagnostics validation",
        "",
        "- Scope: Instrumented Migration (Room) artifacts",
        "- Required policy checks ensure logs and reports are published as merge diagnostics",
        f"- Checks passed: **{passed}**",
        f"- Checks failed: **{failed}**",
        "",
        "| Check | Result | Details |",
        "| --- | --- | --- |",
    ]
    for item in checks:
        lines.append(f"| {item.name} | {item.status} | {item.details} |")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    output_path = Path(args.output)

    checks = run_checks(artifacts_dir)
    report = build_markdown(checks)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report, encoding="utf-8")

    if any(not check.is_ok for check in checks):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
