"""Valida limpeza, compilação e execução do notebook V2."""

from __future__ import annotations

import argparse
import ast
import shutil
import tempfile
from pathlib import Path
from typing import Sequence

import nbformat
from nbconvert.preprocessors import ExecutePreprocessor


DEFAULT_NOTEBOOK_PATH = Path(
    "notebooks/01_dataset_modelagem_energiai_v2.ipynb"
)
EXPECTED_KERNEL_NAME = "python3"


def _parse_arguments(
    arguments: Sequence[str] | None = None,
) -> argparse.Namespace:
    """Lê os argumentos do validador."""
    parser = argparse.ArgumentParser(
        description=(
            "Valida limpeza, compilação e execução "
            "do notebook EnergIAI V2."
        )
    )
    parser.add_argument(
        "--notebook",
        type=Path,
        default=DEFAULT_NOTEBOOK_PATH,
        help="Caminho do notebook em relação ao diretório atual.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=900,
        help="Timeout em segundos para cada célula.",
    )

    return parser.parse_args(arguments)


def _normalize_notebook_path(
    notebook_path: Path,
) -> Path:
    """Resolve e valida o caminho do notebook."""
    if not isinstance(notebook_path, Path):
        raise TypeError(
            "notebook_path deve ser pathlib.Path"
        )

    normalized_path = notebook_path.resolve()

    if not normalized_path.is_file():
        raise FileNotFoundError(
            f"notebook não encontrado: {normalized_path}"
        )

    if normalized_path.suffix != ".ipynb":
        raise ValueError(
            "notebook deve possuir extensão .ipynb"
        )

    return normalized_path


def _read_notebook(
    notebook_path: Path,
) -> nbformat.NotebookNode:
    """Lê e valida estruturalmente o notebook."""
    try:
        notebook = nbformat.read(
            notebook_path,
            as_version=4,
        )
    except Exception as error:
        raise ValueError(
            f"falha ao ler notebook: {notebook_path}"
        ) from error

    try:
        nbformat.validate(notebook)
    except Exception as error:
        raise ValueError(
            f"notebook inválido: {notebook_path}"
        ) from error

    return notebook


def validate_notebook_cleanliness(
    notebook: nbformat.NotebookNode,
) -> None:
    """Exige notebook sem outputs nem contadores de execução."""
    cells_with_outputs: list[int] = []
    cells_with_execution_count: list[int] = []

    for index, cell in enumerate(notebook.cells):
        if cell.cell_type != "code":
            continue

        if cell.get("outputs"):
            cells_with_outputs.append(index)

        if cell.get("execution_count") is not None:
            cells_with_execution_count.append(index)

    if cells_with_outputs:
        raise ValueError(
            "notebook possui outputs persistidos nas células: "
            f"{cells_with_outputs}"
        )

    if cells_with_execution_count:
        raise ValueError(
            "notebook possui execution_count nas células: "
            f"{cells_with_execution_count}"
        )


def compile_notebook_code_cells(
    notebook: nbformat.NotebookNode,
    notebook_path: Path,
) -> int:
    """Compila todas as células Python e retorna a quantidade."""
    compiled_cells = 0

    for index, cell in enumerate(notebook.cells):
        if cell.cell_type != "code":
            continue

        source = str(cell.source)

        try:
            ast.parse(
                source,
                filename=(
                    f"{notebook_path}"
                    f"#cell-{index}"
                ),
            )
        except SyntaxError as error:
            raise SyntaxError(
                "falha de compilação na célula "
                f"{index} do notebook"
            ) from error

        compiled_cells += 1

    if compiled_cells == 0:
        raise ValueError(
            "notebook deve possuir células de código"
        )

    return compiled_cells


def validate_kernel_metadata(
    notebook: nbformat.NotebookNode,
) -> None:
    """Valida o kernel esperado pelo smoke test."""
    kernelspec = notebook.metadata.get(
        "kernelspec",
        {},
    )
    kernel_name = kernelspec.get("name")

    if kernel_name != EXPECTED_KERNEL_NAME:
        raise ValueError(
            "kernel do notebook inválido: "
            f"{kernel_name!r}; esperado "
            f"{EXPECTED_KERNEL_NAME!r}"
        )


def execute_notebook_copy(
    notebook_path: Path,
    timeout: int,
) -> None:
    """Executa uma cópia temporária sem alterar o original."""
    if isinstance(timeout, bool) or not isinstance(
        timeout,
        int,
    ):
        raise TypeError(
            "timeout deve ser inteiro"
        )

    if timeout <= 0:
        raise ValueError(
            "timeout deve ser maior que zero"
        )

    repository_root = notebook_path.parents[2]

    with tempfile.TemporaryDirectory(
        prefix="energiai-notebook-smoke-"
    ) as temporary_directory:
        temporary_path = (
            Path(temporary_directory)
            / notebook_path.name
        )

        shutil.copy2(
            notebook_path,
            temporary_path,
        )

        copied_notebook = _read_notebook(
            temporary_path
        )

        processor = ExecutePreprocessor(
            timeout=timeout,
            kernel_name=EXPECTED_KERNEL_NAME,
        )

        try:
            processor.preprocess(
                copied_notebook,
                {
                    "metadata": {
                        "path": str(repository_root),
                    }
                },
            )
        except Exception as error:
            raise RuntimeError(
                "smoke test do notebook falhou"
            ) from error


def validate_notebook(
    notebook_path: Path,
    timeout: int = 900,
) -> int:
    """Executa todas as validações obrigatórias."""
    normalized_path = _normalize_notebook_path(
        notebook_path
    )
    notebook = _read_notebook(
        normalized_path
    )

    validate_notebook_cleanliness(
        notebook
    )
    validate_kernel_metadata(
        notebook
    )
    compiled_cells = compile_notebook_code_cells(
        notebook,
        normalized_path,
    )
    execute_notebook_copy(
        normalized_path,
        timeout,
    )

    return compiled_cells


def main(
    arguments: Sequence[str] | None = None,
) -> int:
    """Executa o validador pela linha de comando."""
    parsed_arguments = _parse_arguments(
        arguments
    )

    compiled_cells = validate_notebook(
        notebook_path=parsed_arguments.notebook,
        timeout=parsed_arguments.timeout,
    )

    print("Notebook limpo: aprovado")
    print(
        "Células de código compiladas: "
        f"{compiled_cells}"
    )
    print(
        "Smoke test em cópia temporária: aprovado"
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
