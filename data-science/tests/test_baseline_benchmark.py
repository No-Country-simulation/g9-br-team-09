"""Testes do benchmark diagnóstico da baseline determinística."""

import sys
from pathlib import Path

import pandas as pd


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import baseline_benchmark  # noqa: E402
import dataset  # noqa: E402
import schema  # noqa: E402


def test_preparacao_benchmark_usa_somente_features_de_producao() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        200,
        seed=schema.RANDOM_SEED,
    )

    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    assert isinstance(features, pd.DataFrame)
    assert isinstance(target, pd.Series)
    assert tuple(features.columns) == schema.FEATURE_COLUMNS
    assert target.name == schema.TARGET_COLUMN
    assert len(features) == len(sample)
    assert len(target) == len(sample)
    assert not set(schema.PROHIBITED_MODEL_FEATURES).intersection(
        features.columns
    )

    pd.testing.assert_series_equal(
        target,
        sample[schema.TARGET_COLUMN],
    )


def test_divisao_benchmark_e_estratificada_e_reprodutivel() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    first_split = baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )
    second_split = baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )

    (
        x_train,
        x_validation,
        x_test,
        y_train,
        y_validation,
        y_test,
    ) = first_split

    assert len(x_train) == 3_500
    assert len(x_validation) == 750
    assert len(x_test) == 750

    assert len(y_train) == 3_500
    assert len(y_validation) == 750
    assert len(y_test) == 750

    assert tuple(x_train.columns) == schema.FEATURE_COLUMNS
    assert tuple(x_validation.columns) == schema.FEATURE_COLUMNS
    assert tuple(x_test.columns) == schema.FEATURE_COLUMNS

    train_indexes = set(x_train.index)
    validation_indexes = set(x_validation.index)
    test_indexes = set(x_test.index)

    assert train_indexes.isdisjoint(validation_indexes)
    assert train_indexes.isdisjoint(test_indexes)
    assert validation_indexes.isdisjoint(test_indexes)
    assert (
        train_indexes
        | validation_indexes
        | test_indexes
    ) == set(features.index)

    expected_distribution = (
        target.value_counts(normalize=True).sort_index()
    )

    for subset_target in (y_train, y_validation, y_test):
        observed_distribution = (
            subset_target.value_counts(normalize=True).sort_index()
        )

        assert (
            observed_distribution
            .sub(expected_distribution)
            .abs()
            .max()
        ) <= 0.01

    for first_part, second_part in zip(
        first_split,
        second_split,
        strict=True,
    ):
        if isinstance(first_part, pd.DataFrame):
            pd.testing.assert_frame_equal(first_part, second_part)
        else:
            pd.testing.assert_series_equal(first_part, second_part)


def test_dummy_baseline_retorna_f1_macro_valido_e_reprodutivel() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_dummy_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_dummy_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
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
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_logistic_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_logistic_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
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
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = baseline_benchmark.split_benchmark_data(
        features,
        target,
        seed=schema.RANDOM_SEED,
    )

    first_score = baseline_benchmark.evaluate_tree_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed=schema.RANDOM_SEED,
    )
    second_score = baseline_benchmark.evaluate_tree_baseline(
        x_train,
        y_train,
        x_validation,
        y_validation,
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
    features, target = baseline_benchmark.prepare_benchmark_data(sample)

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
