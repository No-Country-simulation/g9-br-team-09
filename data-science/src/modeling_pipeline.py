"""Fábricas de pré-processamento e pipelines do Dataset EnergIAI V2.

Este módulo centraliza componentes reutilizáveis de modelagem. As fábricas
não ajustam transformadores ou estimadores, não criam splits, não consultam
o holdout e não alteram os dados recebidos.
"""

from __future__ import annotations

from collections.abc import Sequence
from typing import Final

from sklearn.base import BaseEstimator, clone
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

import schema


CATEGORICAL_FEATURES: Final[tuple[str, ...]] = (
    "tipo_imovel",
)
NUMERICAL_FEATURES: Final[tuple[str, ...]] = tuple(
    feature
    for feature in schema.FEATURE_COLUMNS
    if feature not in CATEGORICAL_FEATURES
)

__all__ = [
    "CATEGORICAL_FEATURES",
    "NUMERICAL_FEATURES",
    "build_model_pipeline",
    "build_preprocessor",
]


def _normalize_feature_columns(
    feature_columns: Sequence[str],
) -> tuple[str, ...]:
    """Valida e normaliza as features solicitadas."""
    if isinstance(feature_columns, (str, bytes)):
        raise TypeError(
            "feature_columns deve ser uma sequência de strings"
        )

    if not isinstance(feature_columns, Sequence):
        raise TypeError(
            "feature_columns deve ser uma sequência de strings"
        )

    normalized_columns = tuple(feature_columns)

    if not normalized_columns:
        raise ValueError("feature_columns não pode estar vazio")

    if any(
        not isinstance(feature, str)
        for feature in normalized_columns
    ):
        raise TypeError(
            "feature_columns deve conter somente strings"
        )

    seen: set[str] = set()
    duplicated_features: list[str] = []

    for feature in normalized_columns:
        if (
            feature in seen
            and feature not in duplicated_features
        ):
            duplicated_features.append(feature)

        seen.add(feature)

    if duplicated_features:
        raise ValueError(
            "feature_columns contém duplicatas: "
            + ", ".join(duplicated_features)
        )

    invalid_features = sorted(
        set(normalized_columns).difference(
            schema.FEATURE_COLUMNS
        )
    )

    if invalid_features:
        raise ValueError(
            "Features inválidas: "
            + ", ".join(invalid_features)
        )

    return normalized_columns


def build_preprocessor(
    feature_columns: Sequence[str] = schema.FEATURE_COLUMNS,
) -> ColumnTransformer:
    """Cria um ColumnTransformer não ajustado."""
    normalized_columns = _normalize_feature_columns(
        feature_columns
    )

    numerical_features = [
        feature
        for feature in normalized_columns
        if feature in NUMERICAL_FEATURES
    ]
    categorical_features = [
        feature
        for feature in normalized_columns
        if feature in CATEGORICAL_FEATURES
    ]

    transformers: list[
        tuple[str, BaseEstimator, list[str]]
    ] = []

    if numerical_features:
        transformers.append(
            (
                "numerical",
                StandardScaler(),
                numerical_features,
            )
        )

    if categorical_features:
        transformers.append(
            (
                "categorical",
                OneHotEncoder(
                    handle_unknown="ignore",
                    sparse_output=False,
                ),
                categorical_features,
            )
        )

    return ColumnTransformer(
        transformers=transformers,
        remainder="drop",
    )


def build_model_pipeline(
    estimator: BaseEstimator,
    feature_columns: Sequence[str] = schema.FEATURE_COLUMNS,
) -> Pipeline:
    """Cria um Pipeline não ajustado com uma cópia do estimador."""
    if not isinstance(estimator, BaseEstimator):
        raise TypeError(
            "estimator deve ser um estimador scikit-learn"
        )

    return Pipeline(
        steps=[
            (
                "preprocessor",
                build_preprocessor(feature_columns),
            ),
            (
                "classifier",
                clone(estimator),
            ),
        ]
    )
