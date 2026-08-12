"""Testes da infraestrutura de calibração dos finalistas da issue #86."""

from __future__ import annotations

import inspect
import sys
from collections.abc import Callable
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline

SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import model_calibration  # noqa: E402
import model_tuning  # noqa: E402
import schema  # noqa: E402


def test_probability_methods_congelam_contrato() -> None:
    assert model_calibration.PROBABILITY_METHODS == (
        "raw",
        "sigmoid",
        "isotonic",
    )
    assert model_calibration.RAW_METHOD == "raw"
    assert model_calibration.SIGMOID_METHOD == "sigmoid"
    assert model_calibration.ISOTONIC_METHOD == "isotonic"


def test_build_calibration_cv_congela_configuracao() -> None:
    cv = model_calibration.build_calibration_cv()

    assert isinstance(cv, StratifiedKFold)
    assert cv.n_splits == 5
    assert cv.shuffle is True
    assert cv.random_state == schema.RANDOM_SEED


@pytest.mark.parametrize(
    ("model_name", "classifier_type", "expected_params"),
    (
        (
            "hist_gradient_boosting",
            HistGradientBoostingClassifier,
            {
                "classifier__l2_regularization": 0.0,
                "classifier__learning_rate": 0.10,
                "classifier__max_iter": 100,
                "classifier__max_leaf_nodes": 15,
            },
        ),
        (
            "random_forest",
            RandomForestClassifier,
            {
                "classifier__max_features": "sqrt",
                "classifier__min_samples_leaf": 1,
                "classifier__n_estimators": 200,
            },
        ),
    ),
)
def test_build_tuned_finalist_pipeline_aplica_parametros_vencedores(
    model_name: str,
    classifier_type: type,
    expected_params: dict[str, object],
) -> None:
    pipeline = model_calibration.build_tuned_finalist_pipeline(model_name)

    assert isinstance(pipeline, Pipeline)
    assert tuple(pipeline.named_steps) == ("preprocessor", "classifier")
    assert isinstance(pipeline.named_steps["classifier"], classifier_type)

    actual_params = pipeline.get_params()
    for parameter_name, expected_value in expected_params.items():
        assert actual_params[parameter_name] == expected_value


@pytest.mark.parametrize("model_name", model_tuning.FINALIST_MODEL_NAMES)
def test_build_raw_candidate_retorna_pipeline_ajustado(
    model_name: str,
) -> None:
    candidate = model_calibration.build_probability_candidate(
        model_name,
        "raw",
    )

    assert isinstance(candidate, Pipeline)


@pytest.mark.parametrize("method", ("sigmoid", "isotonic"))
@pytest.mark.parametrize("model_name", model_tuning.FINALIST_MODEL_NAMES)
def test_build_calibrated_candidate_congela_contrato(
    model_name: str,
    method: str,
) -> None:
    candidate = model_calibration.build_probability_candidate(
        model_name,
        method,
    )

    assert isinstance(candidate, CalibratedClassifierCV)
    assert isinstance(candidate.estimator, Pipeline)
    assert candidate.method == method
    assert candidate.ensemble is False
    assert candidate.n_jobs == 1

    assert isinstance(candidate.cv, StratifiedKFold)
    assert candidate.cv.n_splits == 5
    assert candidate.cv.shuffle is True
    assert candidate.cv.random_state == schema.RANDOM_SEED


def test_selected_probability_candidate_congela_random_forest_isotonic() -> None:
    assert model_calibration.SELECTED_MODEL_NAME == "random_forest"
    assert (
        model_calibration.SELECTED_PROBABILITY_METHOD
        == model_calibration.ISOTONIC_METHOD
    )

    candidate = (
        model_calibration.build_selected_probability_candidate()
    )

    assert isinstance(candidate, CalibratedClassifierCV)
    assert candidate.method == "isotonic"
    assert candidate.ensemble is False
    assert candidate.n_jobs == 1

    assert isinstance(candidate.cv, StratifiedKFold)
    assert candidate.cv.n_splits == 5
    assert candidate.cv.shuffle is True
    assert candidate.cv.random_state == schema.RANDOM_SEED

    assert isinstance(candidate.estimator, Pipeline)
    assert tuple(candidate.estimator.named_steps) == (
        "preprocessor",
        "classifier",
    )

    classifier = candidate.estimator.named_steps["classifier"]

    assert isinstance(classifier, RandomForestClassifier)

    actual_params = candidate.estimator.get_params()

    assert actual_params["classifier__max_features"] == "sqrt"
    assert actual_params["classifier__min_samples_leaf"] == 1
    assert actual_params["classifier__n_estimators"] == 200


