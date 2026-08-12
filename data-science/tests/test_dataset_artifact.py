"""Testes da geração e persistência do dataset candidato EnergIAI V2."""

from __future__ import annotations

import hashlib
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import dataset_artifact  # noqa: E402
import schema  # noqa: E402
import scenarios  # noqa: E402


FIXED_GENERATED_AT_UTC = datetime(
    2026,
    8,
    1,
    0,
    1,
    43,
    910061,
    tzinfo=timezone.utc,
)


def test_validate_final_dataset_accepts_valid_candidate() -> None:
    sample = dataset_artifact.generate_candidate_dataset()

    summary = dataset_artifact.validate_final_dataset(sample)

    assert summary["record_count"] == schema.DATASET_SIZE
    assert summary["duplicate_count"] == 0
    assert summary["null_count"] == 0
    assert summary["non_finite_count"] == 0
    assert summary["boundary_count"] == 150
    assert summary["rare_count"] == 250
    assert summary["outlier_count"] == 150
    assert set(summary["class_distribution"]) == set(
        schema.ENERGY_CATEGORIES
    )
    assert set(summary["property_distribution"]) == set(
        schema.PROPERTY_TYPES
    )


def test_validate_final_dataset_rejects_integral_duplicates() -> None:
    sample = dataset_artifact.generate_candidate_dataset()
    sample.iloc[-1] = sample.iloc[0]

    with pytest.raises(
        ValueError,
        match="dataset final não pode conter duplicatas integrais",
    ):
        dataset_artifact.validate_final_dataset(sample)


def test_validate_final_dataset_rejects_null_value() -> None:
    sample = dataset_artifact.generate_candidate_dataset()
    sample.loc[0, "consumo_kwh"] = float("nan")

    with pytest.raises(
        ValueError,
        match="dataset final não pode conter valores nulos",
    ):
        dataset_artifact.validate_final_dataset(sample)


def test_validate_final_dataset_rejects_invalid_schema() -> None:
    sample = dataset_artifact.generate_candidate_dataset()
    sample = sample.drop(columns=["categoria"])

    with pytest.raises(
        ValueError,
        match="schema final inválido",
    ):
        dataset_artifact.validate_final_dataset(sample)


def test_validate_final_dataset_rejects_invalid_record_count() -> None:
    sample = dataset_artifact.generate_candidate_dataset().iloc[:-1]

    with pytest.raises(
        ValueError,
        match="dataset final deve possuir exatamente 5000 registros",
    ):
        dataset_artifact.validate_final_dataset(sample)


def test_write_dataset_artifacts_creates_csv_and_metadata(
    tmp_path: Path,
) -> None:
    result = dataset_artifact.write_dataset_artifacts(
        output_directory=tmp_path,
        commit_or_tag="test-commit",
        generated_at_utc=FIXED_GENERATED_AT_UTC,
    )

    csv_path = tmp_path / dataset_artifact.DATASET_FILENAME
    metadata_path = tmp_path / dataset_artifact.METADATA_FILENAME

    assert result.csv_path == csv_path
    assert result.metadata_path == metadata_path
    assert csv_path.is_file()
    assert metadata_path.is_file()

    reloaded = pd.read_csv(csv_path)
    dataset_artifact.validate_final_dataset(reloaded)

    metadata = json.loads(
        metadata_path.read_text(encoding="utf-8")
    )
    csv_hash = hashlib.sha256(csv_path.read_bytes()).hexdigest()

    assert metadata["dataset_version"] == "2.0.0-candidate"
    assert metadata["record_count"] == schema.DATASET_SIZE
    assert metadata["seed"] == schema.RANDOM_SEED
    assert metadata["sha256"] == csv_hash
    assert metadata["commit_or_tag"] == "test-commit"
    assert metadata["generated_at_utc"] == (
        FIXED_GENERATED_AT_UTC.isoformat()
    )
    assert metadata["class_distribution"] == (
        reloaded[schema.TARGET_COLUMN]
        .value_counts()
        .sort_index()
        .to_dict()
    )
    assert metadata["property_distribution"] == (
        reloaded["tipo_imovel"]
        .value_counts()
        .sort_index()
        .to_dict()
    )
    assert metadata["scenario_quotas"] == {
        "boundary_ratio": scenarios.BOUNDARY_CASE_RATIO,
        "rare_ratio": scenarios.RARE_CASE_RATIO,
        "plausible_outlier_ratio": (
            scenarios.PLAUSIBLE_OUTLIER_RATIO
        ),
    }
    assert metadata["limitations"]


