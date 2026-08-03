"""Validação multivariada com splits explícitos do Dataset EnergIAI V2.

Este módulo executa diagnósticos usando somente os conjuntos de treino e
validação recebidos. Ele não cria novos splits, não consulta o conjunto de
teste, não serializa modelos e não altera os DataFrames ou Series de entrada.
"""

from __future__ import annotations

from math import isfinite

import numpy as np
import pandas as pd
from sklearn.feature_selection import mutual_info_classif
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, make_scorer
from sklearn.pipeline import Pipeline

import baseline_benchmark
import schema


CONTINUOUS_FEATURES = ("consumo_kwh",)
DISCRETE_FEATURE_MASK = tuple(
    feature not in CONTINUOUS_FEATURES
    for feature in schema.FEATURE_COLUMNS
)


def _validate_seed(seed: int) -> None:
    """Valida a seed usada pelos diagnósticos reproduzíveis."""
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise ValueError("seed deve ser um inteiro")


def _validate_feature_frame(
    features: pd.DataFrame,
    frame_name: str,
) -> None:
    """Valida um conjunto de features de produção."""
    if not isinstance(features, pd.DataFrame):
        raise TypeError(f"{frame_name} deve ser um pandas.DataFrame")

    if tuple(features.columns) != schema.FEATURE_COLUMNS:
        raise ValueError(
            "As features devem corresponder exatamente "
            "às cinco features de produção"
        )

    if features.empty:
        raise ValueError(f"{frame_name} não pode estar vazio")

    if features.index.has_duplicates:
        raise ValueError(f"{frame_name} possui índices duplicados")

    if features.isna().any().any():
        raise ValueError(f"{frame_name} contém valores nulos")

    numeric_features = (
        "consumo_kwh",
        "quantidade_equipamentos",
        "horas_alto_consumo",
    )

    non_numeric_features = [
        feature
        for feature in numeric_features
        if not pd.api.types.is_numeric_dtype(features[feature])
    ]

    if non_numeric_features:
        raise TypeError(
            f"{frame_name} contém features com tipo não numérico: "
            + ", ".join(non_numeric_features)
        )

    numeric_values = features.loc[
        :,
        list(numeric_features),
    ].to_numpy(dtype=float)

    if not np.isfinite(numeric_values).all():
        raise ValueError(f"{frame_name} contém valores não finitos")

    if not pd.api.types.is_bool_dtype(features["uso_horario_pico"]):
        raise TypeError(
            f"{frame_name}.uso_horario_pico deve possuir tipo booleano"
        )

    observed_property_types = set(
        features["tipo_imovel"].astype(str).unique()
    )
    unexpected_property_types = sorted(
        observed_property_types - set(schema.PROPERTY_TYPES)
    )

    if unexpected_property_types:
        raise ValueError(
            f"{frame_name} contém tipos de imóvel inválidos: "
            + ", ".join(unexpected_property_types)
        )


def _validate_target(
    target: pd.Series,
    target_name: str,
    expected_index: pd.Index,
) -> None:
    """Valida o target correspondente a um conjunto de features."""
    if not isinstance(target, pd.Series):
        raise TypeError(f"{target_name} deve ser uma pandas.Series")

    if target.empty:
        raise ValueError(f"{target_name} não pode estar vazio")

    if target.index.has_duplicates:
        raise ValueError(f"{target_name} possui índices duplicados")

    if not target.index.equals(expected_index):
        raise ValueError(
            f"{target_name} possui índices desalinhados com as features"
        )

    if target.isna().any():
        raise ValueError(f"{target_name} contém valores nulos")

    observed_categories = set(target.astype(str).unique())
    unexpected_categories = sorted(
        observed_categories - set(schema.ENERGY_CATEGORIES)
    )
    missing_categories = sorted(
        set(schema.ENERGY_CATEGORIES) - observed_categories
    )

    if unexpected_categories:
        raise ValueError(
            f"{target_name} contém categorias inválidas: "
            + ", ".join(unexpected_categories)
        )

    if missing_categories:
        raise ValueError(
            f"{target_name} não contém todas as categorias obrigatórias: "
            + ", ".join(missing_categories)
        )


def _validate_training_data(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    seed: int,
) -> None:
    """Valida o conjunto de treino usado por um diagnóstico."""
    _validate_seed(seed)
    _validate_feature_frame(x_train, "x_train")
    _validate_target(y_train, "y_train", x_train.index)


def _validate_explicit_splits(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int,
) -> None:
    """Valida treino e validação sem criar nova divisão."""
    _validate_training_data(x_train, y_train, seed)
    _validate_feature_frame(x_validation, "x_validation")
    _validate_target(
        y_validation,
        "y_validation",
        x_validation.index,
    )

    if not set(x_train.index).isdisjoint(x_validation.index):
        raise ValueError(
            "x_train e x_validation possuem índices sobrepostos"
        )