@pytest.mark.parametrize(
    "invalid_seed",
    (
        True,
        42.0,
        "42",
        None,
    ),
)
def test_selected_probability_candidate_rejeita_seed_invalida(
    invalid_seed: object,
) -> None:
    with pytest.raises(
        TypeError,
        match="seed deve ser um inteiro",
    ):
        model_calibration.build_selected_probability_candidate(
            seed=invalid_seed,
        )


@pytest.mark.parametrize(
    "invalid_model_name",
    (
        "dummy",
        "regressao_logistica",
        "arvore_decisao",
        "ensemble",
    ),
)
def test_probability_candidate_rejeita_modelo_nao_finalista(
    invalid_model_name: str,
) -> None:
    with pytest.raises(ValueError, match="deve ser um dos finalistas"):
        model_calibration.build_probability_candidate(
            invalid_model_name,
            "raw",
        )


@pytest.mark.parametrize(
    "invalid_method",
    (
        "platt",
        "temperature",
        "ensemble",
        "",
    ),
)
def test_probability_candidate_rejeita_metodo_nao_aprovado(
    invalid_method: str,
) -> None:
    with pytest.raises(ValueError, match="métodos permitidos"):
        model_calibration.build_probability_candidate(
            "random_forest",
            invalid_method,
        )


@pytest.mark.parametrize("invalid_seed", (True, 42.0, "42", None))
def test_build_calibration_cv_rejeita_seed_invalida(
    invalid_seed: object,
) -> None:
    with pytest.raises(TypeError, match="seed deve ser um inteiro"):
        model_calibration.build_calibration_cv(seed=invalid_seed)


def test_api_publica_de_calibracao_nao_recebe_conjunto_reservado() -> None:
    public_functions = (
        model_calibration.build_calibration_cv,
        model_calibration.build_probability_candidate,
        model_calibration.build_selected_probability_candidate,
        model_calibration.build_tuned_finalist_pipeline,
        model_calibration.evaluate_probability_candidate,
    )

    for function in public_functions:
        parameters = inspect.signature(function).parameters

        assert all(
            "test" not in parameter.lower()
            and "holdout" not in parameter.lower()
            for parameter in parameters
        )


def test_construcao_dos_candidatos_nao_ajusta_estimadores() -> None:
    raw = model_calibration.build_probability_candidate(
        "random_forest",
        "raw",
    )
    sigmoid = model_calibration.build_probability_candidate(
        "random_forest",
        "sigmoid",
    )
    selected = (
        model_calibration.build_selected_probability_candidate()
    )

    assert not hasattr(raw, "classes_")
    assert not hasattr(sigmoid, "classes_")
    assert not hasattr(selected, "classes_")


def _build_evaluation_splits() -> tuple[
    pd.DataFrame,
    pd.Series,
    pd.DataFrame,
    pd.Series,
]:
    categories_train = tuple(
        schema.ENERGY_CATEGORIES[
            position % len(schema.ENERGY_CATEGORIES)
        ]
        for position in range(60)
    )
    categories_validation = tuple(
        schema.ENERGY_CATEGORIES[
            position % len(schema.ENERGY_CATEGORIES)
        ]
        for position in range(18)
    )

    category_position = {
        category: position
        for position, category in enumerate(schema.ENERGY_CATEGORIES)
    }

    def build_frame(
        categories: tuple[str, ...],
        start_index: int,
    ) -> tuple[pd.DataFrame, pd.Series]:
        features = pd.DataFrame(
            {
                "consumo_kwh": [
                    120.0
                    + category_position[category] * 260.0
                    + (position % 5) * 7.5
                    for position, category in enumerate(categories)
                ],
                "uso_horario_pico": [
                    category != "EFICIENTE"
                    for category in categories
                ],
                "quantidade_equipamentos": [
                    4
                    + category_position[category] * 10
                    + position % 3
                    for position, category in enumerate(categories)
                ],
                "tipo_imovel": [
                    schema.PROPERTY_TYPES[
                        position % len(schema.PROPERTY_TYPES)
                    ]
                    for position in range(len(categories))
                ],
                "horas_alto_consumo": [
                    2
                    + category_position[category] * 7
                    + position % 2
                    for position, category in enumerate(categories)
                ],
            },
            index=pd.Index(
                range(
                    start_index,
                    start_index + len(categories),
                ),
                name="registro",
            ),
        )

        target = pd.Series(
            categories,
            index=features.index,
            name=schema.TARGET_COLUMN,
        )

        return features, target

    x_train, y_train = build_frame(
        categories_train,
        100,
    )
    x_validation, y_validation = build_frame(
        categories_validation,
        1_000,
    )

    return (
        x_train,
        y_train,
        x_validation,
        y_validation,
    )


