"""Testes do runner do holdout sem acesso ao dataset oficial.

Esta suíte valida exclusivamente os guardrails operacionais do runner.
Nenhum teste carrega o CSV oficial, reconstrói o split oficial ou executa
o modelo congelado sobre o holdout reservado.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

import joblib
import pytest


SCRIPT_PATH = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "run_holdout_evaluation.py"
)


def _load_runner_module() -> ModuleType:
    """Carrega o script como módulo sem executar main()."""
    spec = importlib.util.spec_from_file_location(
        "run_holdout_evaluation",
        SCRIPT_PATH,
    )

    if spec is None or spec.loader is None:
        raise RuntimeError(
            "Não foi possível criar o spec do runner"
        )

    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)

    return module


runner = _load_runner_module()


def _build_paths(
    tmp_path: Path,
) -> Any:
    """Cria caminhos totalmente isolados para os testes."""
    data_directory = tmp_path / "data"
    docs_directory = tmp_path / "docs"
    models_directory = tmp_path / "models"

    data_directory.mkdir()
    docs_directory.mkdir()
    models_directory.mkdir()

    return runner.RunnerPaths(
        dataset_path=(
            data_directory
            / "dataset_energiai_v2.csv"
        ),
        metadata_path=(
            data_directory
            / "dataset_energiai_v2.metadata.json"
        ),
        execution_marker_path=(
            docs_directory
            / "holdout-evaluation-v2.execution.json"
        ),
        result_path=(
            docs_directory
            / "holdout-evaluation-v2.json"
        ),
        model_path=(
            models_directory
            / "modelo_energetico_v2.joblib"
        ),
    )


def _build_valid_metadata() -> dict[str, object]:
    """Monta somente os campos congelados usados pelo runner."""
    return {
        "dataset_name": (
            runner.EXPECTED_DATASET_NAME
        ),
        "dataset_version": (
            runner.EXPECTED_DATASET_VERSION
        ),
        "artifact_status": (
            runner.EXPECTED_DATASET_STATUS
        ),
        "commit_or_tag": (
            runner.EXPECTED_DATASET_SOURCE_COMMIT
        ),
        "record_count": (
            runner.schema.DATASET_SIZE
        ),
        "seed": runner.EXPECTED_SEED,
        "target": runner.schema.TARGET_COLUMN,
        "features": list(
            runner.schema.FEATURE_COLUMNS
        ),
        "classes": list(
            runner.schema.ENERGY_CATEGORIES
        ),
        "sha256": (
            runner.EXPECTED_DATASET_SHA256
        ),
    }


def _build_context(
    tmp_path: Path,
    *,
    split: object | None = None,
) -> Any:
    """Monta contexto sintético sem acessar dados oficiais."""
    return runner.PreflightContext(
        paths=_build_paths(tmp_path),
        branch=runner.EXPECTED_BRANCH,
        source_commit="a" * 40,
        dataset_sha256=(
            runner.EXPECTED_DATASET_SHA256
        ),
        split=object() if split is None else split,
    )


def _build_evaluation(
    fitted_candidate: object,
) -> Any:
    """Monta resultado sintético compatível com o avaliador."""
    return (
        runner.holdout_evaluation.HoldoutEvaluationResult(
            model_name=(
                runner.holdout_evaluation.EXPECTED_MODEL_NAME
            ),
            probability_method=(
                runner.holdout_evaluation
                .EXPECTED_PROBABILITY_METHOD
            ),
            seed=runner.EXPECTED_SEED,
            classes=tuple(
                runner.schema.ENERGY_CATEGORIES
            ),
            holdout_size=750,
            holdout_f1_macro=0.91,
            holdout_log_loss=0.12,
            holdout_brier_score=0.08,
            fitted_candidate=fitted_candidate,
        )
    )


def test_import_does_not_execute_holdout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Importar o módulo não deve disparar main ou avaliação."""
    called = False

    def unexpected_evaluation(*_: object) -> None:
        nonlocal called
        called = True

    monkeypatch.setattr(
        runner.holdout_evaluation,
        "evaluate_frozen_holdout",
        unexpected_evaluation,
    )

    assert callable(runner.main)
    assert called is False


def test_cli_preflight_has_no_path_overrides() -> None:
    """A CLI oficial não deve permitir trocar caminhos congelados."""
    parsed = runner._parse_arguments(
        ["--preflight"]
    )

    assert parsed.preflight is True
    assert parsed.execute is False

    for forbidden_attribute in (
        "dataset",
        "metadata",
        "execution_marker",
        "result",
        "model",
    ):
        assert not hasattr(
            parsed,
            forbidden_attribute,
        )


