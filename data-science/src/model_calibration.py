"""Infraestrutura de calibração dos finalistas da issue #86.

Este módulo reconstrói exclusivamente os dois finalistas com os
hiperparâmetros vencedores já registrados, cria candidatos de
probabilidade raw, sigmoid e isotonic e permite avaliá-los somente
sobre treino e validação explícitos.

A seleção pré-Marco 2 é congelada como Random Forest com calibração
isotonic. Nenhuma função deste módulo recebe ou consulta o conjunto
reservado.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from math import isfinite
from typing import Final, Literal

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import brier_score_loss, f1_score, log_loss
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline

import model_comparison
import model_tuning
import modeling_pipeline
import schema


RAW_METHOD: Final[str] = "raw"
SIGMOID_METHOD: Final[str] = "sigmoid"
ISOTONIC_METHOD: Final[str] = "isotonic"

PROBABILITY_METHODS: Final[tuple[str, str, str]] = (
    RAW_METHOD,
    SIGMOID_METHOD,
    ISOTONIC_METHOD,
)

ProbabilityMethod = Literal["raw", "sigmoid", "isotonic"]

CALIBRATION_CV_N_SPLITS: Final[int] = 5
CALIBRATION_CV_SHUFFLE: Final[bool] = True
CALIBRATION_ENSEMBLE: Final[bool] = False
CALIBRATION_N_JOBS: Final[int] = 1

SELECTED_MODEL_NAME: Final[str] = "random_forest"
SELECTED_PROBABILITY_METHOD: Final[ProbabilityMethod] = ISOTONIC_METHOD


__all__ = [
    "CALIBRATION_CV_N_SPLITS",
    "CALIBRATION_CV_SHUFFLE",
    "CALIBRATION_ENSEMBLE",
    "CALIBRATION_N_JOBS",
    "ISOTONIC_METHOD",
    "ModelCalibrationError",
    "PROBABILITY_METHODS",
    "ProbabilityEvaluationResult",
    "ProbabilityMethod",
    "RAW_METHOD",
    "SELECTED_MODEL_NAME",
    "SELECTED_PROBABILITY_METHOD",
    "SIGMOID_METHOD",
    "build_calibration_cv",
    "build_probability_candidate",
    "build_selected_probability_candidate",
    "build_tuned_finalist_pipeline",
    "evaluate_probability_candidate",
]


class ModelCalibrationError(RuntimeError):
    """Indica falha operacional na preparação ou avaliação da calibração."""


@dataclass(frozen=True)
class ProbabilityEvaluationResult:
    """Resultado de um candidato probabilístico na validação fixa."""

    model_name: str
    method: ProbabilityMethod
    classes: tuple[str, ...]
    validation_f1_macro: float
    validation_log_loss: float
    validation_brier_score: float
    fitted_candidate: BaseEstimator = field(
        repr=False,
        compare=False,
    )


def _validate_seed(seed: int) -> None:
    """Valida a seed determinística da calibração."""
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise TypeError("seed deve ser um inteiro")


def _validate_model_name(model_name: str) -> None:
    """Restringe a calibração aos dois finalistas do Marco 1."""
    if not isinstance(model_name, str):
        raise TypeError("model_name deve ser uma string")

    if model_name not in model_tuning.FINALIST_MODEL_NAMES:
        raise ValueError(
            "model_name deve ser um dos finalistas: "
            + ", ".join(model_tuning.FINALIST_MODEL_NAMES)
        )


def _validate_probability_method(method: str) -> None:
    """Restringe a comparação a raw, sigmoid e isotonic."""
    if not isinstance(method, str):
        raise TypeError("method deve ser uma string")

    if method not in PROBABILITY_METHODS:
        raise ValueError(
            "method deve ser um dos métodos permitidos: "
            + ", ".join(PROBABILITY_METHODS)
        )


def build_tuned_finalist_pipeline(
    model_name: str,
    seed: int = schema.RANDOM_SEED,
) -> Pipeline:
    """Reconstrói um finalista com os hiperparâmetros vencedores."""
    _validate_model_name(model_name)
    _validate_seed(seed)

    estimators = model_comparison.build_candidate_estimators(seed=seed)
    estimator = estimators[model_name]

    pipeline = modeling_pipeline.build_model_pipeline(
        estimator,
        schema.FEATURE_COLUMNS,
    )

    tuned_params = model_tuning.build_tuned_finalist_params()[model_name]

    try:
        pipeline.set_params(**tuned_params)
    except (TypeError, ValueError) as error:
        raise ModelCalibrationError(
            "Falha ao aplicar os hiperparâmetros vencedores "
            f"ao finalista {model_name}: {error}"
        ) from error

    return pipeline


def build_calibration_cv(
    seed: int = schema.RANDOM_SEED,
) -> StratifiedKFold:
    """Cria a CV interna usada exclusivamente para calibração."""
    _validate_seed(seed)

    return StratifiedKFold(
        n_splits=CALIBRATION_CV_N_SPLITS,
        shuffle=CALIBRATION_CV_SHUFFLE,
        random_state=seed,
    )


def build_probability_candidate(
    model_name: str,
    method: ProbabilityMethod,
    seed: int = schema.RANDOM_SEED,
) -> BaseEstimator:
    """Cria um candidato raw ou calibrado ainda não ajustado."""
    _validate_model_name(model_name)
    _validate_probability_method(method)
    _validate_seed(seed)

    pipeline = build_tuned_finalist_pipeline(
        model_name=model_name,
        seed=seed,
    )

    if method == RAW_METHOD:
        return pipeline

    return CalibratedClassifierCV(
        estimator=pipeline,
        method=method,
        cv=build_calibration_cv(seed=seed),
        n_jobs=CALIBRATION_N_JOBS,
        ensemble=CALIBRATION_ENSEMBLE,
    )


def build_selected_probability_candidate(
    seed: int = schema.RANDOM_SEED,
) -> BaseEstimator:
    """Reconstrói a solução probabilística selecionada antes do Marco 2."""
    _validate_seed(seed)

    return build_probability_candidate(
        model_name=SELECTED_MODEL_NAME,
        method=SELECTED_PROBABILITY_METHOD,
        seed=seed,
    )


def _extract_fitted_classes(
    candidate: BaseEstimator,
) -> tuple[str, ...]:
    """Obtém e valida a ordem real das classes do estimador ajustado."""
    try:
        raw_classes = np.asarray(candidate.classes_, dtype=object)
    except AttributeError as error:
        raise ModelCalibrationError(
            "O candidato ajustado não expôs classes_: "
            f"{error}"
        ) from error

    if raw_classes.ndim != 1:
        raise ModelCalibrationError(
            "classes_ deve possuir exatamente uma dimensão"
        )

    classes = tuple(str(value) for value in raw_classes.tolist())

    if len(classes) != len(schema.ENERGY_CATEGORIES):
        raise ModelCalibrationError(
            "O candidato não expôs exatamente as três classes oficiais"
        )

    if len(set(classes)) != len(classes):
        raise ModelCalibrationError(
            "O candidato expôs classes duplicadas"
        )

    expected_classes = set(schema.ENERGY_CATEGORIES)
    observed_classes = set(classes)

    if observed_classes != expected_classes:
        missing = sorted(expected_classes - observed_classes)
        unexpected = sorted(observed_classes - expected_classes)

        raise ModelCalibrationError(
            "As classes do candidato divergem do contrato oficial; "
            f"ausentes={missing}, inesperadas={unexpected}"
        )

    return classes


def _normalize_probability_matrix(
    raw_probabilities: object,
    sample_count: int,
    class_count: int,
) -> np.ndarray:
    """Valida forma, finitude, limites e soma das probabilidades."""
    try:
        probabilities = np.asarray(
            raw_probabilities,
            dtype=float,
        )
    except (TypeError, ValueError) as error:
        raise ModelCalibrationError(
            "Não foi possível converter predict_proba para float: "
            f"{error}"
        ) from error

    expected_shape = (sample_count, class_count)

    if probabilities.shape != expected_shape:
        raise ModelCalibrationError(
            "predict_proba retornou shape inválido: "
            f"{probabilities.shape}; esperado={expected_shape}"
        )

    if not np.isfinite(probabilities).all():
        raise ModelCalibrationError(
            "predict_proba retornou probabilidades não finitas"
        )

    if (
        np.any(probabilities < 0.0)
        or np.any(probabilities > 1.0)
    ):
        raise ModelCalibrationError(
            "predict_proba retornou valores fora do intervalo [0, 1]"
        )

    row_sums = probabilities.sum(axis=1)

    if not np.allclose(
        row_sums,
        1.0,
        rtol=1e-7,
        atol=1e-9,
    ):
        raise ModelCalibrationError(
            "As probabilidades de cada registro devem somar 1"
        )

    return probabilities


def _build_metric_probability_view(
    probabilities: np.ndarray,
    classes: tuple[str, ...],
) -> tuple[tuple[str, ...], np.ndarray]:
    """Ordena colunas explicitamente para métricas probabilísticas."""
    metric_labels = tuple(sorted(schema.ENERGY_CATEGORIES))

    try:
        column_indices = tuple(
            classes.index(label)
            for label in metric_labels
        )
    except ValueError as error:
        raise ModelCalibrationError(
            "Não foi possível alinhar probabilidades às classes: "
            f"{error}"
        ) from error

    return (
        metric_labels,
        probabilities[:, column_indices],
    )


def _validate_f1_macro(score: float) -> float:
    """Valida F1-macro."""
    normalized = float(score)

    if (
        not isfinite(normalized)
        or not 0.0 <= normalized <= 1.0
    ):
        raise ModelCalibrationError(
            f"F1-macro inválido: {normalized}"
        )

    return normalized


def _validate_log_loss(score: float) -> float:
    """Valida log loss."""
    normalized = float(score)

    if not isfinite(normalized) or normalized < 0.0:
        raise ModelCalibrationError(
            f"log loss inválido: {normalized}"
        )

    return normalized


def _validate_brier_score(score: float) -> float:
    """Valida Brier multiclasses na escala original."""
    normalized = float(score)

    if (
        not isfinite(normalized)
        or not 0.0 <= normalized <= 2.0
    ):
        raise ModelCalibrationError(
            f"Brier score inválido: {normalized}"
        )

    return normalized


def _calculate_validation_metrics(
    y_validation: pd.Series,
    probabilities: np.ndarray,
    classes: tuple[str, ...],
) -> tuple[float, float, float]:
    """Calcula métricas sem assumir a ordem das classes."""
    class_array = np.asarray(classes, dtype=object)
    predicted_indices = np.argmax(probabilities, axis=1)
    predictions = class_array[predicted_indices]

    f1_macro = f1_score(
        y_validation,
        predictions,
        labels=list(schema.ENERGY_CATEGORIES),
        average="macro",
        zero_division=0,
    )

    metric_labels, metric_probabilities = (
        _build_metric_probability_view(
            probabilities,
            classes,
        )
    )

    probability_log_loss = log_loss(
        y_validation,
        metric_probabilities,
        labels=list(metric_labels),
    )

    probability_brier_score = brier_score_loss(
        y_validation,
        metric_probabilities,
        labels=list(metric_labels),
        scale_by_half=False,
    )

    return (
        _validate_f1_macro(float(f1_macro)),
        _validate_log_loss(float(probability_log_loss)),
        _validate_brier_score(float(probability_brier_score)),
    )


def evaluate_probability_candidate(
    model_name: str,
    method: ProbabilityMethod,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> ProbabilityEvaluationResult:
    """Ajusta no treino e avalia um candidato na validação fixa."""
    _validate_model_name(model_name)
    _validate_probability_method(method)
    _validate_seed(seed)

    model_comparison._validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )

    candidate = build_probability_candidate(
        model_name=model_name,
        method=method,
        seed=seed,
    )

    try:
        candidate.fit(x_train, y_train)
    except Exception as error:
        raise ModelCalibrationError(
            "Falha ao ajustar o candidato "
            f"{model_name}/{method}: {error}"
        ) from error

    classes = _extract_fitted_classes(candidate)

    try:
        raw_probabilities = candidate.predict_proba(x_validation)
    except Exception as error:
        raise ModelCalibrationError(
            "Falha ao obter probabilidades de validação para "
            f"{model_name}/{method}: {error}"
        ) from error

    probabilities = _normalize_probability_matrix(
        raw_probabilities,
        sample_count=len(x_validation),
        class_count=len(classes),
    )

    (
        validation_f1_macro,
        validation_log_loss,
        validation_brier_score,
    ) = _calculate_validation_metrics(
        y_validation,
        probabilities,
        classes,
    )

    return ProbabilityEvaluationResult(
        model_name=model_name,
        method=method,
        classes=classes,
        validation_f1_macro=validation_f1_macro,
        validation_log_loss=validation_log_loss,
        validation_brier_score=validation_brier_score,
         fitted_candidate=candidate,
    )
