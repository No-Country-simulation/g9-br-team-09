"""Geração, validação e persistência do dataset candidato EnergIAI V2."""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

import numpy as np
import pandas as pd

import dataset
import schema
import scenarios


DATASET_VERSION = "2.0.0-candidate"
DATASET_FILENAME = "dataset_energiai_v2.csv"
METADATA_FILENAME = "dataset_energiai_v2.metadata.json"

_CLASS_DISTRIBUTION_TOLERANCE = 0.02
_CONSUMPTION_REPAIR_STEP = 0.01
_MAXIMUM_REPAIR_ATTEMPTS = 10_000

_LIMITATIONS = (
    "Dataset integralmente sintético, sem validação com dados reais.",
    (
        "A categoria e o score de referência são derivados "
        "deterministicamente das cinco features observáveis."
    ),
    (
        "A baseline funciona como oráculo sintético para o MVP "
        "e não comprova capacidade de generalização externa."
    ),
    (
        "O resultado mede a capacidade do modelo de reproduzir padrões "
        "da base sintética sob as condições testadas."
    ),
)


@dataclass(frozen=True)
class DatasetArtifactResult:
    """Caminhos e resumo dos artefatos persistidos."""

    csv_path: Path
    metadata_path: Path
    sha256: str
    summary: dict[str, Any]


def _feature_key(
    sample: pd.DataFrame,
    index: int,
) -> tuple[Any, ...]:
    """Monta a chave das cinco features de um registro."""
    return tuple(
        sample.loc[
            index,
            list(schema.FEATURE_COLUMNS),
        ]
    )


def _candidate_feature_key(
    sample: pd.DataFrame,
    index: int,
    candidate_consumption: float,
) -> tuple[Any, ...]:
    """Monta uma chave candidata com novo consumo."""
    candidate_values = list(
        sample.loc[
            index,
            list(schema.FEATURE_COLUMNS),
        ]
    )
    candidate_values[0] = candidate_consumption

    return tuple(candidate_values)


def _repair_direction(
    original_consumption: float,
    typical_minimum: float,
    typical_maximum: float,
) -> float:
    """Define a direção do ajuste para permanecer em faixa válida."""
    if original_consumption <= typical_minimum:
        return 1.0

    if original_consumption >= typical_maximum:
        return -1.0

    return 1.0


def _candidate_respects_limits(
    candidate: float,
    is_rare: bool,
    typical_minimum: float,
    typical_maximum: float,
    absolute_minimum: float,
    absolute_maximum: float,
) -> bool:
    """Verifica os limites aplicáveis ao consumo candidato."""
    if not absolute_minimum <= candidate <= absolute_maximum:
        return False

    if (
        not is_rare
        and not typical_minimum <= candidate <= typical_maximum
    ):
        return False

    return True