def test_write_dataset_artifacts_is_reproducible(
    tmp_path: Path,
) -> None:
    first_directory = tmp_path / "first"
    second_directory = tmp_path / "second"

    first_result = dataset_artifact.write_dataset_artifacts(
        output_directory=first_directory,
        commit_or_tag="same-commit",
        generated_at_utc=FIXED_GENERATED_AT_UTC,
    )
    second_result = dataset_artifact.write_dataset_artifacts(
        output_directory=second_directory,
        commit_or_tag="same-commit",
        generated_at_utc=FIXED_GENERATED_AT_UTC,
    )

    assert (
        first_result.csv_path.read_bytes()
        == second_result.csv_path.read_bytes()
    )
    assert (
        first_result.metadata_path.read_bytes()
        == second_result.metadata_path.read_bytes()
    )


def test_write_dataset_artifacts_uses_current_utc_by_default(
    tmp_path: Path,
) -> None:
    before_generation = datetime.now(timezone.utc)

    result = dataset_artifact.write_dataset_artifacts(
        output_directory=tmp_path,
        commit_or_tag="test-current-time",
    )

    after_generation = datetime.now(timezone.utc)
    metadata = json.loads(
        result.metadata_path.read_text(encoding="utf-8")
    )
    generated_at_utc = datetime.fromisoformat(
        metadata["generated_at_utc"]
    )

    assert generated_at_utc.utcoffset() == timedelta(0)
    assert before_generation <= generated_at_utc <= after_generation


def test_write_dataset_artifacts_rejects_naive_generated_at_utc(
    tmp_path: Path,
) -> None:
    with pytest.raises(
        ValueError,
        match="generated_at_utc deve possuir timezone UTC",
    ):
        dataset_artifact.write_dataset_artifacts(
            output_directory=tmp_path,
            commit_or_tag="test-commit",
            generated_at_utc=datetime(2026, 8, 1),
        )


def test_write_dataset_artifacts_rejects_non_utc_generated_at_utc(
    tmp_path: Path,
) -> None:
    with pytest.raises(
        ValueError,
        match="generated_at_utc deve estar em UTC",
    ):
        dataset_artifact.write_dataset_artifacts(
            output_directory=tmp_path,
            commit_or_tag="test-commit",
            generated_at_utc=datetime(
                2026,
                8,
                1,
                tzinfo=timezone(timedelta(hours=-3)),
            ),
        )


def test_write_dataset_artifacts_rejects_invalid_generated_at_utc_type(
    tmp_path: Path,
) -> None:
    with pytest.raises(
        TypeError,
        match="generated_at_utc deve ser datetime ou None",
    ):
        dataset_artifact.write_dataset_artifacts(
            output_directory=tmp_path,
            commit_or_tag="test-commit",
            generated_at_utc="2026-08-01T00:00:00+00:00",  # type: ignore[arg-type]
        )


def test_write_dataset_artifacts_rejects_empty_commit_or_tag(
    tmp_path: Path,
) -> None:
    with pytest.raises(
        ValueError,
        match="commit_or_tag não pode estar vazio",
    ):
        dataset_artifact.write_dataset_artifacts(
            output_directory=tmp_path,
            commit_or_tag=" ",
        )
