#!/usr/bin/env python3
"""Validate that engineering iterations preserve history and declare what follows."""

from __future__ import annotations

import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DOCS_ROOT = PROJECT_ROOT / "docs"
ITERATIONS_ROOT = DOCS_ROOT / "iterations"
HISTORY_ROOT = ITERATIONS_ROOT / "history"
ROADMAP = DOCS_ROOT / "ROADMAP_ENGINEERING.md"
CURRENT = ITERATIONS_ROOT / "CURRENT.md"
TEMPLATE = ITERATIONS_ROOT / "ITERATION_TEMPLATE.md"

HISTORY_REQUIRED = (
    "## Identificación",
    "## Objetivo",
    "## Alcance ejecutado",
    "## Cambios y decisiones",
    "## Evidencia y validación",
    "## Resultados",
    "## Fallos, riesgos y deuda",
    "## Estado del roadmap",
    "## Siguiente iteración obligatoria",
    "## Criterios de entrada y salida",
)

CURRENT_REQUIRED = (
    "## Estado acumulado",
    "## Iteración actual o última cerrada",
    "## Bloqueos activos",
    "## Siguiente iteración obligatoria",
    "## Criterios de entrada",
    "## Criterios de salida",
)

ROADMAP_REQUIRED = (
    "## 3. Regla dimensional de identificación",
    "## 6. Fases del roadmap",
    "## 7. Protocolo obligatorio de iteraciones",
    "## 9. Próxima iteración obligatoria",
)


def read_required(path: Path, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"missing required file: {path.relative_to(PROJECT_ROOT)}")
        return ""
    text = path.read_text(encoding="utf-8")
    if not text.strip():
        errors.append(f"empty required file: {path.relative_to(PROJECT_ROOT)}")
    return text


def require_sections(path: Path, text: str, sections: tuple[str, ...], errors: list[str]) -> None:
    for section in sections:
        if section not in text:
            errors.append(f"{path.relative_to(PROJECT_ROOT)} missing section: {section}")


def main() -> int:
    errors: list[str] = []

    roadmap_text = read_required(ROADMAP, errors)
    current_text = read_required(CURRENT, errors)
    template_text = read_required(TEMPLATE, errors)

    require_sections(ROADMAP, roadmap_text, ROADMAP_REQUIRED, errors)
    require_sections(CURRENT, current_text, CURRENT_REQUIRED, errors)
    require_sections(TEMPLATE, template_text, HISTORY_REQUIRED, errors)

    history_files = sorted(HISTORY_ROOT.glob("ITER-*.md")) if HISTORY_ROOT.is_dir() else []
    if not history_files:
        errors.append("no immutable iteration records found under docs/iterations/history")

    iteration_numbers: list[int] = []
    for path in history_files:
        match = re.match(r"ITER-(\d{3})_\d{4}-\d{2}-\d{2}_[a-z0-9-]+\.md$", path.name)
        if not match:
            errors.append(f"invalid iteration filename: {path.name}")
            continue
        iteration_numbers.append(int(match.group(1)))
        text = read_required(path, errors)
        require_sections(path, text, HISTORY_REQUIRED, errors)
        if "NO EJECUTADA" not in text and "PASS" not in text and "APROB" not in text:
            errors.append(f"{path.name} does not state validation outcomes")
        if re.search(r"Siguiente iteración obligatoria\s*$", text, re.MULTILINE):
            errors.append(f"{path.name} declares no concrete next iteration")

    if len(iteration_numbers) != len(set(iteration_numbers)):
        errors.append("duplicate iteration numbers detected")
    if iteration_numbers != sorted(iteration_numbers):
        errors.append("iteration history is not ordered")

    if history_files:
        latest = history_files[-1].name
        if latest not in current_text:
            errors.append(f"CURRENT.md does not reference latest history record: {latest}")

    next_iteration = re.search(r"###\s+(ITER-\d{3})\s+—\s+(.+)", current_text)
    if not next_iteration:
        errors.append("CURRENT.md must name the next iteration as 'ITER-NNN — title'")
    elif next_iteration.group(1) in {f"ITER-{number:03d}" for number in iteration_numbers}:
        errors.append("CURRENT.md next iteration must not reuse a closed iteration number")

    if "error_radio_mm <= 30" not in roadmap_text and "error_radio_mm` <= 30" not in roadmap_text:
        errors.append("roadmap does not preserve the 30 mm radius acceptance rule")

    if errors:
        print("Iteration history validation FAILED:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Iteration history validation OK: "
        f"{len(history_files)} closed record(s), next={next_iteration.group(1) if next_iteration else 'unknown'}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