def repair_feature_duplicates(
    sample: pd.DataFrame,
) -> tuple[pd.DataFrame, int]:
    """Repara duplicatas das features com ajustes mínimos de consumo.

    O primeiro registro de cada grupo duplicado é preservado. Os demais
    recebem ajustes determinísticos de 0,01 kWh em ``consumo_kwh``.

    Registros típicos permanecem dentro da faixa típica de seu imóvel.
    Registros raros permanecem dentro dos limites absolutos do schema.
    """
    if not isinstance(sample, pd.DataFrame):
        raise TypeError("sample deve ser um pandas.DataFrame")

    missing_columns = sorted(
        set(schema.DATASET_COLUMNS).difference(sample.columns)
    )

    if missing_columns:
        raise ValueError(
            "colunas obrigatórias ausentes para reparo: "
            + ", ".join(missing_columns)
        )

    repaired = sample.copy(deep=True)
    feature_columns = list(schema.FEATURE_COLUMNS)

    used_feature_keys = {
        tuple(row)
        for row in repaired.loc[
            :,
            feature_columns,
        ].itertuples(
            index=False,
            name=None,
        )
    }

    duplicate_mask = repaired.duplicated(
        subset=feature_columns,
        keep=False,
    )

    duplicate_groups = repaired.loc[
        duplicate_mask
    ].groupby(
        feature_columns,
        sort=True,
        dropna=False,
    )

    repair_count = 0

    for _, group in duplicate_groups:
        for raw_index in group.index[1:]:
            index = int(raw_index)
            property_type = str(
                repaired.at[index, "tipo_imovel"]
            )
            original_consumption = float(
                repaired.at[index, "consumo_kwh"]
            )

            typical_minimum, typical_maximum = (
                scenarios.TYPICAL_RANGES[
                    property_type
                ]["consumo_kwh"]
            )
            absolute_minimum, absolute_maximum = (
                schema.NUMERIC_LIMITS["consumo_kwh"]
            )
            direction = _repair_direction(
                original_consumption,
                typical_minimum,
                typical_maximum,
            )
            is_rare = bool(
                repaired.at[index, "caso_raro"]
            )
            candidate_found = False

            for attempt in range(
                1,
                _MAXIMUM_REPAIR_ATTEMPTS + 1,
            ):
                candidate = round(
                    original_consumption
                    + direction
                    * attempt
                    * _CONSUMPTION_REPAIR_STEP,
                    2,
                )

                if not _candidate_respects_limits(
                    candidate,
                    is_rare,
                    typical_minimum,
                    typical_maximum,
                    absolute_minimum,
                    absolute_maximum,
                ):
                    continue

                candidate_key = _candidate_feature_key(
                    repaired,
                    index,
                    candidate,
                )

                if candidate_key in used_feature_keys:
                    continue

                repaired.at[
                    index,
                    "consumo_kwh",
                ] = candidate
                used_feature_keys.add(candidate_key)
                repair_count += 1
                candidate_found = True
                break

            if not candidate_found:
                original_key = _feature_key(
                    repaired,
                    index,
                )
                raise RuntimeError(
                    "não foi possível reparar a duplicata "
                    f"do índice {index}: {original_key}"
                )

    recalculated_scores = dataset.calculate_reference_scores(
        repaired
    )
    recalculated_categories = (
        dataset.categorize_reference_scores(
            recalculated_scores
        )
    )

    repaired["score_referencia"] = recalculated_scores
    repaired[schema.TARGET_COLUMN] = (
        recalculated_categories
    )

    if repaired.duplicated(
        subset=feature_columns
    ).any():
        raise RuntimeError(
            "o reparo não eliminou todas as duplicatas "
            "das features"
        )

    if repaired.duplicated().any():
        raise RuntimeError(
            "o reparo não eliminou todas as duplicatas integrais"
        )

    return (
        repaired.loc[
            :,
            list(schema.DATASET_COLUMNS),
        ],
        repair_count,
    )


def _generate_candidate_with_repair_summary(
    sample_size: int,
    seed: int,
) -> tuple[pd.DataFrame, int]:
    """Gera o candidato e aplica o reparo determinístico."""
    if sample_size != schema.DATASET_SIZE:
        raise ValueError(
            "sample_size deve ser igual a "
            f"{schema.DATASET_SIZE} para o artefato candidato"
        )

    if seed != schema.RANDOM_SEED:
        raise ValueError(
            "seed deve ser igual a "
            f"{schema.RANDOM_SEED} para o artefato candidato"
        )

    raw_sample = (
        dataset.generate_audited_sample_with_rare_cases(
            sample_size,
            seed=seed,
        )
    )

    return repair_feature_duplicates(raw_sample)


