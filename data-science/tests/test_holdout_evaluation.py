"""Testes da avaliação final sem acesso ao dataset oficial."""

from __future__ import annotations

import inspect

import numpy as np
import pandas as pd
import pytest

import data_split
import holdout_evaluation
import model_calibration
import schema


def _build_frame(
    categories: tuple[str, ...],
    start_index: int,
) -> tuple[pd.DataFrame, pd.Series]:
    category_position = {
        category: position
        for position, category in enumerate(schema.ENERGY_CATEGORIES)
    }

    features = pd.DataFrame(
        {
            "consumo_kwh": [
                150.0
                + category_position[category] * 250.0
                + position
                for position, category in enumerate(categories)
            ],
            "uso_horario_pico": [
                category != "EFICIENTE"
                for category in categories
            ],
            "quantidade_equipamentos": [
                5 + category_position[category] * 8
                for category in categories
            ],
            "tipo_imovel": [
                schema.PROPERTY_TYPES[
                    position % len(schema.PROPERTY_TYPES)
                ]
                for position in range(len(categories))
            ],
            "horas_alto_consumo": [
                3 + category_position[category] * 6
                for category in categories
            ],
        },
        index=pd.Index(
            range(start_index, start_index + len(categories)),
            name="registro",
        ),
    )

    target = pd.Series(
        categories,
        index=features.index,
        name=schema.TARGET_COLUMN,
    )

    return features, target


def _build_synthetic_split() -> data_split.DataSplit:
    train_categories = tuple(
        schema.ENERGY_CATEGORIES[
            position % len(schema.ENERGY_CATEGORIES)
        ]
        for position in range(18)
    )
    validation_categories = tuple(
        schema.ENERGY_CATEGORIES[
            position % len(schema.ENERGY_CATEGORIES)
        ]
        for position in range(6)
    )
    test_categories = validation_categories

    x_train, y_train = _build_frame(train_categories, 100)
    x_validation, y_validation = _build_frame(
        validation_categories,
        1_000,
    )
    x_test, y_test = _build_frame(test_categories, 2_000)

    return data_split.DataSplit(
        x_train=x_train,
        x_validation=x_validation,
        x_test=x_test,
        y_train=y_train,
        y_validation=y_validation,
        y_test=y_test,
    )


class _FakeCandidate:
    def __init__(
        self,
        classes: tuple[str, ...],
        probabilities: np.ndarray,
    ) -> None:
        self._classes = classes
        self._probabilities = probabilities
        self.fit_indices: tuple[int, ...] | None = None
        self.predict_indices: tuple[int, ...] | None = None
        self.predict_proba_calls = 0

    def fit(
        self,
        features: pd.DataFrame,
        target: pd.Series,
    ) -> _FakeCandidate:
        self.fit_indices = tuple(features.index)
        self.classes_ = np.asarray(self._classes, dtype=object)
        return self

    def predict_proba(
        self,
        features: pd.DataFrame,
    ) -> np.ndarray:
        self.predict_proba_calls += 1
        self.predict_indices = tuple(features.index)
        return self._probabilities.copy()


def test_api_publica_recebe_somente_split() -> None:
    parameters = inspect.signature(
        holdout_evaluation.evaluate_frozen_holdout
    ).parameters

    assert tuple(parameters) == ("split",)


def test_contrato_congelado_rejeita_modelo_divergente(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        model_calibration,
        "SELECTED_MODEL_NAME",
        "hist_gradient_boosting",
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="modelo selecionado divergiu",
    ):
        holdout_evaluation._validate_frozen_contract()