def _build_probabilities_for_targets(
    target: pd.Series,
    classes: tuple[str, ...],
) -> np.ndarray:
    probabilities = np.full(
        (len(target), len(classes)),
        0.10,
        dtype=float,
    )

    for row_index, category in enumerate(target):
        probabilities[
            row_index,
            classes.index(str(category)),
        ] = 0.80

    return probabilities


class _FakeProbabilityCandidate:
    def __init__(
        self,
        classes: tuple[str, ...],
        probabilities: np.ndarray,
        *,
        fail_on_fit: bool = False,
        fail_on_predict_proba: bool = False,
    ) -> None:
        self._classes = classes
        self._probabilities = np.asarray(
            probabilities,
            dtype=float,
        )
        self._fail_on_fit = fail_on_fit
        self._fail_on_predict_proba = fail_on_predict_proba

        self.fit_indices: tuple[int, ...] | None = None
        self.fit_target_indices: tuple[int, ...] | None = None
        self.predict_proba_indices: tuple[int, ...] | None = None

    def fit(
        self,
        features: pd.DataFrame,
        target: pd.Series,
    ) -> _FakeProbabilityCandidate:
        self.fit_indices = tuple(features.index)
        self.fit_target_indices = tuple(target.index)

        if self._fail_on_fit:
            raise ValueError("falha simulada no fit")

        self.classes_ = np.asarray(
            self._classes,
            dtype=object,
        )

        return self

    def predict_proba(
        self,
        features: pd.DataFrame,
    ) -> np.ndarray:
        self.predict_proba_indices = tuple(features.index)

        if self._fail_on_predict_proba:
            raise RuntimeError(
                "falha simulada no predict_proba"
            )

        return self._probabilities.copy()