def generate_candidate_dataset(
    sample_size: int = schema.DATASET_SIZE,
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Gera o dataset candidato auditado e sem duplicatas."""
    candidate, _ = _generate_candidate_with_repair_summary(
        sample_size,
        seed,
    )

    return candidate


def _validate_schema(sample: pd.DataFrame) -> None:
    """Valida quantidade, nomes e ordem das colunas."""
    if sample.shape[0] != schema.DATASET_SIZE:
        raise ValueError(
            "dataset final deve possuir exatamente "
            f"{schema.DATASET_SIZE} registros"
        )

    if tuple(sample.columns) != schema.DATASET_COLUMNS:
        raise ValueError(
            "schema final inválido: as colunas devem corresponder "
            "exatamente a schema.DATASET_COLUMNS"
        )

    if sample.columns.duplicated().any():
        raise ValueError(
            "schema final inválido: não pode haver colunas duplicadas"
        )


def _validate_nulls_and_duplicates(
    sample: pd.DataFrame,
) -> None:
    """Valida nulos e duplicatas integrais."""
    if sample.isna().any().any():
        raise ValueError(
            "dataset final não pode conter valores nulos"
        )

    if sample.duplicated().any():
        raise ValueError(
            "dataset final não pode conter duplicatas integrais"
        )

    if sample.duplicated(
        subset=list(schema.FEATURE_COLUMNS)
    ).any():
        raise ValueError(
            "dataset final não pode conter duplicatas "
            "nas cinco features"
        )


def _validate_numeric_values(sample: pd.DataFrame) -> None:
    """Valida finitude e limites absolutos das colunas numéricas."""
    for column, limits in schema.NUMERIC_LIMITS.items():
        values = pd.to_numeric(
            sample[column],
            errors="coerce",
        ).to_numpy(dtype=float)

        if not np.isfinite(values).all():
            raise ValueError(
                f"{column} deve conter apenas valores numéricos finitos"
            )

        minimum, maximum = limits

        if (
            np.any(values < minimum)
            or np.any(values > maximum)
        ):
            raise ValueError(
                f"{column} deve respeitar os limites "
                f"de {minimum} a {maximum}"
            )


def _validate_domains(sample: pd.DataFrame) -> None:
    """Valida categorias, tipos de imóvel, cenários e flags."""
    observed_property_types = set(
        sample["tipo_imovel"].astype(str).unique()
    )
    expected_property_types = set(schema.PROPERTY_TYPES)

    if observed_property_types != expected_property_types:
        raise ValueError(
            "dataset final deve conter exatamente os seis "
            "tipos de imóvel válidos"
        )

    observed_categories = set(
        sample[schema.TARGET_COLUMN].astype(str).unique()
    )
    expected_categories = set(schema.ENERGY_CATEGORIES)

    if observed_categories != expected_categories:
        raise ValueError(
            "dataset final deve conter exatamente "
            "as três categorias energéticas válidas"
        )

    observed_scenarios = set(
        sample["tipo_cenario"].astype(str).unique()
    )
    expected_scenarios = set(scenarios.SCENARIO_TYPES)

    if observed_scenarios != expected_scenarios:
        raise ValueError(
            "dataset final contém tipos de cenário inválidos "
            "ou ausentes"
        )

    for column in (
        "caso_fronteira",
        "caso_raro",
        "outlier_plausivel",
    ):
        if not pd.api.types.is_bool_dtype(sample[column]):
            raise ValueError(
                f"{column} deve possuir tipo booleano"
            )


def _validate_class_distribution(
    sample: pd.DataFrame,
) -> None:
    """Valida a distribuição final das classes."""
    observed_distribution = (
        sample[schema.TARGET_COLUMN]
        .value_counts(normalize=True)
        .to_dict()
    )

    for category, expected_proportion in (
        scenarios.TARGET_CATEGORY_DISTRIBUTION.items()
    ):
        observed_proportion = float(
            observed_distribution.get(
                category,
                0.0,
            )
        )

        if (
            abs(
                observed_proportion
                - expected_proportion
            )
            > _CLASS_DISTRIBUTION_TOLERANCE
        ):
            raise ValueError(
                "distribuição final das classes fora da tolerância "
                f"para {category}: "
                f"observado={observed_proportion:.4f}, "
                f"esperado={expected_proportion:.4f}"
            )


def _validate_scenarios(sample: pd.DataFrame) -> None:
    """Valida cotas e relações entre os cenários auditáveis."""
    boundary_flags = sample["caso_fronteira"]
    rare_flags = sample["caso_raro"]
    outlier_flags = sample["outlier_plausivel"]

    expected_boundary_count = int(
        round(
            schema.DATASET_SIZE
            * scenarios.BOUNDARY_CASE_RATIO
        )
    )
    expected_rare_count = int(
        round(
            schema.DATASET_SIZE
            * scenarios.RARE_CASE_RATIO
        )
    )
    expected_outlier_count = int(
        round(
            schema.DATASET_SIZE
            * scenarios.PLAUSIBLE_OUTLIER_RATIO
        )
    )

    if int(boundary_flags.sum()) != expected_boundary_count:
        raise ValueError(
            "quantidade final de casos de fronteira inválida"
        )

    if int(rare_flags.sum()) != expected_rare_count:
        raise ValueError(
            "quantidade final de casos raros inválida"
        )

    if int(outlier_flags.sum()) != expected_outlier_count:
        raise ValueError(
            "quantidade final de outliers plausíveis inválida"
        )

    if bool((boundary_flags & rare_flags).any()):
        raise ValueError(
            "casos raros não podem também ser casos de fronteira"
        )

    if bool((outlier_flags & ~rare_flags).any()):
        raise ValueError(
            "outliers plausíveis devem ser subconjunto "
            "dos casos raros"
        )

    if bool((outlier_flags & boundary_flags).any()):
        raise ValueError(
            "outliers plausíveis não podem ser casos de fronteira"
        )

    if not sample.loc[
        boundary_flags,
        "tipo_cenario",
    ].eq("FRONTEIRA").all():
        raise ValueError(
            "flags de fronteira e tipo_cenario estão inconsistentes"
        )

    if not sample.loc[
        rare_flags,
        "tipo_cenario",
    ].eq("RARO_EXTREMO").all():
        raise ValueError(
            "flags de casos raros e tipo_cenario estão inconsistentes"
        )

    typical_flags = ~boundary_flags & ~rare_flags

    if not sample.loc[
        typical_flags,
        "tipo_cenario",
    ].eq("TIPICO").all():
        raise ValueError(
            "registros típicos e tipo_cenario estão inconsistentes"
        )


def _validate_reference_outputs(
    sample: pd.DataFrame,
) -> None:
    """Valida a coerência do score e do target com as features."""
    recalculated_scores = dataset.calculate_reference_scores(
        sample
    )
    recalculated_categories = (
        dataset.categorize_reference_scores(
            recalculated_scores
        )
    )

    if not np.array_equal(
        sample["score_referencia"].to_numpy(),
        recalculated_scores,
    ):
        raise ValueError(
            "score_referencia está inconsistente "
            "com as features observáveis"
        )

    if not np.array_equal(
        sample[schema.TARGET_COLUMN].to_numpy(),
        recalculated_categories,
    ):
        raise ValueError(
            "categoria está inconsistente com score_referencia"
        )


def _validate_generation_lot(
    sample: pd.DataFrame,
) -> None:
    """Valida o identificador reproduzível do lote."""
    expected_lot = (
        f"energiai-v2-seed-{schema.RANDOM_SEED}"
        f"-size-{schema.DATASET_SIZE}"
    )

    if sample["lote_geracao"].nunique() != 1:
        raise ValueError(
            "dataset final deve possuir um único lote de geração"
        )

    if str(
        sample["lote_geracao"].iat[0]
    ) != expected_lot:
        raise ValueError(
            "lote de geração final inválido"
        )


def validate_final_dataset(
    sample: pd.DataFrame,
) -> dict[str, Any]:
    """Valida integralmente o dataset candidato e retorna seu resumo."""
    if not isinstance(sample, pd.DataFrame):
        raise TypeError(
            "sample deve ser um pandas.DataFrame"
        )

    _validate_schema(sample)
    _validate_nulls_and_duplicates(sample)
    _validate_numeric_values(sample)
    _validate_domains(sample)
    _validate_class_distribution(sample)
    _validate_scenarios(sample)
    _validate_reference_outputs(sample)
    _validate_generation_lot(sample)

    numeric_columns = tuple(schema.NUMERIC_LIMITS)
    numeric_values = sample.loc[
        :,
        list(numeric_columns),
    ].to_numpy(dtype=float)

    return {
        "record_count": int(len(sample)),
        "column_count": int(
            len(sample.columns)
        ),
        "duplicate_count": int(
            sample.duplicated().sum()
        ),
        "feature_duplicate_count": int(
            sample.duplicated(
                subset=list(schema.FEATURE_COLUMNS)
            ).sum()
        ),
        "null_count": int(
            sample.isna().sum().sum()
        ),
        "non_finite_count": int(
            np.size(numeric_values)
            - np.isfinite(
                numeric_values
            ).sum()
        ),
        "class_distribution": (
            sample[schema.TARGET_COLUMN]
            .value_counts()
            .sort_index()
            .astype(int)
            .to_dict()
        ),
        "property_distribution": (
            sample["tipo_imovel"]
            .value_counts()
            .sort_index()
            .astype(int)
            .to_dict()
        ),
        "scenario_distribution": (
            sample["tipo_cenario"]
            .value_counts()
            .sort_index()
            .astype(int)
            .to_dict()
        ),
        "boundary_count": int(
            sample["caso_fronteira"].sum()
        ),
        "rare_count": int(
            sample["caso_raro"].sum()
        ),
        "outlier_count": int(
            sample["outlier_plausivel"].sum()
        ),
        "generation_lot": str(
            sample["lote_geracao"].iat[0]
        ),
    }


def _calculate_sha256(file_path: Path) -> str:
    """Calcula o SHA-256 de um arquivo."""
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        for chunk in iter(
            lambda: file.read(1024 * 1024),
            b"",
        ):
            digest.update(chunk)

    return digest.hexdigest()


def _build_metadata(
    summary: dict[str, Any],
    csv_hash: str,
    commit_or_tag: str,
    repair_count: int,
) -> dict[str, Any]:
    """Monta os metadados do dataset candidato."""
    return {
        "dataset_name": "EnergIAI Dataset V2",
        "dataset_version": DATASET_VERSION,
        "artifact_status": "CANDIDATE",
        "generated_at_utc": datetime.now(
            timezone.utc
        ).isoformat(),
        "seed": schema.RANDOM_SEED,
        "record_count": summary[
            "record_count"
        ],
        "column_count": summary[
            "column_count"
        ],
        "schema": list(
            schema.DATASET_COLUMNS
        ),
        "features": list(
            schema.FEATURE_COLUMNS
        ),
        "target": schema.TARGET_COLUMN,
        "classes": list(
            schema.ENERGY_CATEGORIES
        ),
        "property_types": list(
            schema.PROPERTY_TYPES
        ),
        "class_distribution": summary[
            "class_distribution"
        ],
        "property_distribution": summary[
            "property_distribution"
        ],
        "scenario_distribution": summary[
            "scenario_distribution"
        ],
        "scenario_quotas": {
            "boundary_ratio": (
                scenarios.BOUNDARY_CASE_RATIO
            ),
            "rare_ratio": (
                scenarios.RARE_CASE_RATIO
            ),
            "plausible_outlier_ratio": (
                scenarios.PLAUSIBLE_OUTLIER_RATIO
            ),
        },
        "numeric_limits": {
            column: {
                "minimum": limits[0],
                "maximum": limits[1],
            }
            for column, limits
            in schema.NUMERIC_LIMITS.items()
        },
        "generation_formula": {
            "baseline_type": (
                "deterministic_synthetic_oracle"
            ),
            "score_source": (
                "five_observable_features"
            ),
            "category_source": (
                "reference_score_ranges"
            ),
            "reference_score_category_ranges": {
                category: {
                    "minimum": limits[0],
                    "maximum": limits[1],
                }
                for category, limits
                in scenarios
                .REFERENCE_SCORE_CATEGORY_RANGES
                .items()
            },
        },
        "duplicate_repair": {
            "feature": "consumo_kwh",
            "step": _CONSUMPTION_REPAIR_STEP,
            "strategy": (
                "deterministic_inward_adjustment"
            ),
            "preserves_score_and_category": True,
        },
        "repairs": repair_count,
        "rejections": 0,
        "duplicate_count": summary[
            "duplicate_count"
        ],
        "feature_duplicate_count": summary[
            "feature_duplicate_count"
        ],
        "null_count": summary[
            "null_count"
        ],
        "non_finite_count": summary[
            "non_finite_count"
        ],
        "sha256": csv_hash,
        "python_version": (
            platform.python_version()
        ),
        "library_versions": {
            "numpy": np.__version__,
            "pandas": pd.__version__,
        },
        "limitations": list(
            _LIMITATIONS
        ),
        "commit_or_tag": commit_or_tag,
    }


def write_dataset_artifacts(
    output_directory: Path | str,
    commit_or_tag: str,
) -> DatasetArtifactResult:
    """Gera, valida e persiste CSV e metadados do dataset candidato."""
    normalized_commit_or_tag = (
        commit_or_tag.strip()
    )

    if not normalized_commit_or_tag:
        raise ValueError(
            "commit_or_tag não pode estar vazio"
        )

    normalized_output_directory = Path(
        output_directory
    )
    normalized_output_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    sample, repair_count = (
        _generate_candidate_with_repair_summary(
            schema.DATASET_SIZE,
            schema.RANDOM_SEED,
        )
    )
    summary = validate_final_dataset(
        sample
    )

    csv_path = (
        normalized_output_directory
        / DATASET_FILENAME
    )
    metadata_path = (
        normalized_output_directory
        / METADATA_FILENAME
    )

    sample.to_csv(
        csv_path,
        index=False,
        encoding="utf-8",
        lineterminator="\n",
        float_format="%.6f",
    )

    reloaded_sample = pd.read_csv(
        csv_path
    )
    validate_final_dataset(
        reloaded_sample
    )

    csv_hash = _calculate_sha256(
        csv_path
    )
    metadata = _build_metadata(
        summary,
        csv_hash,
        normalized_commit_or_tag,
        repair_count,
    )

    metadata_path.write_text(
        json.dumps(
            metadata,
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )

    return DatasetArtifactResult(
        csv_path=csv_path,
        metadata_path=metadata_path,
        sha256=csv_hash,
        summary=summary,
    )


def _parse_arguments(
    arguments: Sequence[str] | None = None,
) -> argparse.Namespace:
    """Lê os parâmetros do comando de geração."""
    parser = argparse.ArgumentParser(
        description=(
            "Gera o CSV e os metadados do dataset "
            "candidato EnergIAI V2."
        )
    )
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path(
            "data-science/data"
        ),
        help=(
            "Diretório de saída do CSV "
            "e dos metadados."
        ),
    )
    parser.add_argument(
        "--commit-or-tag",
        required=True,
        help=(
            "Commit ou tag de origem "
            "registrado nos metadados."
        ),
    )

    return parser.parse_args(
        arguments
    )


def main(
    arguments: Sequence[str] | None = None,
) -> int:
    """Executa a geração reproduzível dos artefatos."""
    parsed_arguments = _parse_arguments(
        arguments
    )
    result = write_dataset_artifacts(
        output_directory=(
            parsed_arguments.output_directory
        ),
        commit_or_tag=(
            parsed_arguments.commit_or_tag
        ),
    )

    print(f"CSV: {result.csv_path}")
    print(
        f"Metadados: {result.metadata_path}"
    )
    print(f"SHA-256: {result.sha256}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
