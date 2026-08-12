"""Avaliação final única do holdout para a solução congelada da issue #86.

Este módulo existe exclusivamente para a avaliação final posterior ao Marco 2.

Contrato congelado:
- Random Forest;
- n_estimators=200;
- max_features="sqrt";
- min_samples_leaf=1;
- calibração isotonic;
- seed 42;
- categoria definida por argmax das probabilidades.

O módulo não executa avaliação durante importação, não compara modelos,
não executa tuning e não permite selecionar outro método de calibração.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Final

from sklearn.base import BaseEstimator

import data_split
import model_calibration
import model_comparison
import model_tuning
import schema


EXPECTED_MODEL_NAME: Final[str] = "random_forest"
EXPECTED_PROBABILITY_METHOD: Final[str] = "isotonic"
EXPECTED_SEED: Final[int] = 42
EXPECTED_CALIBRATION_CV_N_SPLITS: Final[int] = 5
EXPECTED_CALIBRATION_CV_SHUFFLE: Final[bool] = True
EXPECTED_CALIBRATION_ENSEMBLE: Final[bool] = False
EXPECTED_CALIBRATION_N_JOBS: Final[int] = 1

EXPECTED_TUNED_PARAMS: Final[dict[str, object]] = {
    "classifier__max_features": "sqrt",
    "classifier__min_samples_leaf": 1,
    "classifier__n_estimators": 200,
}


__all__ = [
    "HoldoutEvaluationError",
    "HoldoutEvaluationResult",
    "evaluate_frozen_holdout",
]


class HoldoutEvaluationError(RuntimeError):
    """Indica violação ou falha na avaliação final congelada."""


@dataclass(frozen=True)
class HoldoutEvaluationResult:
    """Resultado imutável da avaliação final única."""

    model_name: str
    probability_method: str
    seed: int
    classes: tuple[str, ...]
    holdout_size: int
    holdout_f1_macro: float
    holdout_log_loss: float
    holdout_brier_score: float
    fitted_candidate: BaseEstimator = field(
        repr=False,
        compare=False,
    )


def _validate_frozen_contract() -> None:
    """Falha se qualquer parte relevante do congelamento tiver derivado."""
    if schema.RANDOM_SEED != EXPECTED_SEED:
        raise HoldoutEvaluationError(
            "A seed do schema divergiu do contrato congelado: "
            f"atual={schema.RANDOM_SEED}, esperado={EXPECTED_SEED}"
        )

    if (
        model_calibration.SELECTED_MODEL_NAME
        != EXPECTED_MODEL_NAME
    ):
        raise HoldoutEvaluationError(
            "O modelo selecionado divergiu do contrato congelado: "
            f"atual={model_calibration.SELECTED_MODEL_NAME}, "
            f"esperado={EXPECTED_MODEL_NAME}"
        )

    if (
        model_calibration.SELECTED_PROBABILITY_METHOD
        != EXPECTED_PROBABILITY_METHOD
    ):
        raise HoldoutEvaluationError(
            "O método probabilístico divergiu do contrato congelado: "
            f"atual={model_calibration.SELECTED_PROBABILITY_METHOD}, "
            f"esperado={EXPECTED_PROBABILITY_METHOD}"
        )

    tuned_params = model_tuning.build_tuned_finalist_params().get(
        EXPECTED_MODEL_NAME
    )

    if tuned_params != EXPECTED_TUNED_PARAMS:
        raise HoldoutEvaluationError(
            "Os hiperparâmetros da Random Forest divergiram do "
            f"contrato congelado: atual={tuned_params}, "
            f"esperado={EXPECTED_TUNED_PARAMS}"
        )

    if (
        model_calibration.CALIBRATION_CV_N_SPLITS
        != EXPECTED_CALIBRATION_CV_N_SPLITS
    ):
        raise HoldoutEvaluationError(
            "O número de folds da calibração divergiu do "
            "contrato congelado"
        )

    if (
        model_calibration.CALIBRATION_CV_SHUFFLE
        is not EXPECTED_CALIBRATION_CV_SHUFFLE
    ):
        raise HoldoutEvaluationError(
            "A configuração de shuffle da calibração divergiu do "
            "contrato congelado"
        )

    if (
        model_calibration.CALIBRATION_ENSEMBLE
        is not EXPECTED_CALIBRATION_ENSEMBLE
    ):
        raise HoldoutEvaluationError(
            "A configuração ensemble da calibração divergiu do "
            "contrato congelado"
        )

    if (
        model_calibration.CALIBRATION_N_JOBS
        != EXPECTED_CALIBRATION_N_JOBS
    ):
        raise HoldoutEvaluationError(
            "O paralelismo da calibração divergiu do contrato congelado"
        )


def _validate_final_split(
    split: data_split.DataSplit,
) -> None:
    """Valida somente a estrutura necessária à avaliação final."""
    if not isinstance(split, data_split.DataSplit):
        raise TypeError(
            "split deve ser uma instância de data_split.DataSplit"
        )

    try:
        model_comparison._validate_explicit_splits(
            split.x_train,
            split.y_train,
            split.x_test,
            split.y_test,
            EXPECTED_SEED,
        )
    except (TypeError, ValueError) as error:
        raise HoldoutEvaluationError(
            "O split fornecido é incompatível com o protocolo "
            f"da avaliação final: {error}"
        ) from error


def evaluate_frozen_holdout(
    split: data_split.DataSplit,
) -> HoldoutEvaluationResult:
    """Ajusta a solução congelada no treino e avalia o holdout uma vez.

    Esta função não permite escolher modelo, hiperparâmetros, calibração
    ou seed. O candidato é reconstruído exclusivamente pelo contrato
    validado no Marco 2.

    A chamada deve ocorrer uma única vez sobre o holdout oficial.
    """
    _validate_frozen_contract()
    _validate_final_split(split)

    candidate = (
        model_calibration.build_selected_probability_candidate()
    )

    try:
        candidate.fit(
            split.x_train,
            split.y_train,
        )
    except Exception as error:
        raise HoldoutEvaluationError(
            "Falha ao ajustar a solução congelada no treino: "
            f"{error}"
        ) from error

    try:
        classes = model_calibration._extract_fitted_classes(
            candidate
        )
    except model_calibration.ModelCalibrationError as error:
        raise HoldoutEvaluationError(
            "Falha ao validar as classes da solução congelada: "
            f"{error}"
        ) from error

    try:
        raw_probabilities = candidate.predict_proba(
            split.x_test
        )
    except Exception as error:
        raise HoldoutEvaluationError(
            "Falha na chamada única de predict_proba do holdout: "
            f"{error}"
        ) from error

    try:
        probabilities = (
            model_calibration._normalize_probability_matrix(
                raw_probabilities,
                sample_count=len(split.x_test),
                class_count=len(classes),
            )
        )

        (
            holdout_f1_macro,
            holdout_log_loss,
            holdout_brier_score,
        ) = model_calibration._calculate_validation_metrics(
            split.y_test,
            probabilities,
            classes,
        )
    except (
        model_calibration.ModelCalibrationError,
        TypeError,
        ValueError,
    ) as error:
        raise HoldoutEvaluationError(
            "Falha ao calcular as métricas finais do holdout: "
            f"{error}"
        ) from error

    return HoldoutEvaluationResult(
        model_name=EXPECTED_MODEL_NAME,
        probability_method=EXPECTED_PROBABILITY_METHOD,
        seed=EXPECTED_SEED,
        classes=classes,
        holdout_size=len(split.x_test),
        holdout_f1_macro=holdout_f1_macro,
        holdout_log_loss=holdout_log_loss,
        holdout_brier_score=holdout_brier_score,
        fitted_candidate=candidate,
    )
