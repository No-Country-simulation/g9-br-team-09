"""Testes do relatório reproduzível de diagnósticos da baseline."""

import subprocess
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).parents[2]
SCRIPT_PATH = (
    REPOSITORY_ROOT
    / "data-science"
    / "src"
    / "baseline_diagnostics_report.py"
)


def test_comando_gera_relatorio_markdown_reprodutivel(
    tmp_path: Path,
) -> None:
    output_path = tmp_path / "baseline-diagnostics-report-v2.md"

    completed = subprocess.run(
        [
            sys.executable,
            str(SCRIPT_PATH),
            "--sample-size",
            "200",
            "--seed",
            "42",
            "--n-repeats",
            "2",
            "--output",
            str(output_path),
        ],
        cwd=REPOSITORY_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )

    assert completed.returncode == 0, completed.stderr
    assert output_path.is_file()

    first_content = output_path.read_text(encoding="utf-8")

    assert (
        "# Relatório de diagnósticos da baseline — "
        "Dataset EnergiAI V2"
    ) in first_content
    assert "## Configuração reproduzível" in first_content
    assert "## Benchmark diagnóstico" in first_content
    assert "## Diagnóstico por feature individual" in first_content
    assert "## Diagnóstico por ablação" in first_content
    assert "## Permutation importance" in first_content
    assert "## Consolidação do guardrail" in first_content
    assert "Tamanho da amostra | `200`" in first_content
    assert "Seed | `42`" in first_content
    assert "Repetições da permutação | `2`" in first_content
    assert (
        f'--output "{output_path.as_posix()}"'
        in first_content
    )
    assert (
        "O resultado mede a capacidade do modelo de reproduzir "
        "padrões da base sintética sob as condições testadas."
    ) in first_content

    second_completed = subprocess.run(
        [
            sys.executable,
            str(SCRIPT_PATH),
            "--sample-size",
            "200",
            "--seed",
            "42",
            "--n-repeats",
            "2",
            "--output",
            str(output_path),
        ],
        cwd=REPOSITORY_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )

    assert second_completed.returncode == 0, second_completed.stderr

    second_content = output_path.read_text(encoding="utf-8")

    assert first_content == second_content