def test_cli_rejects_dataset_path_override() -> None:
    """Argumentos antigos de caminho devem ser recusados."""
    with pytest.raises(SystemExit) as error:
        runner._parse_arguments(
            [
                "--preflight",
                "--dataset",
                "outro.csv",
            ]
        )

    assert error.value.code == 2


def test_invalid_execution_confirmation_stops_before_preflight(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Confirmação inválida deve abortar antes de qualquer preflight."""
    def unexpected_preflight(*_: object) -> None:
        pytest.fail(
            "run_preflight não deveria ser chamado"
        )

    monkeypatch.setattr(
        runner,
        "run_preflight",
        unexpected_preflight,
    )

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="Confirmação inválida",
    ):
        runner.main(
            [
                "--execute",
                "--confirmation",
                "CONFIRMACAO_INVALIDA",
            ]
        )


def test_calculate_sha256_uses_file_bytes(
    tmp_path: Path,
) -> None:
    """O hash deve representar exatamente os bytes persistidos."""
    file_path = tmp_path / "sample.bin"
    content = b"energiai-holdout-test"
    file_path.write_bytes(content)

    expected = hashlib.sha256(
        content
    ).hexdigest()

    assert (
        runner._calculate_sha256(file_path)
        == expected
    )


def test_validate_metadata_accepts_frozen_identity() -> None:
    """O contrato congelado correto deve ser aceito."""
    runner._validate_metadata(
        _build_valid_metadata(),
        runner.EXPECTED_DATASET_SHA256,
    )


def test_validate_metadata_rejects_dataset_hash_divergence() -> None:
    """Qualquer alteração nos bytes do CSV deve bloquear o fluxo."""
    with pytest.raises(
        runner.HoldoutRunnerError,
        match="CSV SHA-256",
    ):
        runner._validate_metadata(
            _build_valid_metadata(),
            "0" * 64,
        )


def test_write_json_exclusive_never_overwrites(
    tmp_path: Path,
) -> None:
    """Evidência existente deve bloquear nova escrita."""
    file_path = tmp_path / "evidence.json"

    runner._write_json_exclusive(
        file_path,
        {"status": "FIRST"},
    )

    persisted = json.loads(
        file_path.read_text(
            encoding="utf-8"
        )
    )

    assert persisted == {
        "status": "FIRST"
    }

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="já existe",
    ):
        runner._write_json_exclusive(
            file_path,
            {"status": "SECOND"},
        )

    persisted_again = json.loads(
        file_path.read_text(
            encoding="utf-8"
        )
    )

    assert persisted_again == {
        "status": "FIRST"
    }


@pytest.mark.parametrize(
    "existing_attribute",
    [
        "execution_marker_path",
        "result_path",
        "model_path",
    ],
)
def test_output_absence_rejects_any_prior_evidence(
    tmp_path: Path,
    existing_attribute: str,
) -> None:
    """Qualquer evidência anterior deve bloquear nova execução."""
    paths = _build_paths(tmp_path)
    existing_path = getattr(
        paths,
        existing_attribute,
    )

    existing_path.write_bytes(b"already-exists")

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="já existe",
    ):
        runner._validate_output_absence(
            paths
        )


def test_validate_git_state_accepts_only_clean_synced_branch(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Branch correta, worktree limpo e 0/0 devem ser exigidos."""
    responses = {
        (
            "branch",
            "--show-current",
        ): runner.EXPECTED_BRANCH,
        (
            "rev-parse",
            "HEAD",
        ): "b" * 40,
        (
            "status",
            "--porcelain",
            "--untracked-files=all",
        ): "",
        (
            "rev-list",
            "--left-right",
            "--count",
            (
                "HEAD...origin/"
                + runner.EXPECTED_BRANCH
            ),
        ): "0\t0",
    }

    def fake_run_git(
        _: Path,
        *arguments: str,
    ) -> str:
        return responses[arguments]

    monkeypatch.setattr(
        runner,
        "_run_git",
        fake_run_git,
    )

    branch, source_commit = (
        runner._validate_git_state(
            tmp_path
        )
    )

    assert branch == runner.EXPECTED_BRANCH
    assert source_commit == "b" * 40


def test_validate_git_state_rejects_dirty_worktree(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Alterações locais devem bloquear execução oficial."""
    responses = {
        (
            "branch",
            "--show-current",
        ): runner.EXPECTED_BRANCH,
        (
            "rev-parse",
            "HEAD",
        ): "c" * 40,
        (
            "status",
            "--porcelain",
            "--untracked-files=all",
        ): "?? unexpected.txt",
    }

    def fake_run_git(
        _: Path,
        *arguments: str,
    ) -> str:
        return responses[arguments]

    monkeypatch.setattr(
        runner,
        "_run_git",
        fake_run_git,
    )

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="worktree",
    ):
        runner._validate_git_state(
            tmp_path
        )


def test_run_preflight_uses_only_validated_dependencies(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Preflight pode ser testado sem dataset ou holdout oficiais."""
    paths = _build_paths(tmp_path)
    fake_split = object()
    events: list[str] = []

    def validate_inputs(_: object) -> None:
        events.append("inputs")

    def validate_outputs(_: object) -> None:
        events.append("outputs")

    def validate_writable(_: Path) -> None:
        events.append("writable")

    def validate_git(
        _: Path,
    ) -> tuple[str, str]:
        events.append("git")
        return (
            runner.EXPECTED_BRANCH,
            "d" * 40,
        )

    def calculate_hash(_: Path) -> str:
        events.append("hash")
        return runner.EXPECTED_DATASET_SHA256

    def read_metadata(
        _: Path,
    ) -> dict[str, object]:
        events.append("metadata-read")
        return _build_valid_metadata()

    def validate_metadata(
        _: dict[str, object],
        actual_hash: str,
    ) -> None:
        events.append("metadata-validate")
        assert (
            actual_hash
            == runner.EXPECTED_DATASET_SHA256
        )

    def validate_model_contract() -> None:
        events.append("model-contract")

    def load_split(_: object) -> object:
        events.append("split")
        return fake_split

    monkeypatch.setattr(
        runner,
        "_validate_input_files",
        validate_inputs,
    )
    monkeypatch.setattr(
        runner,
        "_validate_output_absence",
        validate_outputs,
    )
    monkeypatch.setattr(
        runner,
        "_validate_directory_writable",
        validate_writable,
    )
    monkeypatch.setattr(
        runner,
        "_validate_git_state",
        validate_git,
    )
    monkeypatch.setattr(
        runner,
        "_calculate_sha256",
        calculate_hash,
    )
    monkeypatch.setattr(
        runner,
        "_read_json",
        read_metadata,
    )
    monkeypatch.setattr(
        runner,
        "_validate_metadata",
        validate_metadata,
    )
    monkeypatch.setattr(
        runner,
        "_validate_frozen_model_contract",
        validate_model_contract,
    )
    monkeypatch.setattr(
        runner,
        "_load_and_validate_split",
        load_split,
    )

    context = runner.run_preflight(
        paths
    )

    assert context.paths is paths
    assert context.branch == runner.EXPECTED_BRANCH
    assert context.source_commit == "d" * 40
    assert (
        context.dataset_sha256
        == runner.EXPECTED_DATASET_SHA256
    )
    assert context.split is fake_split

    assert events == [
        "inputs",
        "outputs",
        "writable",
        "writable",
        "git",
        "hash",
        "metadata-read",
        "metadata-validate",
        "model-contract",
        "split",
    ]


def test_dump_model_atomically_persists_single_joblib(
    tmp_path: Path,
) -> None:
    """O artefato sintético deve ser promovido sem arquivo parcial."""
    model_path = (
        tmp_path
        / "modelo_energetico_v2.joblib"
    )
    fitted_candidate = {
        "kind": "synthetic-test-model",
        "value": 42,
    }

    runner._dump_model_atomically(
        fitted_candidate,
        model_path,
    )

    assert model_path.is_file()

    loaded = joblib.load(
        model_path
    )

    assert loaded == fitted_candidate

    leftovers = list(
        tmp_path.glob(
            ".modelo-energetico-v2-*.tmp"
        )
    )
    assert leftovers == []

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="já existe",
    ):
        runner._dump_model_atomically(
            fitted_candidate,
            model_path,
        )


def test_execute_official_holdout_calls_evaluator_once_and_in_order(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Marcador deve anteceder a única chamada ao avaliador."""
    fake_split = object()
    context = _build_context(
        tmp_path,
        split=fake_split,
    )
    fitted_candidate = object()
    evaluation = _build_evaluation(
        fitted_candidate
    )

    events: list[str] = []
    evaluation_calls = 0
    timestamps = iter(
        [
            "2026-08-11T04:00:00+00:00",
            "2026-08-11T04:01:00+00:00",
        ]
    )

    def fake_now() -> str:
        return next(timestamps)

    def fake_write(
        file_path: Path,
        _: dict[str, object],
    ) -> None:
        if (
            file_path
            == context.paths.execution_marker_path
        ):
            events.append("marker")
        elif (
            file_path
            == context.paths.result_path
        ):
            events.append("result")
        else:
            pytest.fail(
                f"Escrita inesperada: {file_path}"
            )

    def fake_evaluate(
        split: object,
    ) -> object:
        nonlocal evaluation_calls
        evaluation_calls += 1
        events.append("evaluate")
        assert split is fake_split
        return evaluation

    def fake_dump(
        candidate: object,
        _: Path,
    ) -> None:
        events.append("model")
        assert candidate is fitted_candidate

    def fake_hash(_: Path) -> str:
        events.append("model-hash")
        return "e" * 64

    monkeypatch.setattr(
        runner,
        "_utc_now",
        fake_now,
    )
    monkeypatch.setattr(
        runner,
        "_write_json_exclusive",
        fake_write,
    )
    monkeypatch.setattr(
        runner.holdout_evaluation,
        "evaluate_frozen_holdout",
        fake_evaluate,
    )
    monkeypatch.setattr(
        runner,
        "_dump_model_atomically",
        fake_dump,
    )
    monkeypatch.setattr(
        runner,
        "_calculate_sha256",
        fake_hash,
    )

    result = (
        runner.execute_official_holdout(
            context
        )
    )

    assert evaluation_calls == 1
    assert events == [
        "marker",
        "evaluate",
        "model",
        "model-hash",
        "result",
    ]

    assert (
        result["holdout"][
            "official_evaluation_count"
        ]
        == 1
    )
    assert (
        result["model"]["artifact_sha256"]
        == "e" * 64
    )
    assert (
        result["methodology_statement"]
        == runner.METHODOLOGY_STATEMENT
    )


def test_execution_failure_preserves_marker_and_blocks_result(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Falha posterior ao início deve deixar marcador persistente."""
    context = _build_context(
        tmp_path
    )

    def fail_evaluation(
        _: object,
    ) -> None:
        raise RuntimeError(
            "synthetic evaluation failure"
        )

    monkeypatch.setattr(
        runner.holdout_evaluation,
        "evaluate_frozen_holdout",
        fail_evaluation,
    )

    with pytest.raises(
        runner.HoldoutRunnerError,
        match="NÃO execute novamente",
    ) as error:
        runner.execute_official_holdout(
            context
        )

    assert (
        "synthetic evaluation failure"
        in str(error.value)
    )
    assert (
        context.paths.execution_marker_path
        .is_file()
    )
    assert not context.paths.result_path.exists()
    assert not context.paths.model_path.exists()

    marker = json.loads(
        context.paths.execution_marker_path
        .read_text(
            encoding="utf-8"
        )
    )

    assert (
        marker["status"]
        == "OFFICIAL_EVALUATION_STARTED"
    )


def test_preflight_main_never_calls_official_evaluator(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    """--preflight deve apenas relatar validação sem avaliação."""
    context = _build_context(
        tmp_path
    )

    monkeypatch.setattr(
        runner,
        "_build_paths",
        lambda: context.paths,
    )
    monkeypatch.setattr(
        runner,
        "run_preflight",
        lambda _: context,
    )

    def unexpected_execution(
        _: object,
    ) -> None:
        pytest.fail(
            "execute_official_holdout não deveria ser chamado"
        )

    monkeypatch.setattr(
        runner,
        "execute_official_holdout",
        unexpected_execution,
    )

    exit_code = runner.main(
        ["--preflight"]
    )

    captured = capsys.readouterr()

    assert exit_code == 0
    assert (
        "Preflight do holdout: APROVADO"
        in captured.out
    )
    assert (
        "Holdout de desempenho: NÃO AVALIADO"
        in captured.out
    )


def test_result_payload_contains_mandatory_limitation(
    tmp_path: Path,
) -> None:
    """O relatório sintético deve preservar a frase metodológica."""
    context = _build_context(
        tmp_path
    )
    evaluation = _build_evaluation(
        object()
    )

    payload = runner._build_result_payload(
        context=context,
        evaluation=evaluation,
        model_sha256="f" * 64,
        started_at_utc=(
            "2026-08-11T04:00:00+00:00"
        ),
        completed_at_utc=(
            "2026-08-11T04:01:00+00:00"
        ),
    )

    assert (
        payload["methodology_statement"]
        == (
            "O resultado mede a capacidade do modelo "
            "de reproduzir padrões da base sintética "
            "sob as condições testadas."
        )
    )
    assert (
        payload["holdout"]["size"]
        == 750
    )
    assert (
        payload["holdout"][
            "official_evaluation_count"
        ]
        == 1
    )
