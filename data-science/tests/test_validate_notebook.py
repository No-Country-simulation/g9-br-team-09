"""Testes do validador do notebook EnergIAI V2."""

from __future__ import annotations

import importlib.util
from pathlib import Path
from types import ModuleType
from typing import Any

import nbformat
import pytest


SCRIPT_PATH = (
    Path(__file__).parents[1]
    / "scripts"
    / "validate_notebook.py"
)


def _load_validator_module() -> ModuleType:
    """Carrega o script de validação como módulo de teste."""
    module_name = "energiai_validate_notebook"
    spec = importlib.util.spec_from_file_location(
        module_name,
        SCRIPT_PATH,
    )

    if spec is None or spec.loader is None:
        raise RuntimeError(
            "não foi possível carregar validate_notebook.py"
        )

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    return module


validator = _load_validator_module()


def _build_notebook(
    *sources: str,
) -> nbformat.NotebookNode:
    """Cria notebook mínimo com kernel Python 3."""
    notebook = nbformat.v4.new_notebook()
    notebook.metadata["kernelspec"] = {
        "display_name": "Python 3",
        "language": "python",
        "name": "python3",
    }
    notebook.metadata["language_info"] = {
        "name": "python",
    }
    notebook.cells = [
        nbformat.v4.new_code_cell(source=source)
        for source in sources
    ]

    return notebook


def _write_notebook(
    notebook_path: Path,
    notebook: nbformat.NotebookNode,
) -> None:
    """Persiste notebook válido em UTF-8."""
    notebook_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    nbformat.write(
        notebook,
        notebook_path,
    )


def test_validate_notebook_cleanliness_accepts_clean_notebook() -> None:
    notebook = _build_notebook(
        "value = 1",
    )

    validator.validate_notebook_cleanliness(
        notebook
    )


def test_validate_notebook_cleanliness_rejects_outputs() -> None:
    notebook = _build_notebook(
        "print('ok')",
    )
    notebook.cells[0]["outputs"] = [
        nbformat.v4.new_output(
            output_type="stream",
            name="stdout",
            text="ok\n",
        )
    ]

    with pytest.raises(
        ValueError,
        match="outputs persistidos",
    ):
        validator.validate_notebook_cleanliness(
            notebook
        )


def test_validate_notebook_cleanliness_rejects_execution_count() -> None:
    notebook = _build_notebook(
        "value = 1",
    )
    notebook.cells[0]["execution_count"] = 1

    with pytest.raises(
        ValueError,
        match="execution_count",
    ):
        validator.validate_notebook_cleanliness(
            notebook
        )


def test_compile_notebook_code_cells_returns_code_cell_count() -> None:
    notebook = _build_notebook(
        "first = 1",
        "second = first + 1",
    )
    notebook.cells.insert(
        1,
        nbformat.v4.new_markdown_cell(
            source="Texto explicativo."
        ),
    )

    compiled_cells = (
        validator.compile_notebook_code_cells(
            notebook,
            Path("notebook.ipynb"),
        )
    )

    assert compiled_cells == 2


def test_compile_notebook_code_cells_rejects_invalid_syntax() -> None:
    notebook = _build_notebook(
        "if True print('erro')",
    )

    with pytest.raises(
        SyntaxError,
        match="falha de compilação na célula 0",
    ):
        validator.compile_notebook_code_cells(
            notebook,
            Path("notebook.ipynb"),
        )


def test_compile_notebook_code_cells_rejects_notebook_without_code() -> None:
    notebook = nbformat.v4.new_notebook()
    notebook.cells = [
        nbformat.v4.new_markdown_cell(
            source="Sem código."
        )
    ]

    with pytest.raises(
        ValueError,
        match="deve possuir células de código",
    ):
        validator.compile_notebook_code_cells(
            notebook,
            Path("notebook.ipynb"),
        )


def test_validate_kernel_metadata_accepts_python3() -> None:
    notebook = _build_notebook(
        "value = 1",
    )

    validator.validate_kernel_metadata(
        notebook
    )


def test_validate_kernel_metadata_rejects_unexpected_kernel() -> None:
    notebook = _build_notebook(
        "value = 1",
    )
    notebook.metadata["kernelspec"]["name"] = (
        "unexpected-kernel"
    )

    with pytest.raises(
        ValueError,
        match="kernel do notebook inválido",
    ):
        validator.validate_kernel_metadata(
            notebook
        )


