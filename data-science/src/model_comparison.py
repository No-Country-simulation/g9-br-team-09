"""Comparação inicial dos cinco modelos candidatos da issue #86.

Este módulo recebe exclusivamente os conjuntos explícitos de treino e
validação. Ele não cria splits, não gera o dataset, não consulta o conjunto
reservado, não ajusta hiperparâmetros e não calibra probabilidades.
"""

from __future__ import annotations

import warnings
from dataclasses import dataclass
from math import isclose, isfinite
from statistics import median
from time import perf_counter
from typing import Final

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import (
    HistGradientBoostingClassifier,
    RandomForestClassifier,
)
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score
from sklearn.tree import DecisionTreeClassifier

import modeling_pipeline
import schema


MODEL_NAMES: Final[tuple[str, ...]] = (
    "dummy",
    "regressao_logistica",
    "arvore_decisao",
    "random_forest",
    "hist_gradient_boosting",
)
FINALIST_CUTOFF_GAP: Final[float] = 0.01
PREDICTION_TIMING_REPEATS: Final[int] = 5

_NUMERIC_FEATURES: Final[tuple[str, ...]] = (
    "consumo_kwh",
    "quantidade_equipamentos",
    "horas_alto_consumo",
)

__all__ = [
    "FINALIST_CUTOFF_GAP",
    "MODEL_NAMES",
    "ModelComparisonError",
    "ModelComparisonResult",
    "ModelConvergenceError",
    "PREDICTION_TIMING_REPEATS",
    "ProvisionalFinalistSelection",
    "build_candidate_estimators",
    "compare_candidate_models",
    "select_provisional_finalists",
]


class ModelComparisonError(RuntimeError):
    """Indica falha operacional durante a comparação de modelos."""


class ModelConvergenceError(ModelComparisonError):
    """Indica convergência insuficiente de um modelo candidato."""


@dataclass(frozen=True)
class ModelComparisonResult:
    """Resultado reproduzível de um modelo na validação explícita."""

    model_name: str
    f1_macro: float
    fit_time_seconds: float
    prediction_time_seconds: float


@dataclass(frozen=True)
class ProvisionalFinalistSelection:
    """Ranking inicial anterior à validação humana do Marco 1."""

    ranked_model_names: tuple[str, ...]
    provisional_finalists: tuple[str, str]
    cutoff_f1_gap: float
    requires_cutoff_review: bool


def _validate_seed(seed: int) -> None:
    """Valida a seed determinística usada pelos estimadores."""
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise TypeError("seed deve ser um inteiro")


