"""Comparação reproduzível dos modelos candidatos da issue #86.

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
from sklearn.model_selection import (
    RepeatedStratifiedKFold,
    cross_val_score,
)
from sklearn.tree import DecisionTreeClassifier

import modeling_pipeline
import schema


BASELINE_MODEL_NAME: Final[str] = "dummy"
CANDIDATE_MODEL_NAMES: Final[tuple[str, ...]] = (
    "regressao_logistica",
    "arvore_decisao",
    "random_forest",
    "hist_gradient_boosting",
)
MODEL_NAMES: Final[tuple[str, ...]] = (
    BASELINE_MODEL_NAME,
    *CANDIDATE_MODEL_NAMES,
)
BASELINE_ROLE: Final[str] = "baseline"
CANDIDATE_ROLE: Final[str] = "candidate"
CV_N_SPLITS: Final[int] = 5
CV_N_REPEATS: Final[int] = 3
CV_TOTAL_SPLITS: Final[int] = CV_N_SPLITS * CV_N_REPEATS
FINALIST_CUTOFF_GAP: Final[float] = 0.01
PREDICTION_TIMING_REPEATS: Final[int] = 5

_NUMERIC_FEATURES: Final[tuple[str, ...]] = (
    "consumo_kwh",
    "quantidade_equipamentos",
    "horas_alto_consumo",
)
__all__ = [
    "BASELINE_MODEL_NAME",
    "BASELINE_ROLE",
    "CANDIDATE_MODEL_NAMES",
    "CANDIDATE_ROLE",
    "CV_N_REPEATS",
    "CV_N_SPLITS",
    "CV_TOTAL_SPLITS",
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
    """Resultado reproduzível de um modelo em CV e validação fixa."""

    model_name: str
    role: str
    cv_f1_macro_scores: tuple[float, ...]
    cv_f1_macro_mean: float
    cv_f1_macro_std: float
    validation_f1_macro: float
    fit_time_seconds: float
    prediction_time_seconds: float


@dataclass(frozen=True)
class ProvisionalFinalistSelection:
    """Ranking dos candidatos anterior à validação humana do Marco 1."""

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


def _validate_cv_capacity(y_train: pd.Series) -> None:
    """Garante pelo menos um registro de cada classe em cada fold."""
    class_counts = y_train.value_counts()
    insufficient_categories = sorted(
        category
        for category in schema.ENERGY_CATEGORIES
        if int(class_counts.get(category, 0)) < CV_N_SPLITS
    )

    if insufficient_categories:
        raise ValueError(
            "y_train deve conter ao menos "
            f"{CV_N_SPLITS} registros de cada categoria para CV: "
            + ", ".join(insufficient_categories)
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
    _validate_cv_capacity(y_train)
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


def _normalize_cv_scores(
    raw_scores: np.ndarray,
    model_name: str,
) -> tuple[float, ...]:
    """Normaliza e valida os 15 F1-macro produzidos pela CV repetida."""
    normalized_scores = tuple(
        float(score)
        for score in np.asarray(raw_scores, dtype=float).tolist()
    )

    if len(normalized_scores) != CV_TOTAL_SPLITS:
        raise ModelComparisonError(
            "A validação cruzada repetida não retornou "
            f"{CV_TOTAL_SPLITS} scores para o modelo {model_name}"
        )

    if any(
        not isfinite(score) or not 0.0 <= score <= 1.0
        for score in normalized_scores
    ):
        raise ModelComparisonError(
            f"A validação cruzada retornou score inválido para {model_name}"
        )

    return normalized_scores


def _model_role(model_name: str) -> str:
    """Retorna o papel metodológico congelado do modelo."""
    if model_name == BASELINE_MODEL_NAME:
        return BASELINE_ROLE

    if model_name in CANDIDATE_MODEL_NAMES:
        return CANDIDATE_ROLE

    raise ModelComparisonError(
        f"Nome de modelo inválido: {model_name}"
    )


def build_candidate_estimators(
    seed: int = schema.RANDOM_SEED,
) -> dict[str, BaseEstimator]:
    """Cria a baseline e os quatro candidatos não ajustados."""
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


def _cross_validate_candidate(
    model_name: str,
    estimator: BaseEstimator,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    seed: int,
) -> tuple[float, ...]:
    """Executa CV estratificada repetida somente no conjunto de treino."""
    model = modeling_pipeline.build_model_pipeline(
        estimator,
        schema.FEATURE_COLUMNS,
    )
    cv = RepeatedStratifiedKFold(
        n_splits=CV_N_SPLITS,
        n_repeats=CV_N_REPEATS,
        random_state=seed,
    )

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", ConvergenceWarning)
            raw_scores = cross_val_score(
                model,
                x_train,
                y_train,
                scoring="f1_macro",
                cv=cv,
                n_jobs=1,
                error_score="raise",
            )
    except ConvergenceWarning as error:
        raise ModelConvergenceError(
            "Falha de convergência na validação cruzada do modelo "
            f"{model_name}: {error}"
        ) from error
    except Exception as error:
        raise ModelComparisonError(
            "Falha na validação cruzada do modelo "
            f"{model_name}: {error}"
        ) from error

    return _normalize_cv_scores(raw_scores, model_name)


def _fit_and_validate_candidate(
    model_name: str,
    estimator: BaseEstimator,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
) -> tuple[float, float, float]:
    """Ajusta no treino e avalia separadamente na validação fixa."""
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

    return (
        _calculate_f1_macro(
            y_validation,
            reference_predictions,
        ),
        _validate_elapsed_time(
            fit_elapsed,
            "Tempo de ajuste",
            model_name,
        ),
        _validate_elapsed_time(
            median(prediction_durations),
            "Tempo mediano de predição",
            model_name,
        ),
    )


def _evaluate_candidate(
    model_name: str,
    estimator: BaseEstimator,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int,
) -> ModelComparisonResult:
    """Produz CV no treino e avaliação separada na validação fixa."""
    cv_scores = _cross_validate_candidate(
        model_name,
        estimator,
        x_train,
        y_train,
        seed,
    )
    (
        validation_f1_macro,
        fit_time_seconds,
        prediction_time_seconds,
    ) = _fit_and_validate_candidate(
        model_name,
        estimator,
        x_train,
        y_train,
        x_validation,
        y_validation,
    )

    return ModelComparisonResult(
        model_name=model_name,
        role=_model_role(model_name),
        cv_f1_macro_scores=cv_scores,
        cv_f1_macro_mean=float(np.mean(cv_scores)),
        cv_f1_macro_std=float(np.std(cv_scores, ddof=0)),
        validation_f1_macro=validation_f1_macro,
        fit_time_seconds=fit_time_seconds,
        prediction_time_seconds=prediction_time_seconds,
    )


def compare_candidate_models(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> tuple[ModelComparisonResult, ...]:
    """Compara a baseline e os candidatos sem acessar o conjunto reservado."""
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
            seed,
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

        expected_role = _model_role(result.model_name)
        if result.role != expected_role:
            raise ValueError(
                f"Papel inválido para {result.model_name}: {result.role}"
            )

        if not isinstance(result.cv_f1_macro_scores, tuple):
            raise TypeError(
                "cv_f1_macro_scores deve ser uma tupla"
            )

        normalized_scores = _normalize_cv_scores(
            np.asarray(result.cv_f1_macro_scores, dtype=float),
            result.model_name,
        )
        expected_mean = float(np.mean(normalized_scores))
        expected_std = float(np.std(normalized_scores, ddof=0))

        if (
            not isfinite(result.cv_f1_macro_mean)
            or not 0.0 <= result.cv_f1_macro_mean <= 1.0
            or not isclose(
                result.cv_f1_macro_mean,
                expected_mean,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
        ):
            raise ValueError(
                f"Média de CV inválida para {result.model_name}"
            )

        if (
            not isfinite(result.cv_f1_macro_std)
            or result.cv_f1_macro_std < 0.0
            or not isclose(
                result.cv_f1_macro_std,
                expected_std,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
        ):
            raise ValueError(
                f"Estabilidade de CV inválida para {result.model_name}"
            )

        if (
            not isfinite(result.validation_f1_macro)
            or not 0.0 <= result.validation_f1_macro <= 1.0
        ):
            raise ValueError(
                f"F1-macro de validação inválido para {result.model_name}"
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
    """Ordena somente candidatos pela média do F1-macro da CV."""
    _validate_comparison_results(results)
    canonical_position = {
        model_name: position
        for position, model_name in enumerate(CANDIDATE_MODEL_NAMES)
    }
    candidate_results = tuple(
        result
        for result in results
        if result.model_name in CANDIDATE_MODEL_NAMES
    )
    ranked_results = tuple(
        sorted(
            candidate_results,
            key=lambda result: (
                -result.cv_f1_macro_mean,
                canonical_position[result.model_name],
            ),
        )
    )
    cutoff_f1_gap = float(
        ranked_results[1].cv_f1_macro_mean
        - ranked_results[2].cv_f1_macro_mean
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