def test_normalize_notebook_path_rejects_invalid_inputs(
    tmp_path: Path,
) -> None:
    with pytest.raises(
        TypeError,
        match="notebook_path deve ser pathlib.Path",
    ):
        validator._normalize_notebook_path(
            "notebook.ipynb"  # type: ignore[arg-type]
        )

    with pytest.raises(
        FileNotFoundError,
        match="notebook não encontrado",
    ):
        validator._normalize_notebook_path(
            tmp_path / "missing.ipynb"
        )

    invalid_extension = tmp_path / "notebook.txt"
    invalid_extension.write_text(
        "{}",
        encoding="utf-8",
    )

    with pytest.raises(
        ValueError,
        match="extensão .ipynb",
    ):
        validator._normalize_notebook_path(
            invalid_extension
        )


@pytest.mark.parametrize(
    ("timeout", "error_type", "message"),
    (
        (
            True,
            TypeError,
            "timeout deve ser inteiro",
        ),
        (
            0,
            ValueError,
            "timeout deve ser maior que zero",
        ),
        (
            -1,
            ValueError,
            "timeout deve ser maior que zero",
        ),
    ),
)
def test_execute_notebook_copy_rejects_invalid_timeout(
    tmp_path: Path,
    timeout: object,
    error_type: type[Exception],
    message: str,
) -> None:
    with pytest.raises(
        error_type,
        match=message,
    ):
        validator.execute_notebook_copy(
            tmp_path / "notebook.ipynb",
            timeout,  # type: ignore[arg-type]
        )


def test_execute_notebook_copy_uses_temporary_copy(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    repository_root = tmp_path / "repository"
    notebook_path = (
        repository_root
        / "data-science"
        / "notebooks"
        / "notebook.ipynb"
    )
    notebook = _build_notebook(
        "value = 1",
    )
    _write_notebook(
        notebook_path,
        notebook,
    )

    original_bytes = notebook_path.read_bytes()
    read_paths: list[Path] = []
    processor_arguments: dict[str, Any] = {}
    preprocess_resources: dict[str, Any] = {}

    original_read_notebook = (
        validator._read_notebook
    )

    def recording_read_notebook(
        path: Path,
    ) -> nbformat.NotebookNode:
        read_paths.append(path)
        return original_read_notebook(path)

    class FakeExecutePreprocessor:
        def __init__(
            self,
            *,
            timeout: int,
            kernel_name: str,
        ) -> None:
            processor_arguments.update(
                {
                    "timeout": timeout,
                    "kernel_name": kernel_name,
                }
            )

        def preprocess(
            self,
            copied_notebook: nbformat.NotebookNode,
            resources: dict[str, Any],
        ) -> tuple[nbformat.NotebookNode, dict[str, Any]]:
            preprocess_resources.update(resources)
            return copied_notebook, resources

    monkeypatch.setattr(
        validator,
        "_read_notebook",
        recording_read_notebook,
    )
    monkeypatch.setattr(
        validator,
        "ExecutePreprocessor",
        FakeExecutePreprocessor,
    )

    validator.execute_notebook_copy(
        notebook_path,
        timeout=120,
    )

    assert notebook_path.read_bytes() == original_bytes
    assert len(read_paths) == 1
    assert read_paths[0] != notebook_path
    assert read_paths[0].name == notebook_path.name
    assert not read_paths[0].exists()
    assert processor_arguments == {
        "timeout": 120,
        "kernel_name": "python3",
    }
    assert preprocess_resources == {
        "metadata": {
            "path": str(repository_root),
        }
    }


def test_validate_notebook_runs_all_validations(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    notebook_path = (
        tmp_path
        / "repository"
        / "data-science"
        / "notebooks"
        / "notebook.ipynb"
    )
    notebook = _build_notebook(
        "first = 1",
        "second = first + 1",
    )
    _write_notebook(
        notebook_path,
        notebook,
    )

    execution_calls: list[tuple[Path, int]] = []

    def fake_execute_notebook_copy(
        received_path: Path,
        timeout: int,
    ) -> None:
        execution_calls.append(
            (received_path, timeout)
        )

    monkeypatch.setattr(
        validator,
        "execute_notebook_copy",
        fake_execute_notebook_copy,
    )

    compiled_cells = validator.validate_notebook(
        notebook_path,
        timeout=321,
    )

    assert compiled_cells == 2
    assert execution_calls == [
        (
            notebook_path.resolve(),
            321,
        )
    ]


def test_main_reports_success(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    monkeypatch.setattr(
        validator,
        "validate_notebook",
        lambda notebook_path, timeout: 22,
    )

    exit_code = validator.main(
        [
            "--notebook",
            "notebooks/notebook.ipynb",
            "--timeout",
            "123",
        ]
    )

    captured = capsys.readouterr()

    assert exit_code == 0
    assert "Notebook limpo: aprovado" in captured.out
    assert (
        "Células de código compiladas: 22"
        in captured.out
    )
    assert (
        "Smoke test em cópia temporária: aprovado"
        in captured.out
    )
