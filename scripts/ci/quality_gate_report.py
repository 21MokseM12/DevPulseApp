#!/usr/bin/env python3
"""Builds a compact Markdown summary for CI quality gates."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class GateResult:
    gate_id: str
    gate_name: str
    gradle_task: str
    merge_policy: str
    retry_enabled: bool
    attempts_used: int
    max_attempts: int
    status: str
    duration_seconds: int

    @property
    def status_icon(self) -> str:
        return "PASS" if self.status == "success" else "FAIL"

    @property
    def retry_note(self) -> str:
        if not self.retry_enabled:
            return "disabled"
        return f"{self.attempts_used}/{self.max_attempts}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate CI quality gate summary.")
    parser.add_argument("--artifacts-dir", required=True, help="Path to downloaded artifacts root")
    parser.add_argument("--matrix-doc", required=True, help="Path to quality matrix documentation file")
    parser.add_argument("--output", required=True, help="Path to markdown output file")
    return parser.parse_args()


def _read_gate_result(file_path: Path) -> GateResult:
    data = json.loads(file_path.read_text(encoding="utf-8"))
    return GateResult(
        gate_id=data["gate_id"],
        gate_name=data["gate_name"],
        gradle_task=data["gradle_task"],
        merge_policy=data["merge_policy"],
        retry_enabled=bool(data["retry_enabled"]),
        attempts_used=int(data["attempts_used"]),
        max_attempts=int(data["max_attempts"]),
        status=data["status"],
        duration_seconds=int(data["duration_seconds"]),
    )


def load_results(artifacts_dir: Path) -> list[GateResult]:
    gate_files = sorted(artifacts_dir.rglob("gate-*.json"))
    return sorted((_read_gate_result(path) for path in gate_files), key=lambda item: item.gate_name.lower())


def _status_summary(results: Iterable[GateResult]) -> tuple[int, int]:
    passed = 0
    failed = 0
    for result in results:
        if result.status == "success":
            passed += 1
        else:
            failed += 1
    return passed, failed


def _policy_lines(matrix_doc: Path) -> list[str]:
    if not matrix_doc.exists():
        return ["- Matrix document missing in workspace checkout."]

    merge_policy_lines: list[str] = []
    capture = False
    for raw_line in matrix_doc.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line == "## Merge policy":
            capture = True
            continue
        if capture and line.startswith("## "):
            break
        if capture and line.startswith("- "):
            merge_policy_lines.append(line)

    if not merge_policy_lines:
        merge_policy_lines.append("- Merge policy section not found in matrix document.")
    return merge_policy_lines


def build_report(results: list[GateResult], matrix_doc: Path) -> str:
    passed, failed = _status_summary(results)

    lines = [
        "## CI quality gates",
        "",
        f"- Gates passed: **{passed}**",
        f"- Gates failed: **{failed}**",
        f"- Matrix doc: `{matrix_doc}`",
        "",
        "| Gate | Gradle task | Policy | Retry | Result | Duration (s) |",
        "| --- | --- | --- | --- | --- | ---: |",
    ]

    for result in results:
        lines.append(
            "| {gate} | `{task}` | {policy} | {retry} | {status} | {duration} |".format(
                gate=result.gate_name,
                task=result.gradle_task,
                policy=result.merge_policy,
                retry=result.retry_note,
                status=result.status_icon,
                duration=result.duration_seconds,
            )
        )

    lines.extend(["", "### Merge policy", ""])
    lines.extend(_policy_lines(matrix_doc))
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    matrix_doc = Path(args.matrix_doc)
    output = Path(args.output)

    results = load_results(artifacts_dir)
    report = build_report(results, matrix_doc)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