def _calculate_f1_macro(
    expected: pd.Series,
    predicted: np.ndarray,
) -> float:
    """Calcula F1-macro com a ordem oficial das categorias."""
    score = float(
        f1_score(
            expected,
            predicted,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )

    if not isfinite(score):
        raise RuntimeError("O F1-macro calculado não é finito")

    return score


def _build_logistic_pipeline(
    feature_columns: tuple[str, ...],
    seed: int,
) -> Pipeline:
    """Cria uma Regressão Logística com pré-processamento encapsulado."""
    return Pipeline(
        steps=[
            (
                "preprocessor",
                baseline_benchmark.build_preprocessor(feature_columns),
            ),
            (
                "classifier",
                LogisticRegression(
                    max_iter=2_000,
                    random_state=seed,
                ),
            ),
        ]
    )


def calculate_mutual_information(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Calcula mutual information usando exclusivamente o treino."""
    _validate_training_data(x_train, y_train, seed)

    encoded_features = x_train.loc[
        :,
        list(schema.FEATURE_COLUMNS),
    ].copy()

    encoded_features["uso_horario_pico"] = (
        encoded_features["uso_horario_pico"].astype("int8")
    )
    encoded_features["tipo_imovel"] = pd.Categorical(
        encoded_features["tipo_imovel"],
        categories=schema.PROPERTY_TYPES,
    ).codes

    if (encoded_features["tipo_imovel"] < 0).any():
        raise ValueError(
            "x_train contém tipo_imovel sem codificação válida"
        )

    encoded_target = pd.Categorical(
        y_train,
        categories=schema.ENERGY_CATEGORIES,
    ).codes

    if (encoded_target < 0).any():
        raise ValueError("y_train contém categoria sem codificação válida")

    scores = mutual_info_classif(
        encoded_features,
        encoded_target,
        discrete_features=np.asarray(
            DISCRETE_FEATURE_MASK,
            dtype=bool,
        ),
        random_state=seed,
        n_jobs=1,
    )

    if len(scores) != len(schema.FEATURE_COLUMNS):
        raise RuntimeError(
            "A mutual information não retornou um valor por feature"
        )

    if not np.isfinite(scores).all():
        raise RuntimeError(
            "A mutual information retornou valores não finitos"
        )

    if (scores < 0.0).any():
        raise RuntimeError(
            "A mutual information retornou valores negativos"
        )

    return {
        feature: float(score)
        for feature, score in zip(
            schema.FEATURE_COLUMNS,
            scores,
            strict=True,
        )
    }


def evaluate_single_feature_logistic(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia uma Regressão Logística com uma feature por vez."""
    _validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )

    results: dict[str, float] = {}

    for feature in schema.FEATURE_COLUMNS:
        selected_columns = [feature]
        model = _build_logistic_pipeline((feature,), seed)

        model.fit(
            x_train.loc[:, selected_columns],
            y_train,
        )
        predictions = model.predict(
            x_validation.loc[:, selected_columns]
        )

        results[feature] = _calculate_f1_macro(
            y_validation,
            predictions,
        )

    return results


def evaluate_leave_one_feature_out_logistic(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia a remoção de uma feature por vez."""
    _validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )

    feature_sets = (
        baseline_benchmark.build_leave_one_feature_out_feature_sets()
    )
    results: dict[str, float] = {}

    for removed_feature, selected_features in feature_sets.items():
        selected_columns = list(selected_features)
        model = _build_logistic_pipeline(selected_features, seed)

        model.fit(
            x_train.loc[:, selected_columns],
            y_train,
        )
        predictions = model.predict(
            x_validation.loc[:, selected_columns]
        )

        results[removed_feature] = _calculate_f1_macro(
            y_validation,
            predictions,
        )

    return results


def calculate_permutation_importance(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
    n_repeats: int = 10,
) -> dict[str, dict[str, float]]:
    """Calcula permutation importance no conjunto de validação."""
    if (
        isinstance(n_repeats, bool)
        or not isinstance(n_repeats, int)
        or n_repeats <= 0
    ):
        raise ValueError(
            "n_repeats deve ser um inteiro maior que zero"
        )

    _validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )

    model = _build_logistic_pipeline(
        schema.FEATURE_COLUMNS,
        seed,
    )
    model.fit(x_train, y_train)

    f1_macro_scorer = make_scorer(
        f1_score,
        labels=list(schema.ENERGY_CATEGORIES),
        average="macro",
        zero_division=0,
    )

    permutation_results = permutation_importance(
        model,
        x_validation,
        y_validation,
        scoring=f1_macro_scorer,
        n_repeats=n_repeats,
        random_state=seed,
        n_jobs=1,
    )

    if not np.isfinite(permutation_results.importances_mean).all():
        raise RuntimeError(
            "A permutation importance retornou médias não finitas"
        )

    if not np.isfinite(permutation_results.importances_std).all():
        raise RuntimeError(
            "A permutation importance retornou desvios não finitos"
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
