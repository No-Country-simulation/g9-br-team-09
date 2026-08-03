"""Testes da validação multivariada com splits explícitos."""

import sys
from math import isfinite
from pathlib import Path

import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import baseline_benchmark  # noqa: E402
import dataset  # noqa: E402
import multivariate_validation  # noqa: E402
import schema  # noqa: E402


def _build_explicit_splits() -> tuple[
    pd.DataFrame,
    pd.DataFrame,
    pd.DataFrame,
    pd.Series,
    pd.Series,
    pd.Series,
]:
    """Cria os splits reproduzíveis usados nos testes."""
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    return baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )


def test_mutual_information_usa_somente_treino_e_e_reprodutivel() -> None:
    (
        x_train,
        _,
        _,
        y_train,
        _,
        _,
    ) = _build_explicit_splits()

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
    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = _build_explicit_splits()

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
    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = _build_explicit_splits()

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
    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = _build_explicit_splits()

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
    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = _build_explicit_splits()

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
    (
        x_train,
        _,
        _,
        y_train,
        _,
        _,
    ) = _build_explicit_splits()

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
