"""Busca controlada de hiperparâmetros dos finalistas da issue #86.

Este módulo ajusta exclusivamente HistGradientBoosting e Random Forest.
A busca usa somente treino, mantém a validação fixa separada e não recebe
o conjunto reservado.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from math import isfinite
from typing import Final

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator
from sklearn.metrics import f1_score
from sklearn.model_selection import GridSearchCV, ParameterGrid, RepeatedStratifiedKFold

import model_comparison
import modeling_pipeline
import schema


FINALIST_MODEL_NAMES: Final[tuple[str, str]] = (
    "hist_gradient_boosting",
    "random_forest",
)
GRID_SCORING: Final[str] = "f1_macro"
GRID_N_JOBS: Final[int] = 1
GRID_ERROR_SCORE: Final[str] = "raise"
GRID_REFIT: Final[bool] = True

_TUNED_FINALIST_PARAMS: Final[
    tuple[tuple[str, tuple[tuple[str, object], ...]], ...]
] = (
    (
        "hist_gradient_boosting",
        (
            ("classifier__l2_regularization", 0.0),
            ("classifier__learning_rate", 0.10),
            ("classifier__max_iter", 100),
            ("classifier__max_leaf_nodes", 15),
        ),
    ),
    (
        "random_forest",
        (
            ("classifier__max_features", "sqrt"),
            ("classifier__min_samples_leaf", 1),
            ("classifier__n_estimators", 200),
        ),
    ),
)

__all__ = [
    "FINALIST_MODEL_NAMES",
    "GRID_ERROR_SCORE",
    "GRID_N_JOBS",
    "GRID_REFIT",
    "GRID_SCORING",
    "ModelTuningError",
    "ModelTuningResult",
    "build_finalist_grid_search",
    "build_finalist_param_grids",
    "build_tuned_finalist_params",
    "tune_finalist_model",
    "tune_finalist_models",
]


def build_tuned_finalist_params() -> dict[str, dict[str, object]]:
    """Retorna cópias dos hiperparâmetros vencedores do tuning concluído."""
    return {
        model_name: dict(params)
        for model_name, params in _TUNED_FINALIST_PARAMS
    }


class ModelTuningError(RuntimeError):
    """Indica falha operacional durante o tuning dos finalistas."""


@dataclass(frozen=True)
class ModelTuningResult:
    """Resultado reproduzível da busca de um modelo finalista."""

    model_name: str
    best_params: tuple[tuple[str, object], ...]
    cv_best_f1_macro: float
    cv_best_f1_macro_std: float
    validation_f1_macro: float
    candidate_configurations: int
    total_cv_fits: int
    fitted_pipeline: BaseEstimator = field(repr=False, compare=False)

    @property
    def best_params_dict(self) -> dict[str, object]:
        """Retorna os melhores parâmetros como um novo dicionário."""
        return dict(self.best_params)


def build_finalist_param_grids() -> dict[str, dict[str, list[object]]]:
    """Cria cópias dos espaços reduzidos aprovados no Marco 1."""
    return {
        "hist_gradient_boosting": {
            "classifier__learning_rate": [0.05, 0.10],
            "classifier__max_iter": [100, 200],
            "classifier__max_leaf_nodes": [15, 31],
            "classifier__l2_regularization": [0.0, 1.0],
        },
        "random_forest": {
            "classifier__n_estimators": [100, 200],
            "classifier__max_features": ["sqrt", None],
            "classifier__min_samples_leaf": [1, 2],
        },
    }


def _validate_finalist_model_name(model_name: str) -> None:
    """Restringe a busca aos dois finalistas validados no Marco 1."""
    if not isinstance(model_name, str):
        raise TypeError("model_name deve ser uma string")
    if model_name not in FINALIST_MODEL_NAMES:
        raise ValueError(
            "model_name deve ser um dos finalistas: "
            + ", ".join(FINALIST_MODEL_NAMES)
        )


def _validate_score(score: float, metric_name: str) -> float:
    """Normaliza e valida uma métrica F1-macro."""
    normalized_score = float(score)
    if not isfinite(normalized_score) or not 0.0 <= normalized_score <= 1.0:
        raise ModelTuningError(f"{metric_name} inválido: {normalized_score}")
    return normalized_score


def _calculate_validation_f1(expected: pd.Series, predicted: np.ndarray) -> float:
    """Calcula F1-macro na validação fixa com as classes oficiais."""
    score = f1_score(
        expected,
        predicted,
        labels=list(schema.ENERGY_CATEGORIES),
        average="macro",
        zero_division=0,
    )
    return _validate_score(float(score), "F1-macro da validação fixa")


def _build_repeated_cv(seed: int) -> RepeatedStratifiedKFold:
    """Cria a mesma CV repetida validada no Marco 1."""
    return RepeatedStratifiedKFold(
        n_splits=model_comparison.CV_N_SPLITS,
        n_repeats=model_comparison.CV_N_REPEATS,
        random_state=seed,
    )


def build_finalist_grid_search(
    model_name: str,
    seed: int = schema.RANDOM_SEED,
) -> GridSearchCV:
    """Cria a busca não ajustada de um finalista."""
    _validate_finalist_model_name(model_name)
    estimators = model_comparison.build_candidate_estimators(seed=seed)
    estimator = estimators[model_name]
    pipeline = modeling_pipeline.build_model_pipeline(
        estimator,
        schema.FEATURE_COLUMNS,
    )
    return GridSearchCV(
        estimator=pipeline,
        param_grid=build_finalist_param_grids()[model_name],
        scoring=GRID_SCORING,
        n_jobs=GRID_N_JOBS,
        refit=GRID_REFIT,
        cv=_build_repeated_cv(seed),
        error_score=GRID_ERROR_SCORE,
        return_train_score=False,
    )


def _candidate_configuration_count(param_grid: dict[str, list[object]]) -> int:
    """Conta as combinações exatas de um espaço de busca."""
    return len(ParameterGrid(param_grid))


def _extract_cv_std(search: GridSearchCV, model_name: str) -> float:
    """Obtém o desvio-padrão da configuração vencedora."""
    try:
        best_index = int(search.best_index_)
        raw_values = np.asarray(search.cv_results_["std_test_score"], dtype=float)
        raw_std = float(raw_values[best_index])
    except (AttributeError, KeyError, IndexError, TypeError) as error:
        raise ModelTuningError(
            "Não foi possível obter o desvio-padrão da CV "
            f"para {model_name}: {error}"
        ) from error
    return _validate_score(raw_std, "Desvio-padrão do F1-macro da CV")


def _normalize_best_params(
    search: GridSearchCV,
    model_name: str,
) -> tuple[tuple[str, object], ...]:
    """Normaliza os parâmetros vencedores em ordem determinística."""
    try:
        params = search.best_params_
    except AttributeError as error:
        raise ModelTuningError(
            f"A busca de {model_name} não expôs best_params_: {error}"
        ) from error
    if not isinstance(params, dict) or not params:
        raise ModelTuningError(
            f"A busca de {model_name} retornou best_params_ inválido"
        )
    return tuple(sorted(params.items()))


def tune_finalist_model(
    model_name: str,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> ModelTuningResult:
    """Executa busca no treino e avalia a configuração na validação fixa."""
    _validate_finalist_model_name(model_name)
    model_comparison._validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )
    search = build_finalist_grid_search(model_name=model_name, seed=seed)
    try:
        search.fit(x_train, y_train)
    except Exception as error:
        raise ModelTuningError(
            f"Falha no GridSearchCV de {model_name}: {error}"
        ) from error

    cv_best_f1 = _validate_score(float(search.best_score_), "Melhor F1-macro da CV")
    cv_best_std = _extract_cv_std(search, model_name)
    best_params = _normalize_best_params(search, model_name)

    try:
        validation_predictions = search.best_estimator_.predict(x_validation)
    except Exception as error:
        raise ModelTuningError(
            "Falha ao avaliar a configuração vencedora de "
            f"{model_name} na validação fixa: {error}"
        ) from error

    validation_f1 = _calculate_validation_f1(y_validation, validation_predictions)
    candidate_configurations = _candidate_configuration_count(
        build_finalist_param_grids()[model_name]
    )
    total_cv_fits = candidate_configurations * model_comparison.CV_TOTAL_SPLITS

    return ModelTuningResult(
        model_name=model_name,
        best_params=best_params,
        cv_best_f1_macro=cv_best_f1,
        cv_best_f1_macro_std=cv_best_std,
        validation_f1_macro=validation_f1,
        candidate_configurations=candidate_configurations,
        total_cv_fits=total_cv_fits,
        fitted_pipeline=search.best_estimator_,
    )


def tune_finalist_models(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> tuple[ModelTuningResult, ModelTuningResult]:
    """Executa a busca controlada somente nos dois finalistas."""
    model_comparison._validate_explicit_splits(
        x_train,
        y_train,
        x_validation,
        y_validation,
        seed,
    )
    results = tuple(
        tune_finalist_model(
            model_name,
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=seed,
        )
        for model_name in FINALIST_MODEL_NAMES
    )
    if len(results) != 2:
        raise ModelTuningError("A busca deve retornar exatamente dois finalistas")
    return results