def _validate_feature_frame(
    features: pd.DataFrame,
    frame_name: str,
) -> None:
    """Valida um conjunto explícito das cinco features oficiais."""
    if not isinstance(features, pd.DataFrame):
        raise TypeError(f"{frame_name} deve ser um pandas.DataFrame")

    if features.empty:
        raise ValueError(f"{frame_name} não pode estar vazio")

    if features.columns.has_duplicates:
        raise ValueError(f"{frame_name} possui nomes de colunas duplicados")

    if tuple(features.columns) != schema.FEATURE_COLUMNS:
        raise ValueError(
            f"{frame_name} deve conter exatamente as cinco "
            "features oficiais, na ordem definida em schema.FEATURE_COLUMNS"
        )

    if features.index.has_duplicates:
        raise ValueError(f"{frame_name} possui índices duplicados")

    if features.isna().any().any():
        raise ValueError(f"{frame_name} contém valores nulos")

    non_numeric_features = [
        feature
        for feature in _NUMERIC_FEATURES
        if (
            not pd.api.types.is_numeric_dtype(features[feature])
            or pd.api.types.is_bool_dtype(features[feature])
        )
    ]

    if non_numeric_features:
        raise TypeError(
            f"{frame_name} contém features com tipo não numérico: "
            + ", ".join(non_numeric_features)
        )

    numeric_values = features.loc[
        :,
        list(_NUMERIC_FEATURES),
    ].to_numpy(dtype=float)

    if not np.isfinite(numeric_values).all():
        raise ValueError(f"{frame_name} contém valores não finitos")

    for feature in _NUMERIC_FEATURES:
        minimum, maximum = schema.NUMERIC_LIMITS[feature]
        invalid_values = ~features[feature].between(
            minimum,
            maximum,
            inclusive="both",
        )

        if invalid_values.any():
            raise ValueError(
                f"{frame_name}.{feature} contém valores fora "
                f"do intervalo [{minimum}, {maximum}]"
            )

    if not pd.api.types.is_bool_dtype(
        features["uso_horario_pico"]
    ):
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
    """Valida o target correspondente a um conjunto explícito."""
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
    expected_categories = set(schema.ENERGY_CATEGORIES)
    unexpected_categories = sorted(
        observed_categories - expected_categories
    )
    missing_categories = sorted(
        expected_categories - observed_categories
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


def _validate_explicit_splits(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int,
) -> None:
    """Valida treino e validação sem receber o objeto de split completo."""
    _validate_seed(seed)
    _validate_feature_frame(x_train, "x_train")
    _validate_target(y_train, "y_train", x_train.index)
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
    """Calcula e valida o F1-macro na ordem oficial das classes."""
    score = float(
        f1_score(
            expected,
            predicted,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )

    if not isfinite(score) or not 0.0 <= score <= 1.0:
        raise ModelComparisonError(
            "O F1-macro calculado é inválido"
        )

    return score


def _validate_elapsed_time(
    elapsed: float,
    metric_name: str,
    model_name: str,
) -> float:
    """Valida uma duração medida com relógio monotônico."""
    normalized_elapsed = float(elapsed)

    if (
        not isfinite(normalized_elapsed)
        or normalized_elapsed < 0.0
    ):
        raise ModelComparisonError(
            f"{metric_name} inválido para o modelo {model_name}"
        )

    return normalized_elapsed


def build_candidate_estimators(
    seed: int = schema.RANDOM_SEED,
) -> dict[str, BaseEstimator]:
    """Cria novas instâncias dos cinco candidatos não ajustados."""
    _validate_seed(seed)

    return {
        "dummy": DummyClassifier(
            strategy="most_frequent",
            random_state=seed,
        ),
        "regressao_logistica": LogisticRegression(
            solver="lbfgs",
            max_iter=2_000,
            random_state=seed,
        ),
        "arvore_decisao": DecisionTreeClassifier(
            max_depth=5,
            random_state=seed,
        ),
        "random_forest": RandomForestClassifier(
            n_estimators=100,
            random_state=seed,
            n_jobs=1,
        ),
        "hist_gradient_boosting": HistGradientBoostingClassifier(
            max_iter=100,
            early_stopping=False,
            categorical_features=None,
            random_state=seed,
        ),
    }


def _evaluate_candidate(
    model_name: str,
    estimator: BaseEstimator,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
) -> ModelComparisonResult:
    """Ajusta e avalia um candidato com o pipeline comum."""
    model = modeling_pipeline.build_model_pipeline(
        estimator,
        schema.FEATURE_COLUMNS,
    )

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", ConvergenceWarning)

            fit_started_at = perf_counter()
            model.fit(x_train, y_train)
            fit_elapsed = perf_counter() - fit_started_at

            reference_predictions = model.predict(x_validation)
            prediction_durations: list[float] = []

            for _ in range(PREDICTION_TIMING_REPEATS):
                prediction_started_at = perf_counter()
                repeated_predictions = model.predict(x_validation)
                prediction_elapsed = (
                    perf_counter() - prediction_started_at
                )

                if not np.array_equal(
                    reference_predictions,
                    repeated_predictions,
                ):
                    raise ModelComparisonError(
                        "Predições não determinísticas para o modelo "
                        f"{model_name}"
                    )

                prediction_durations.append(
                    _validate_elapsed_time(
                        prediction_elapsed,
                        "Tempo de predição",
                        model_name,
                    )
                )

    except ConvergenceWarning as error:
        raise ModelConvergenceError(
            "Falha de convergência ao avaliar o modelo "
            f"{model_name}: {error}"
        ) from error
    except ModelComparisonError:
        raise
    except Exception as error:
        raise ModelComparisonError(
            f"Falha ao avaliar o modelo {model_name}: {error}"
        ) from error

    return ModelComparisonResult(
        model_name=model_name,
        f1_macro=_calculate_f1_macro(
            y_validation,
            reference_predictions,
        ),
        fit_time_seconds=_validate_elapsed_time(
            fit_elapsed,
            "Tempo de ajuste",
            model_name,
        ),
        prediction_time_seconds=_validate_elapsed_time(
            median(prediction_durations),
            "Tempo mediano de predição",
            model_name,
        ),
    )


def compare_candidate_models(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> tuple[ModelComparisonResult, ...]:
    """Compara os cinco modelos somente em treino e validação."""
    _validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )
    estimators = build_candidate_estimators(seed)

    if tuple(estimators) != MODEL_NAMES:
        raise ModelComparisonError(
            "A fábrica não retornou os cinco modelos na ordem oficial"
        )

    return tuple(
        _evaluate_candidate(
            model_name,
            estimator,
            x_train,
            y_train,
            x_validation,
            y_validation,
        )
        for model_name, estimator in estimators.items()
    )


def _validate_comparison_results(
    results: tuple[ModelComparisonResult, ...],
) -> None:
    """Valida resultados antes do ranking provisório."""
    if not isinstance(results, tuple):
        raise TypeError(
            "results deve ser uma tupla de ModelComparisonResult"
        )

    if len(results) != len(MODEL_NAMES):
        raise ValueError(
            "results deve conter exatamente cinco resultados"
        )

    observed_names: list[str] = []

    for result in results:
        if not isinstance(result, ModelComparisonResult):
            raise TypeError(
                "results deve conter somente ModelComparisonResult"
            )

        if result.model_name not in MODEL_NAMES:
            raise ValueError(
                f"Nome de modelo inválido: {result.model_name}"
            )

        if result.model_name in observed_names:
            raise ValueError(
                f"Nome de modelo duplicado: {result.model_name}"
            )

        observed_names.append(result.model_name)

        if (
            not isfinite(result.f1_macro)
            or not 0.0 <= result.f1_macro <= 1.0
        ):
            raise ValueError(
                f"F1-macro inválido para {result.model_name}"
            )

        for metric_name, value in (
            ("fit_time_seconds", result.fit_time_seconds),
            (
                "prediction_time_seconds",
                result.prediction_time_seconds,
            ),
        ):
            if not isfinite(value) or value < 0.0:
                raise ValueError(
                    f"{metric_name} inválido para {result.model_name}"
                )

    if set(observed_names) != set(MODEL_NAMES):
        raise ValueError(
            "results não contém exatamente os cinco modelos oficiais"
        )


def select_provisional_finalists(
    results: tuple[ModelComparisonResult, ...],
) -> ProvisionalFinalistSelection:
    """Ordena por F1-macro e sinaliza revisão no corte 2º/3º."""
    _validate_comparison_results(results)
    canonical_position = {
        model_name: position
        for position, model_name in enumerate(MODEL_NAMES)
    }
    ranked_results = tuple(
        sorted(
            results,
            key=lambda result: (
                -result.f1_macro,
                canonical_position[result.model_name],
            ),
        )
    )
    cutoff_f1_gap = float(
        ranked_results[1].f1_macro
        - ranked_results[2].f1_macro
    )

    if not isfinite(cutoff_f1_gap) or cutoff_f1_gap < 0.0:
        raise ModelComparisonError(
            "A diferença de F1-macro no corte é inválida"
        )

    return ProvisionalFinalistSelection(
        ranked_model_names=tuple(
            result.model_name
            for result in ranked_results
        ),
        provisional_finalists=(
            ranked_results[0].model_name,
            ranked_results[1].model_name,
        ),
        cutoff_f1_gap=cutoff_f1_gap,
        requires_cutoff_review=(
            cutoff_f1_gap < FINALIST_CUTOFF_GAP
            and not isclose(
                cutoff_f1_gap,
                FINALIST_CUTOFF_GAP,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
        ),
    )
