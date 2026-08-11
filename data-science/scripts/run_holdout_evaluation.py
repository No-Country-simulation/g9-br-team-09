"""Execução controlada e única do holdout oficial da issue #86.

Este script orquestra exclusivamente a avaliação final autorizada após
o Marco 2.

Guardrails principais:
- valida a identidade criptográfica do dataset oficial;
- valida os metadados congelados do dataset;
- reconstrói exclusivamente o split central 70/15/15 com seed 42;
- exige branch publicada, sincronizada e worktree limpo;
- exige confirmação textual explícita para a execução oficial;
- cria um marcador persistente e exclusivo antes da avaliação;
- chama evaluate_frozen_holdout exatamente uma vez por execução oficial;
- serializa exatamente o fitted_candidate que produziu as métricas;
- persiste métricas e hashes sem selecionar ou ajustar outra solução;
- recusa nova execução quando qualquer evidência oficial já existir.

O modo --preflight não avalia o holdout.
Importar este módulo também não executa nenhuma avaliação.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Final, Sequence

import joblib
import pandas as pd
import sklearn


DATA_SCIENCE_ROOT: Final[Path] = Path(__file__).resolve().parents[1]
SRC_DIRECTORY: Final[Path] = DATA_SCIENCE_ROOT / "src"

if str(SRC_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SRC_DIRECTORY))

import data_split  # noqa: E402
import dataset_artifact  # noqa: E402
import holdout_evaluation  # noqa: E402
import schema  # noqa: E402


EXPECTED_BRANCH: Final[str] = "feature/86/dataset-modelagem-v2"

EXPECTED_DATASET_NAME: Final[str] = "EnergIAI Dataset V2"
EXPECTED_DATASET_VERSION: Final[str] = "2.0.0-candidate"
EXPECTED_DATASET_STATUS: Final[str] = "CANDIDATE"
EXPECTED_DATASET_SOURCE_COMMIT: Final[str] = (
    "5aba82b5f465501cc00b22d96e510b50f112e95e"
)
EXPECTED_DATASET_SHA256: Final[str] = (
    "6c147517fce6108f0f663d72c41428736325e248db171a8357050ab02c8a73a3"
)
EXPECTED_SEED: Final[int] = 42

EXECUTION_CONFIRMATION: Final[str] = (
    "EXECUTAR_HOLDOUT_OFICIAL_V2_UNICA_VEZ"
)

DEFAULT_DATASET_PATH: Final[Path] = (
    DATA_SCIENCE_ROOT
    / "data"
    / dataset_artifact.DATASET_FILENAME
)
DEFAULT_METADATA_PATH: Final[Path] = (
    DATA_SCIENCE_ROOT
    / "data"
    / dataset_artifact.METADATA_FILENAME
)
DEFAULT_EXECUTION_MARKER_PATH: Final[Path] = (
    DATA_SCIENCE_ROOT
    / "docs"
    / "holdout-evaluation-v2.execution.json"
)
DEFAULT_RESULT_PATH: Final[Path] = (
    DATA_SCIENCE_ROOT
    / "docs"
    / "holdout-evaluation-v2.json"
)
DEFAULT_MODEL_PATH: Final[Path] = (
    DATA_SCIENCE_ROOT
    / "models"
    / "modelo_energetico_v2.joblib"
)

METHODOLOGY_STATEMENT: Final[str] = (
    "O resultado mede a capacidade do modelo de reproduzir padrões "
    "da base sintética sob as condições testadas."
)


class HoldoutRunnerError(RuntimeError):
    """Indica falha ou violação no protocolo de execução do holdout."""


@dataclass(frozen=True)
class RunnerPaths:
    """Caminhos utilizados pela execução controlada."""

    dataset_path: Path
    metadata_path: Path
    execution_marker_path: Path
    result_path: Path
    model_path: Path


@dataclass(frozen=True)
class PreflightContext:
    """Estado validado antes da criação do marcador oficial."""

    paths: RunnerPaths
    branch: str
    source_commit: str
    dataset_sha256: str
    split: data_split.DataSplit


def _utc_now() -> str:
    """Retorna timestamp UTC auditável."""
    return datetime.now(timezone.utc).isoformat()


def _calculate_sha256(file_path: Path) -> str:
    """Calcula SHA-256 de um arquivo sem depender de helper privado."""
    digest = hashlib.sha256()

    try:
        with file_path.open("rb") as file:
            for chunk in iter(
                lambda: file.read(1024 * 1024),
                b"",
            ):
                digest.update(chunk)
    except OSError as error:
        raise HoldoutRunnerError(
            f"Não foi possível calcular SHA-256 de {file_path}: {error}"
        ) from error

    return digest.hexdigest()


def _read_json(file_path: Path) -> dict[str, Any]:
    """Lê um objeto JSON UTF-8."""
    try:
        with file_path.open(
            "r",
            encoding="utf-8",
        ) as file:
            content = json.load(file)
    except (OSError, json.JSONDecodeError) as error:
        raise HoldoutRunnerError(
            f"Não foi possível ler JSON válido de {file_path}: {error}"
        ) from error

    if not isinstance(content, dict):
        raise HoldoutRunnerError(
            f"O JSON deve possuir objeto na raiz: {file_path}"
        )

    return content


def _write_json_exclusive(
    file_path: Path,
    payload: dict[str, Any],
) -> None:
    """Cria JSON exclusivamente; nunca sobrescreve evidência existente."""
    try:
        with file_path.open(
            "x",
            encoding="utf-8",
            newline="\n",
        ) as file:
            json.dump(
                payload,
                file,
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            file.write("\n")
    except FileExistsError as error:
        raise HoldoutRunnerError(
            f"A evidência já existe e não pode ser sobrescrita: {file_path}"
        ) from error
    except OSError as error:
        raise HoldoutRunnerError(
            f"Falha ao persistir evidência em {file_path}: {error}"
        ) from error


def _expect_equal(
    field_name: str,
    actual: object,
    expected: object,
) -> None:
    """Exige igualdade exata em um campo congelado."""
    if actual != expected:
        raise HoldoutRunnerError(
            f"{field_name} divergiu do contrato congelado: "
            f"atual={actual!r}, esperado={expected!r}"
        )


def _validate_metadata(
    metadata: dict[str, Any],
    actual_dataset_sha256: str,
) -> None:
    """Valida somente identidade e contrato estável do dataset."""
    _expect_equal(
        "dataset_name",
        metadata.get("dataset_name"),
        EXPECTED_DATASET_NAME,
    )
    _expect_equal(
        "dataset_version",
        metadata.get("dataset_version"),
        EXPECTED_DATASET_VERSION,
    )
    _expect_equal(
        "artifact_status",
        metadata.get("artifact_status"),
        EXPECTED_DATASET_STATUS,
    )
    _expect_equal(
        "commit_or_tag",
        metadata.get("commit_or_tag"),
        EXPECTED_DATASET_SOURCE_COMMIT,
    )
    _expect_equal(
        "record_count",
        metadata.get("record_count"),
        schema.DATASET_SIZE,
    )
    _expect_equal(
        "seed",
        metadata.get("seed"),
        EXPECTED_SEED,
    )
    _expect_equal(
        "target",
        metadata.get("target"),
        schema.TARGET_COLUMN,
    )
    _expect_equal(
        "features",
        tuple(metadata.get("features", ())),
        schema.FEATURE_COLUMNS,
    )
    _expect_equal(
        "classes",
        tuple(metadata.get("classes", ())),
        schema.ENERGY_CATEGORIES,
    )
    _expect_equal(
        "metadata.sha256",
        metadata.get("sha256"),
        EXPECTED_DATASET_SHA256,
    )
    _expect_equal(
        "CSV SHA-256",
        actual_dataset_sha256,
        EXPECTED_DATASET_SHA256,
    )

    if dataset_artifact.DATASET_VERSION != EXPECTED_DATASET_VERSION:
        raise HoldoutRunnerError(
            "dataset_artifact.DATASET_VERSION divergiu do "
            "contrato congelado"
        )

    if schema.RANDOM_SEED != EXPECTED_SEED:
        raise HoldoutRunnerError(
            "schema.RANDOM_SEED divergiu do contrato congelado"
        )


def _run_git(
    repository_root: Path,
    *arguments: str,
) -> str:
    """Executa comando Git somente leitura e devolve stdout."""
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=repository_root,
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise HoldoutRunnerError(
            "Falha ao consultar o estado Git com "
            f"'git {' '.join(arguments)}': {error}"
        ) from error

    return completed.stdout.strip()


def _validate_git_state(
    repository_root: Path,
) -> tuple[str, str]:
    """Exige branch correta, worktree limpo e sincronização 0/0."""
    branch = _run_git(
        repository_root,
        "branch",
        "--show-current",
    )
    source_commit = _run_git(
        repository_root,
        "rev-parse",
        "HEAD",
    )
    status = _run_git(
        repository_root,
        "status",
        "--porcelain",
        "--untracked-files=all",
    )

    if branch != EXPECTED_BRANCH:
        raise HoldoutRunnerError(
            "A execução oficial só pode ocorrer na branch "
            f"{EXPECTED_BRANCH}; atual={branch!r}"
        )

    if status:
        raise HoldoutRunnerError(
            "O worktree deve estar completamente limpo antes "
            "da execução oficial"
        )

    remote_difference = _run_git(
        repository_root,
        "rev-list",
        "--left-right",
        "--count",
        f"HEAD...origin/{EXPECTED_BRANCH}",
    )

    normalized_difference = remote_difference.split()

    if normalized_difference != ["0", "0"]:
        raise HoldoutRunnerError(
            "HEAD local deve estar sincronizado com a branch remota "
            f"antes da execução; atual={remote_difference!r}"
        )

    return branch, source_commit


def _validate_input_files(paths: RunnerPaths) -> None:
    """Exige a presença dos artefatos de entrada."""
    for file_path in (
        paths.dataset_path,
        paths.metadata_path,
    ):
        if not file_path.is_file():
            raise HoldoutRunnerError(
                f"Arquivo obrigatório não encontrado: {file_path}"
            )


def _validate_output_absence(paths: RunnerPaths) -> None:
    """Recusa execução quando qualquer evidência oficial já existe."""
    for file_path in (
        paths.execution_marker_path,
        paths.result_path,
        paths.model_path,
    ):
        if file_path.exists():
            raise HoldoutRunnerError(
                "Execução oficial recusada porque já existe "
                f"artefato ou evidência: {file_path}"
            )


def _validate_directory_writable(directory: Path) -> None:
    """Valida escrita no diretório antes de consumir o holdout."""
    if not directory.is_dir():
        raise HoldoutRunnerError(
            f"Diretório obrigatório não encontrado: {directory}"
        )

    temporary_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=".holdout-preflight-",
            suffix=".tmp",
            dir=directory,
            delete=False,
        ) as temporary:
            temporary.write(b"preflight")
            temporary_path = Path(temporary.name)
    except OSError as error:
        raise HoldoutRunnerError(
            f"Diretório sem escrita disponível: {directory}: {error}"
        ) from error
    finally:
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError as error:
                raise HoldoutRunnerError(
                    "Não foi possível remover arquivo temporário de "
                    f"preflight em {directory}: {error}"
                ) from error


def _load_and_validate_split(
    paths: RunnerPaths,
) -> data_split.DataSplit:
    """Carrega o dataset validado e reconstrói o split central."""
    try:
        sample = pd.read_csv(paths.dataset_path)
    except (
        OSError,
        UnicodeError,
        pd.errors.ParserError,
    ) as error:
        raise HoldoutRunnerError(
            f"Falha ao carregar o dataset oficial: {error}"
        ) from error

    try:
        dataset_artifact.validate_final_dataset(sample)
    except (
        TypeError,
        ValueError,
        KeyError,
        RuntimeError,
    ) as error:
        raise HoldoutRunnerError(
            f"O dataset oficial falhou na validação integral: {error}"
        ) from error

    try:
        return data_split.create_stratified_data_split(
            sample,
            seed=EXPECTED_SEED,
        )
    except (
        TypeError,
        ValueError,
        KeyError,
        RuntimeError,
    ) as error:
        raise HoldoutRunnerError(
            f"Não foi possível reconstruir o split oficial: {error}"
        ) from error


def _validate_frozen_model_contract() -> None:
    """Valida o contrato congelado sem consultar métricas do holdout."""
    try:
        holdout_evaluation._validate_frozen_contract()
    except holdout_evaluation.HoldoutEvaluationError as error:
        raise HoldoutRunnerError(
            f"O contrato congelado do modelo divergiu: {error}"
        ) from error


def run_preflight(
    paths: RunnerPaths,
) -> PreflightContext:
    """Executa somente validações anteriores à avaliação oficial."""
    _validate_input_files(paths)
    _validate_output_absence(paths)

    _validate_directory_writable(
        paths.execution_marker_path.parent
    )
    _validate_directory_writable(
        paths.model_path.parent
    )

    branch, source_commit = _validate_git_state(
        DATA_SCIENCE_ROOT.parent
    )

    dataset_sha256 = _calculate_sha256(
        paths.dataset_path
    )
    metadata = _read_json(
        paths.metadata_path
    )

    _validate_metadata(
        metadata,
        dataset_sha256,
    )
    _validate_frozen_model_contract()

    split = _load_and_validate_split(
        paths
    )

    return PreflightContext(
        paths=paths,
        branch=branch,
        source_commit=source_commit,
        dataset_sha256=dataset_sha256,
        split=split,
    )


def _build_marker_payload(
    context: PreflightContext,
    started_at_utc: str,
) -> dict[str, Any]:
    """Monta marcador que bloqueia qualquer segunda execução."""
    return {
        "purpose": "single_official_holdout_evaluation",
        "status": "OFFICIAL_EVALUATION_STARTED",
        "started_at_utc": started_at_utc,
        "branch": context.branch,
        "source_commit": context.source_commit,
        "dataset": {
            "name": EXPECTED_DATASET_NAME,
            "version": EXPECTED_DATASET_VERSION,
            "source_commit": EXPECTED_DATASET_SOURCE_COMMIT,
            "sha256": context.dataset_sha256,
            "seed": EXPECTED_SEED,
        },
        "frozen_solution": {
            "model": holdout_evaluation.EXPECTED_MODEL_NAME,
            "probability_method": (
                holdout_evaluation.EXPECTED_PROBABILITY_METHOD
            ),
        },
        "environment": {
            "python": platform.python_version(),
            "joblib": joblib.__version__,
            "pandas": pd.__version__,
            "scikit_learn": sklearn.__version__,
        },
        "guardrail": (
            "A presença deste arquivo bloqueia qualquer nova "
            "execução oficial do holdout."
        ),
    }


def _dump_model_atomically(
    fitted_candidate: object,
    model_path: Path,
) -> None:
    """Serializa o candidato oficial em um único arquivo sem parcial final."""
    if model_path.exists():
        raise HoldoutRunnerError(
            f"O artefato final já existe: {model_path}"
        )

    temporary_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=".modelo-energetico-v2-",
            suffix=".joblib.tmp",
            dir=model_path.parent,
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)

        dumped_files = joblib.dump(
            fitted_candidate,
            temporary_path,
            compress=3,
        )

        if len(dumped_files) != 1:
            raise HoldoutRunnerError(
                "A serialização oficial deve produzir exatamente "
                "um arquivo Joblib"
            )

        dumped_path = Path(dumped_files[0]).resolve()

        if dumped_path != temporary_path.resolve():
            raise HoldoutRunnerError(
                "O Joblib persistiu o candidato em caminho inesperado"
            )

        if model_path.exists():
            raise HoldoutRunnerError(
                "O artefato final surgiu durante a serialização; "
                "nenhum arquivo será sobrescrito"
            )

        os.replace(
            temporary_path,
            model_path,
        )
        temporary_path = None
    except HoldoutRunnerError:
        raise
    except Exception as error:
        raise HoldoutRunnerError(
            f"Falha ao serializar o fitted_candidate oficial: {error}"
        ) from error
    finally:
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError:
                pass


def _build_result_payload(
    context: PreflightContext,
    evaluation: holdout_evaluation.HoldoutEvaluationResult,
    model_sha256: str,
    started_at_utc: str,
    completed_at_utc: str,
) -> dict[str, Any]:
    """Monta o registro final sem incluir o estimador serializado."""
    return {
        "status": "OFFICIAL_HOLDOUT_EVALUATED",
        "started_at_utc": started_at_utc,
        "completed_at_utc": completed_at_utc,
        "branch": context.branch,
        "source_commit": context.source_commit,
        "dataset": {
            "name": EXPECTED_DATASET_NAME,
            "version": EXPECTED_DATASET_VERSION,
            "source_commit": EXPECTED_DATASET_SOURCE_COMMIT,
            "sha256": context.dataset_sha256,
            "seed": EXPECTED_SEED,
        },
        "model": {
            "name": evaluation.model_name,
            "probability_method": evaluation.probability_method,
            "classes": list(evaluation.classes),
            "artifact_path": (
                "models/modelo_energetico_v2.joblib"
            ),
            "artifact_sha256": model_sha256,
        },
        "holdout": {
            "size": evaluation.holdout_size,
            "f1_macro": evaluation.holdout_f1_macro,
            "log_loss": evaluation.holdout_log_loss,
            "brier_multiclass": evaluation.holdout_brier_score,
            "official_evaluation_count": 1,
        },
        "environment": {
            "python": platform.python_version(),
            "joblib": joblib.__version__,
            "pandas": pd.__version__,
            "scikit_learn": sklearn.__version__,
        },
        "methodology_statement": METHODOLOGY_STATEMENT,
        "limitations": [
            (
                "Dataset integralmente sintético, sem validação "
                "com dados reais."
            ),
            (
                "As métricas não comprovam desempenho real, "
                "causalidade ou validade externa."
            ),
        ],
    }


def execute_official_holdout(
    context: PreflightContext,
) -> dict[str, Any]:
    """Consome o holdout uma única vez e preserva a evidência resultante."""
    started_at_utc = _utc_now()

    marker_payload = _build_marker_payload(
        context,
        started_at_utc,
    )

    _write_json_exclusive(
        context.paths.execution_marker_path,
        marker_payload,
    )

    try:
        evaluation = (
            holdout_evaluation.evaluate_frozen_holdout(
                context.split
            )
        )

        _dump_model_atomically(
            evaluation.fitted_candidate,
            context.paths.model_path,
        )

        model_sha256 = _calculate_sha256(
            context.paths.model_path
        )
        completed_at_utc = _utc_now()

        result_payload = _build_result_payload(
            context=context,
            evaluation=evaluation,
            model_sha256=model_sha256,
            started_at_utc=started_at_utc,
            completed_at_utc=completed_at_utc,
        )

        _write_json_exclusive(
            context.paths.result_path,
            result_payload,
        )
    except Exception as error:
        raise HoldoutRunnerError(
            "A execução oficial foi iniciada e o marcador foi "
            "preservado. NÃO execute novamente o holdout sem uma "
            "decisão formal sobre a falha. "
            f"Erro original: {error}"
        ) from error

    return result_payload


def _build_paths() -> RunnerPaths:
    """Retorna exclusivamente os caminhos oficiais congelados."""
    return RunnerPaths(
        dataset_path=DEFAULT_DATASET_PATH.resolve(),
        metadata_path=DEFAULT_METADATA_PATH.resolve(),
        execution_marker_path=(
            DEFAULT_EXECUTION_MARKER_PATH.resolve()
        ),
        result_path=DEFAULT_RESULT_PATH.resolve(),
        model_path=DEFAULT_MODEL_PATH.resolve(),
    )


def _parse_arguments(
    arguments: Sequence[str] | None = None,
) -> argparse.Namespace:
    """Lê os argumentos explícitos da execução controlada."""
    parser = argparse.ArgumentParser(
        description=(
            "Prepara ou executa uma única vez o holdout oficial "
            "da solução congelada da issue #86."
        )
    )

    mode = parser.add_mutually_exclusive_group(
        required=True
    )
    mode.add_argument(
        "--preflight",
        action="store_true",
        help=(
            "Valida ambiente, dataset, Git e split sem avaliar "
            "o holdout."
        ),
    )
    mode.add_argument(
        "--execute",
        action="store_true",
        help="Executa a avaliação oficial única.",
    )

    parser.add_argument(
        "--confirmation",
        default="",
        help=(
            "Confirmação obrigatória para --execute. "
            "Não é necessária em --preflight."
        ),
    )

    return parser.parse_args(arguments)


def main(
    arguments: Sequence[str] | None = None,
) -> int:
    """Ponto de entrada explícito; nunca executado durante import."""
    parsed = _parse_arguments(arguments)

    if (
        parsed.execute
        and parsed.confirmation != EXECUTION_CONFIRMATION
    ):
        raise HoldoutRunnerError(
            "Confirmação inválida. A execução oficial foi recusada."
        )

    paths = _build_paths()
    context = run_preflight(paths)

    if parsed.preflight:
        print("Preflight do holdout: APROVADO")
        print(f"Branch: {context.branch}")
        print(f"Commit: {context.source_commit}")
        print(
            "Dataset SHA-256: "
            f"{context.dataset_sha256}"
        )
        print("Holdout de desempenho: NÃO AVALIADO")
        return 0

    result = execute_official_holdout(
        context
    )

    print("Avaliação oficial do holdout: CONCLUÍDA")
    print(
        "Resultado: "
        f"{context.paths.result_path}"
    )
    print(
        "Modelo: "
        f"{context.paths.model_path}"
    )
    print(
        "F1-macro: "
        f"{result['holdout']['f1_macro']:.12f}"
    )
    print(
        "Log loss: "
        f"{result['holdout']['log_loss']:.12f}"
    )
    print(
        "Brier multiclasses: "
        f"{result['holdout']['brier_multiclass']:.12f}"
    )

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except HoldoutRunnerError as error:
        print(
            f"ERRO: {error}",
            file=sys.stderr,
        )
        raise SystemExit(1) from error