def test_avaliacao_usa_treino_e_um_predict_proba_sintetico(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    split = _build_synthetic_split()
    classes = tuple(schema.ENERGY_CATEGORIES)

    probabilities = np.full(
        (len(split.y_test), len(classes)),
        0.10,
        dtype=float,
    )

    for row_index, category in enumerate(split.y_test):
        probabilities[
            row_index,
            classes.index(str(category)),
        ] = 0.80

    candidate = _FakeCandidate(
        classes,
        probabilities,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_selected_probability_candidate",
        lambda: candidate,
    )

    result = holdout_evaluation.evaluate_frozen_holdout(split)

    assert candidate.fit_indices == tuple(split.x_train.index)
    assert candidate.predict_indices == tuple(split.x_test.index)
    assert candidate.predict_proba_calls == 1

    assert set(candidate.fit_indices).isdisjoint(
        candidate.predict_indices
    )

    assert result.model_name == "random_forest"
    assert result.probability_method == "isotonic"
    assert result.seed == 42
    assert result.holdout_size == len(split.x_test)

    assert result.holdout_f1_macro == pytest.approx(1.0)
    assert result.holdout_log_loss == pytest.approx(-np.log(0.80))
    assert result.holdout_brier_score == pytest.approx(0.06)


def test_avaliacao_contextualiza_falha_de_fit(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    split = _build_synthetic_split()

    class _FailingFitCandidate:
        def fit(
            self,
            features: pd.DataFrame,
            target: pd.Series,
        ) -> _FailingFitCandidate:
            raise ValueError("falha simulada no fit")

    monkeypatch.setattr(
        model_calibration,
        "build_selected_probability_candidate",
        lambda: _FailingFitCandidate(),
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="falha simulada no fit",
    ) as error:
        holdout_evaluation.evaluate_frozen_holdout(split)

    assert isinstance(error.value.__cause__, ValueError)


def test_avaliacao_contextualiza_falha_de_predict_proba(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    split = _build_synthetic_split()

    class _FailingPredictCandidate:
        def fit(
            self,
            features: pd.DataFrame,
            target: pd.Series,
        ) -> _FailingPredictCandidate:
            self.classes_ = np.asarray(
                tuple(schema.ENERGY_CATEGORIES),
                dtype=object,
            )
            return self

        def predict_proba(
            self,
            features: pd.DataFrame,
        ) -> np.ndarray:
            raise RuntimeError("falha simulada no predict_proba")

    monkeypatch.setattr(
        model_calibration,
        "build_selected_probability_candidate",
        lambda: _FailingPredictCandidate(),
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="falha simulada no predict_proba",
    ) as error:
        holdout_evaluation.evaluate_frozen_holdout(split)

    assert isinstance(error.value.__cause__, RuntimeError)


def test_contrato_congelado_rejeita_seed_divergente(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(schema, "RANDOM_SEED", 43)

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="seed do schema divergiu",
    ):
        holdout_evaluation._validate_frozen_contract()


def test_contrato_congelado_rejeita_metodo_probabilistico_divergente(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        model_calibration,
        "SELECTED_PROBABILITY_METHOD",
        "sigmoid",
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="método probabilístico divergiu",
    ):
        holdout_evaluation._validate_frozen_contract()


def test_contrato_congelado_rejeita_hiperparametros_divergentes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    divergent_params = dict(holdout_evaluation.EXPECTED_TUNED_PARAMS)
    divergent_params["classifier__n_estimators"] = 199

    monkeypatch.setattr(
        holdout_evaluation.model_tuning,
        "build_tuned_finalist_params",
        lambda: {"random_forest": divergent_params},
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="hiperparâmetros da Random Forest divergiram",
    ):
        holdout_evaluation._validate_frozen_contract()


@pytest.mark.parametrize(
    ("attribute", "divergent_value"),
    (
        ("CALIBRATION_CV_N_SPLITS", 4),
        ("CALIBRATION_CV_SHUFFLE", False),
        ("CALIBRATION_ENSEMBLE", True),
        ("CALIBRATION_N_JOBS", 2),
    ),
)
def test_contrato_congelado_rejeita_configuracao_calibracao_divergente(
    monkeypatch: pytest.MonkeyPatch,
    attribute: str,
    divergent_value: object,
) -> None:
    monkeypatch.setattr(
        model_calibration,
        attribute,
        divergent_value,
    )

    with pytest.raises(
        holdout_evaluation.HoldoutEvaluationError,
        match="calibração divergiu",
    ):
        holdout_evaluation._validate_frozen_contract()
