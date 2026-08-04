"""Testes do benchmark diagnóstico da baseline determinística."""

import sys
from math import isfinite
from pathlib import Path

import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import baseline_benchmark  # noqa: E402
import dataset  # noqa: E402
import data_split  # noqa: E402
import schema  # noqa: E402




def test_dummy_baseline_retorna_f1_macro_valido_e_reprodutivel() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    split = data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_dummy_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_dummy_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )

    assert isinstance(first_score, float)
    assert 0.0 <= first_score <= 1.0
    assert first_score == second_score


def test_regressao_logistica_retorna_f1_macro_valido_e_reprodutivel() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    split = data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_logistic_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_logistic_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )

    assert isinstance(first_score, float)
    assert 0.0 <= first_score <= 1.0
    assert first_score == second_score


def test_arvore_decisao_retorna_f1_macro_valido_e_reprodutivel() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    split = data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_tree_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_tree_baseline(
        split.x_train,
        split.y_train,
        split.x_validation,
        split.y_validation,
        seed=schema.RANDOM_SEED,
    )

    assert isinstance(first_score, float)
    assert 0.0 <= first_score <= 1.0
    assert first_score == second_score


def test_benchmark_consolidado_retorna_metricas_reprodutiveis() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    first_results = baseline_benchmark.run_baseline_benchmark(
        sample,
        seed=schema.RANDOM_SEED,
    )
    second_results = baseline_benchmark.run_baseline_benchmark(
        sample,
        seed=schema.RANDOM_SEED,
    )

    assert set(first_results) == {
        "dummy",
        "regressao_logistica",
        "arvore_decisao",
    }
    assert first_results == second_results

    for score in first_results.values():
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0


def test_preprocessador_suporta_cada_feature_individual() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        200,
        seed=schema.RANDOM_SEED,
    )
    features = sample.loc[
        :,
        list(schema.FEATURE_COLUMNS),
    ].copy()
    target = sample.loc[
        :,
        schema.TARGET_COLUMN,
    ].copy()

    for feature in schema.FEATURE_COLUMNS:
        preprocessor = baseline_benchmark.build_preprocessor(
            (feature,)
        )

        transformed = preprocessor.fit_transform(
            features.loc[:, [feature]],
            target,
        )

        assert transformed.shape[0] == len(sample)
        assert transformed.shape[1] >= 1


def test_regressao_logistica_por_feature_retorna_metricas_reprodutiveis() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    first_results = (
        baseline_benchmark.run_single_feature_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
        )
    )
    second_results = (
        baseline_benchmark.run_single_feature_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for score in first_results.values():
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0


def test_ablation_define_subconjuntos_validos_e_metricas_reprodutiveis() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    original_sample = sample.copy(deep=True)

    feature_sets = (
        baseline_benchmark.build_leave_one_feature_out_feature_sets()
    )

    assert tuple(feature_sets) == schema.FEATURE_COLUMNS

    for removed_feature, selected_features in feature_sets.items():
        assert removed_feature not in selected_features
        assert len(selected_features) == len(schema.FEATURE_COLUMNS) - 1
        assert set(selected_features) == (
            set(schema.FEATURE_COLUMNS) - {removed_feature}
        )

    first_results = (
        baseline_benchmark.run_leave_one_feature_out_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
        )
    )
    second_results = (
        baseline_benchmark.run_leave_one_feature_out_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
        )
    )

    assert tuple(first_results) == schema.FEATURE_COLUMNS
    assert first_results == second_results

    for score in first_results.values():
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0

    pd.testing.assert_frame_equal(sample, original_sample)


def test_permutation_importance_retorna_metricas_reprodutiveis() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    original_sample = sample.copy(deep=True)

    first_results = (
        baseline_benchmark.run_permutation_importance_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
            n_repeats=5,
        )
    )
    second_results = (
        baseline_benchmark.run_permutation_importance_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
            n_repeats=5,
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
    pd.testing.assert_frame_equal(sample, original_sample)


@pytest.mark.parametrize(
    "invalid_n_repeats",
    (
        0,
        -1,
        True,
        1.5,
    ),
)
def test_permutation_importance_rejeita_n_repeats_invalido(
    invalid_n_repeats: object,
) -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        200,
        seed=schema.RANDOM_SEED,
    )

    with pytest.raises(
        ValueError,
        match="n_repeats deve ser um inteiro maior que zero",
    ):
        baseline_benchmark.run_permutation_importance_logistic_benchmark(
            sample,
            seed=schema.RANDOM_SEED,
            n_repeats=invalid_n_repeats,
        )