def test_evaluate_probability_candidate_respeita_classes_e_splits(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    (
        x_train,
        y_train,
        x_validation,
        y_validation,
    ) = _build_evaluation_splits()

    classes = (
        "INEFICIENTE",
        "EFICIENTE",
        "MODERADO",
    )

    probabilities = _build_probabilities_for_targets(
        y_validation,
        classes,
    )

    candidate = _FakeProbabilityCandidate(
        classes,
        probabilities,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_probability_candidate",
        lambda model_name, method, seed: candidate,
    )

    result = model_calibration.evaluate_probability_candidate(
        "random_forest",
        "raw",
        x_train,
        y_train,
        x_validation,
        y_validation,
    )

    assert candidate.fit_indices == tuple(x_train.index)
    assert candidate.fit_target_indices == tuple(y_train.index)
    assert candidate.predict_proba_indices == tuple(
        x_validation.index
    )

    assert set(candidate.fit_indices).isdisjoint(
        candidate.predict_proba_indices
    )

    assert result.model_name == "random_forest"
    assert result.method == "raw"
    assert result.classes == classes

    assert result.validation_f1_macro == pytest.approx(1.0)
    assert result.validation_log_loss == pytest.approx(
        -np.log(0.80)
    )
    assert result.validation_brier_score == pytest.approx(
        0.06
    )

    assert result.fitted_candidate is candidate


def test_evaluate_probability_candidate_contextualiza_falha_de_fit(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    splits = _build_evaluation_splits()

    classes = tuple(schema.ENERGY_CATEGORIES)
    probabilities = _build_probabilities_for_targets(
        splits[3],
        classes,
    )

    candidate = _FakeProbabilityCandidate(
        classes,
        probabilities,
        fail_on_fit=True,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_probability_candidate",
        lambda model_name, method, seed: candidate,
    )

    with pytest.raises(
        model_calibration.ModelCalibrationError,
        match="falha simulada no fit",
    ) as error:
        model_calibration.evaluate_probability_candidate(
            "random_forest",
            "raw",
            *splits,
        )

    assert isinstance(error.value.__cause__, ValueError)


def test_evaluate_probability_candidate_contextualiza_falha_de_predict_proba(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    splits = _build_evaluation_splits()

    classes = tuple(schema.ENERGY_CATEGORIES)
    probabilities = _build_probabilities_for_targets(
        splits[3],
        classes,
    )

    candidate = _FakeProbabilityCandidate(
        classes,
        probabilities,
        fail_on_predict_proba=True,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_probability_candidate",
        lambda model_name, method, seed: candidate,
    )

    with pytest.raises(
        model_calibration.ModelCalibrationError,
        match="falha simulada no predict_proba",
    ) as error:
        model_calibration.evaluate_probability_candidate(
            "random_forest",
            "raw",
            *splits,
        )

    assert isinstance(error.value.__cause__, RuntimeError)


@pytest.mark.parametrize(
    ("probability_builder", "expected_message"),
    (
        (
            lambda size: np.full(
                (size, 2),
                0.5,
                dtype=float,
            ),
            "shape inválido",
        ),
        (
            lambda size: np.column_stack(
                (
                    np.full(size, np.nan),
                    np.zeros(size),
                    np.ones(size),
                )
            ),
            "não finitas",
        ),
        (
            lambda size: np.column_stack(
                (
                    np.full(size, 1.10),
                    np.zeros(size),
                    np.zeros(size),
                )
            ),
            r"intervalo \[0, 1\]",
        ),
        (
            lambda size: np.full(
                (size, 3),
                0.30,
                dtype=float,
            ),
            "devem somar 1",
        ),
    ),
)
def test_evaluate_probability_candidate_rejeita_probabilidades_invalidas(
    monkeypatch: pytest.MonkeyPatch,
    probability_builder: Callable[[int], np.ndarray],
    expected_message: str,
) -> None:
    splits = _build_evaluation_splits()
    classes = tuple(schema.ENERGY_CATEGORIES)

    probabilities = probability_builder(
        len(splits[2])
    )

    candidate = _FakeProbabilityCandidate(
        classes,
        probabilities,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_probability_candidate",
        lambda model_name, method, seed: candidate,
    )

    with pytest.raises(
        model_calibration.ModelCalibrationError,
        match=expected_message,
    ):
        model_calibration.evaluate_probability_candidate(
            "random_forest",
            "raw",
            *splits,
        )


@pytest.mark.parametrize(
    "classes",
    (
        (
            "EFICIENTE",
            "MODERADO",
            "OUTRA",
        ),
        (
            "EFICIENTE",
            "EFICIENTE",
            "INEFICIENTE",
        ),
    ),
)
def test_evaluate_probability_candidate_rejeita_classes_invalidas(
    monkeypatch: pytest.MonkeyPatch,
    classes: tuple[str, ...],
) -> None:
    splits = _build_evaluation_splits()

    probabilities = np.full(
        (len(splits[2]), 3),
        1.0 / 3.0,
        dtype=float,
    )

    candidate = _FakeProbabilityCandidate(
        classes,
        probabilities,
    )

    monkeypatch.setattr(
        model_calibration,
        "build_probability_candidate",
        lambda model_name, method, seed: candidate,
    )

    with pytest.raises(
        model_calibration.ModelCalibrationError,
    ):
        model_calibration.evaluate_probability_candidate(
            "random_forest",
            "raw",
            *splits,
        )


def test_evaluator_publico_nao_recebe_holdout_ou_teste() -> None:
    parameters = inspect.signature(
        model_calibration.evaluate_probability_candidate
    ).parameters

    assert tuple(parameters) == (
        "model_name",
        "method",
        "x_train",
        "y_train",
        "x_validation",
        "y_validation",
        "seed",
    )

    assert all(
        "test" not in name.lower()
        and "holdout" not in name.lower()
        for name in parameters
    )
