"""Benchmark diagnóstico da baseline determinística.

Este módulo não altera o gerador, não produz o dataset oficial e não
serializa modelos. Ele utiliza apenas as cinco features de produção.
"""

import pandas as pd
from sklearn.dummy import DummyClassifier
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, make_scorer
from sklearn.tree import DecisionTreeClassifier

import data_split
from modeling_pipeline import (
    build_model_pipeline,
    build_preprocessor as build_preprocessor,
)
import schema



def evaluate_dummy_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia o piso de comparação com a classe mais frequente."""
    model = DummyClassifier(
        strategy="most_frequent",
        random_state=seed,
    )
    model.fit(x_train, y_train)

    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def evaluate_logistic_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia uma Regressão Logística com pré-processamento."""
    model = build_model_pipeline(
        LogisticRegression(
            max_iter=2_000,
            random_state=seed,
        ),
        schema.FEATURE_COLUMNS,
    )

    model.fit(x_train, y_train)
    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def evaluate_tree_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia uma Árvore de Decisão simples com pré-processamento."""
    model = build_model_pipeline(
        DecisionTreeClassifier(
            max_depth=5,
            random_state=seed,
        ),
        schema.FEATURE_COLUMNS,
    )

    model.fit(x_train, y_train)
    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def run_baseline_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Executa os modelos diagnósticos na mesma divisão estratificada."""
    split = data_split.create_stratified_data_split(
        sample,
        seed=seed,
    )

    return {
        "dummy": evaluate_dummy_baseline(
            split.x_train,
            split.y_train,
            split.x_validation,
            split.y_validation,
            seed=seed,
        ),
        "regressao_logistica": evaluate_logistic_baseline(
            split.x_train,
            split.y_train,
            split.x_validation,
            split.y_validation,
            seed=seed,
        ),
        "arvore_decisao": evaluate_tree_baseline(
            split.x_train,
            split.y_train,
            split.x_validation,
            split.y_validation,
            seed=seed,
        ),
    }


def run_single_feature_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia a Regressão Logística com uma feature por vez."""
    split = data_split.create_stratified_data_split(
        sample,
        seed=seed,
    )
    results: dict[str, float] = {}

    for feature in schema.FEATURE_COLUMNS:
        selected_columns = [feature]

        model = build_model_pipeline(
            LogisticRegression(
                max_iter=2_000,
                random_state=seed,
            ),
            (feature,),
        )

        model.fit(
            split.x_train.loc[:, selected_columns],
            split.y_train,
        )
        predictions = model.predict(
            split.x_validation.loc[:, selected_columns]
        )

        results[feature] = float(
            f1_score(
                split.y_validation,
                predictions,
                labels=list(schema.ENERGY_CATEGORIES),
                average="macro",
                zero_division=0,
            )
        )

    return results


def build_leave_one_feature_out_feature_sets() -> dict[str, tuple[str, ...]]:
    """Cria os subconjuntos usados na ablação de uma feature por vez."""
    return {
        removed_feature: tuple(
            feature
            for feature in schema.FEATURE_COLUMNS
            if feature != removed_feature
        )
        for removed_feature in schema.FEATURE_COLUMNS
    }


def run_leave_one_feature_out_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia a Regressão Logística removendo uma feature por execução."""
    split = data_split.create_stratified_data_split(
        sample,
        seed=seed,
    )
    feature_sets = build_leave_one_feature_out_feature_sets()
    results: dict[str, float] = {}

    for removed_feature, selected_features in feature_sets.items():
        selected_columns = list(selected_features)

        model = build_model_pipeline(
            LogisticRegression(
                max_iter=2_000,
                random_state=seed,
            ),
            selected_features,
        )

        model.fit(
            split.x_train.loc[:, selected_columns],
            split.y_train,
        )
        predictions = model.predict(
            split.x_validation.loc[:, selected_columns]
        )

        results[removed_feature] = float(
            f1_score(
                split.y_validation,
                predictions,
                labels=list(schema.ENERGY_CATEGORIES),
                average="macro",
                zero_division=0,
            )
        )

    return results


def run_permutation_importance_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
    n_repeats: int = 10,
) -> dict[str, dict[str, float]]:
    """Calcula a importância por permutação das features originais."""
    if (
        isinstance(n_repeats, bool)
        or not isinstance(n_repeats, int)
        or n_repeats <= 0
    ):
        raise ValueError(
            "n_repeats deve ser um inteiro maior que zero"
        )

    split = data_split.create_stratified_data_split(
        sample,
        seed=seed,
    )

    model = build_model_pipeline(
        LogisticRegression(
            max_iter=2_000,
            random_state=seed,
        ),
        schema.FEATURE_COLUMNS,
    )

    model.fit(split.x_train, split.y_train)

    f1_macro_scorer = make_scorer(
        f1_score,
        labels=list(schema.ENERGY_CATEGORIES),
        average="macro",
        zero_division=0,
    )

    permutation_results = permutation_importance(
        model,
        split.x_validation,
        split.y_validation,
        scoring=f1_macro_scorer,
        n_repeats=n_repeats,
        random_state=seed,
        n_jobs=1,
    )

    return {
        feature: {
            "importance_mean": float(importance_mean),
            "importance_std": float(importance_std),
        }
        for feature, importance_mean, importance_std in zip(
            schema.FEATURE_COLUMNS,
            permutation_results.importances_mean,
            permutation_results.importances_std,
            strict=True,
        )
    }
