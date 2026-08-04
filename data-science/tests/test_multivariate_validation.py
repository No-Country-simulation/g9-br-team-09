"""Testes da validação multivariada com splits explícitos."""

import sys
from math import isfinite
from pathlib import Path

import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import dataset  # noqa: E402
import multivariate_validation  # noqa: E402
import data_split  # noqa: E402
import schema  # noqa: E402


def _build_explicit_splits() -> data_split.DataSplit:
    """Cria os splits reproduzíveis usados nos testes."""
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    return data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )


def test_mutual_information_usa_somente_treino_e_e_reprodutivel() -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    y_train = split.y_train

    original_x_train = x_train.copy(deep=True)
    original_y_train = y_train.copy(deep=True)

    first_results = (
        multivariate_validation.calculate_mutual_information(
            x_train,
            y_train,
            seed=schema.RANDOM_SEED,
        )
    )
    second_results = (
        multivariate_validation.calculate_mutual_information(
            x_train,
            y_train,
            seed=schema.RANDOM_SEED,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for value in first_results.values():
        assert isinstance(value, float)
        assert isfinite(value)
        assert value >= 0.0

    pd.testing.assert_frame_equal(x_train, original_x_train)
    pd.testing.assert_series_equal(y_train, original_y_train)


def test_modelos_individuais_usam_splits_explicitos() -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    x_validation = split.x_validation
    y_train = split.y_train
    y_validation = split.y_validation

    original_x_train = x_train.copy(deep=True)
    original_x_validation = x_validation.copy(deep=True)
    original_y_train = y_train.copy(deep=True)
    original_y_validation = y_validation.copy(deep=True)

    first_results = (
        multivariate_validation.evaluate_single_feature_logistic(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
        )
    )
    second_results = (
        multivariate_validation.evaluate_single_feature_logistic(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for score in first_results.values():
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0

    pd.testing.assert_frame_equal(x_train, original_x_train)
    pd.testing.assert_frame_equal(
        x_validation,
        original_x_validation,
    )
    pd.testing.assert_series_equal(y_train, original_y_train)
    pd.testing.assert_series_equal(
        y_validation,
        original_y_validation,
    )


def test_ablacao_usa_splits_explicitos_e_e_reprodutivel() -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    x_validation = split.x_validation
    y_train = split.y_train
    y_validation = split.y_validation

    first_results = (
        multivariate_validation.evaluate_leave_one_feature_out_logistic(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
        )
    )
    second_results = (
        multivariate_validation.evaluate_leave_one_feature_out_logistic(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for score in first_results.values():
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0


def test_permutation_importance_usa_splits_explicitos() -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    x_validation = split.x_validation
    y_train = split.y_train
    y_validation = split.y_validation

    first_results = (
        multivariate_validation.calculate_permutation_importance(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
            n_repeats=3,
        )
    )
    second_results = (
        multivariate_validation.calculate_permutation_importance(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
            n_repeats=3,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for metrics in first_results.values():
        assert set(metrics) == {
            "importance_mean",
            "importance_std",
        }
        assert isinstance(metrics["importance_mean"], float)
        assert isinstance(metrics["importance_std"], float)
        assert isfinite(metrics["importance_mean"])
        assert isfinite(metrics["importance_std"])
        assert metrics["importance_std"] >= 0.0


@pytest.mark.parametrize(
    "invalid_n_repeats",
    (
        0,
        -1,
        True,
        1.5,
    ),
)
def test_permutation_importance_rejeita_repeticoes_invalidas(
    invalid_n_repeats: object,
) -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    x_validation = split.x_validation
    y_train = split.y_train
    y_validation = split.y_validation

    with pytest.raises(
        ValueError,
        match="n_repeats deve ser um inteiro maior que zero",
    ):
        multivariate_validation.calculate_permutation_importance(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=schema.RANDOM_SEED,
            n_repeats=invalid_n_repeats,
        )


def test_diagnostico_rejeita_conjunto_incompleto_de_features() -> None:
    split = _build_explicit_splits()
    x_train = split.x_train
    y_train = split.y_train

    incomplete_x_train = x_train.drop(
        columns=["tipo_imovel"],
    )

    with pytest.raises(
        ValueError,
        match=(
            "As features devem corresponder exatamente "
            "às cinco features de produção"
        ),
    ):
        multivariate_validation.calculate_mutual_information(
            incomplete_x_train,
            y_train,
            seed=schema.RANDOM_SEED,
        )
