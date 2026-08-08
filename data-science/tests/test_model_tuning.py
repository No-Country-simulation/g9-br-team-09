"""Testes do tuning controlado dos dois finalistas da issue #86."""

from __future__ import annotations

import inspect
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.model_selection import GridSearchCV, ParameterGrid, RepeatedStratifiedKFold
from sklearn.pipeline import Pipeline

SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import model_comparison  # noqa: E402
import model_tuning  # noqa: E402
import schema  # noqa: E402


def _build_feature_frame(start_index: int, size: int) -> tuple[pd.DataFrame, pd.Series]:
    categories = tuple(
        schema.ENERGY_CATEGORIES[position % len(schema.ENERGY_CATEGORIES)]
        for position in range(size)
    )
    category_position = {
        category: position
        for position, category in enumerate(schema.ENERGY_CATEGORIES)
    }
    features = pd.DataFrame(
        {
            "consumo_kwh": [
                120.0 + category_position[category] * 260.0 + (position % 5) * 7.5
                for position, category in enumerate(categories)
            ],
            "uso_horario_pico": [category != "EFICIENTE" for category in categories],
            "quantidade_equipamentos": [
                4 + category_position[category] * 10 + position % 3
                for position, category in enumerate(categories)
            ],
            "tipo_imovel": [
                schema.PROPERTY_TYPES[position % len(schema.PROPERTY_TYPES)]
                for position in range(size)
            ],
            "horas_alto_consumo": [
                2 + category_position[category] * 7 + position % 2
                for position, category in enumerate(categories)
            ],
        },
        index=pd.Index(range(start_index, start_index + size), name="registro"),
    )
    target = pd.Series(categories, index=features.index, name=schema.TARGET_COLUMN)
    return features, target


def _build_explicit_splits() -> tuple[pd.DataFrame, pd.Series, pd.DataFrame, pd.Series]:
    x_train, y_train = _build_feature_frame(100, 60)
    x_validation, y_validation = _build_feature_frame(1_000, 18)
    return x_train, y_train, x_validation, y_validation


def test_param_grids_congelam_espacos_e_contagens() -> None:
    grids = model_tuning.build_finalist_param_grids()
    assert tuple(grids) == model_tuning.FINALIST_MODEL_NAMES
    assert grids["hist_gradient_boosting"] == {
        "classifier__learning_rate": [0.05, 0.10],
        "classifier__max_iter": [100, 200],
        "classifier__max_leaf_nodes": [15, 31],
        "classifier__l2_regularization": [0.0, 1.0],
    }
    assert grids["random_forest"] == {
        "classifier__n_estimators": [100, 200],
        "classifier__max_features": ["sqrt", None],
        "classifier__min_samples_leaf": [1, 2],
    }
    assert len(ParameterGrid(grids["hist_gradient_boosting"])) == 16
    assert len(ParameterGrid(grids["random_forest"])) == 8
    grids["random_forest"]["classifier__n_estimators"].append(999)
    assert model_tuning.build_finalist_param_grids()["random_forest"][
        "classifier__n_estimators"
    ] == [100, 200]


def test_tuned_finalist_params_registra_vencedores_e_isola_mutacoes() -> None:
    params = model_tuning.build_tuned_finalist_params()

    assert tuple(params) == model_tuning.FINALIST_MODEL_NAMES
    assert params["hist_gradient_boosting"] == {
        "classifier__l2_regularization": 0.0,
        "classifier__learning_rate": 0.10,
        "classifier__max_iter": 100,
        "classifier__max_leaf_nodes": 15,
    }
    assert params["random_forest"] == {
        "classifier__max_features": "sqrt",
        "classifier__min_samples_leaf": 1,
        "classifier__n_estimators": 200,
    }

    params["random_forest"]["classifier__n_estimators"] = 999

    fresh_params = model_tuning.build_tuned_finalist_params()
    assert fresh_params["random_forest"]["classifier__n_estimators"] == 200


@pytest.mark.parametrize("model_name", model_tuning.FINALIST_MODEL_NAMES)
def test_build_grid_search_congela_contrato(model_name: str) -> None:
    search = model_tuning.build_finalist_grid_search(model_name)
    assert isinstance(search, GridSearchCV)
    assert isinstance(search.estimator, Pipeline)
    assert tuple(search.estimator.named_steps) == ("preprocessor", "classifier")
    assert search.param_grid == model_tuning.build_finalist_param_grids()[model_name]
    assert search.scoring == "f1_macro"
    assert search.n_jobs == 1
    assert search.refit is True
    assert search.error_score == "raise"
    assert search.return_train_score is False
    cv = search.cv
    assert isinstance(cv, RepeatedStratifiedKFold)
    assert cv.n_repeats == model_comparison.CV_N_REPEATS
    assert cv.get_n_splits() == model_comparison.CV_TOTAL_SPLITS
    assert cv.random_state == schema.RANDOM_SEED

    classifier = search.estimator.named_steps["classifier"]
    if model_name == "hist_gradient_boosting":
        assert isinstance(classifier, HistGradientBoostingClassifier)
        assert classifier.max_iter == 100
        assert classifier.early_stopping is False
        assert classifier.categorical_features is None
        assert classifier.random_state == schema.RANDOM_SEED
    else:
        assert isinstance(classifier, RandomForestClassifier)
        assert classifier.n_estimators == 100
        assert classifier.criterion == "gini"
        assert classifier.bootstrap is True
        assert classifier.random_state == schema.RANDOM_SEED
        assert classifier.n_jobs == 1


@pytest.mark.parametrize(
    "invalid_name",
    ("dummy", "regressao_logistica", "arvore_decisao", "ensemble"),
)
def test_build_grid_search_rejeita_modelo_nao_finalista(invalid_name: str) -> None:
    with pytest.raises(ValueError, match="deve ser um dos finalistas"):
        model_tuning.build_finalist_grid_search(invalid_name)


@pytest.mark.parametrize("invalid_seed", (True, 42.0, "42", None))
def test_build_grid_search_rejeita_seed_invalida(invalid_seed: object) -> None:
    with pytest.raises(TypeError, match="seed deve ser um inteiro"):
        model_tuning.build_finalist_grid_search("random_forest", seed=invalid_seed)


def test_tuning_publico_nao_recebe_conjunto_reservado() -> None:
    expected = {"x_train", "y_train", "x_validation", "y_validation", "seed"}
    parameters = inspect.signature(model_tuning.tune_finalist_models).parameters
    assert set(parameters) == expected
    assert all(
        "test" not in name.lower() and "holdout" not in name.lower()
        for name in parameters
    )


class _Predictor:
    def __init__(self, predictions: np.ndarray) -> None:
        self._predictions = predictions

    def predict(self, features: pd.DataFrame) -> np.ndarray:
        assert len(features) == len(self._predictions)
        return self._predictions.copy()


class _FakeCV:
    def get_n_splits(self) -> int:
        return model_comparison.CV_TOTAL_SPLITS


class _FakeSearch:
    def __init__(self, predictions: np.ndarray, *, fail_on_fit: bool = False) -> None:
        self.fail_on_fit = fail_on_fit
        self.fit_indices: tuple[int, ...] | None = None
        self.fit_target_indices: tuple[int, ...] | None = None
        self.best_score_ = 0.95
        self.best_index_ = 0
        self.best_params_ = {"classifier__n_estimators": 100}
        self.cv_results_ = {"std_test_score": np.array([0.01])}
        self.best_estimator_ = _Predictor(predictions)
        self.param_grid = {"classifier__n_estimators": [100]}
        self.cv = _FakeCV()

    def fit(self, features: pd.DataFrame, target: pd.Series) -> "_FakeSearch":
        self.fit_indices = tuple(features.index)
        self.fit_target_indices = tuple(target.index)
        if self.fail_on_fit:
            raise ValueError("falha simulada")
        return self


def test_tune_finalist_model_busca_so_no_treino(monkeypatch: pytest.MonkeyPatch) -> None:
    x_train, y_train, x_validation, y_validation = _build_explicit_splits()
    fake_search = _FakeSearch(y_validation.to_numpy(copy=True))
    monkeypatch.setattr(
        model_tuning,
        "build_finalist_grid_search",
        lambda model_name, seed: fake_search,
    )
    monkeypatch.setattr(
        model_tuning,
        "build_finalist_param_grids",
        lambda: {
            "hist_gradient_boosting": {"classifier__max_iter": [100]},
            "random_forest": {"classifier__n_estimators": [100]},
        },
    )
    result = model_tuning.tune_finalist_model(
        "random_forest",
        x_train,
        y_train,
        x_validation,
        y_validation,
    )
    assert fake_search.fit_indices == tuple(x_train.index)
    assert fake_search.fit_target_indices == tuple(y_train.index)
    assert set(fake_search.fit_indices).isdisjoint(x_validation.index)
    assert result.model_name == "random_forest"
    assert result.cv_best_f1_macro == pytest.approx(0.95)
    assert result.cv_best_f1_macro_std == pytest.approx(0.01)
    assert result.validation_f1_macro == pytest.approx(1.0)
    assert result.candidate_configurations == 1
    assert result.total_cv_fits == model_comparison.CV_TOTAL_SPLITS
    assert result.best_params_dict == {"classifier__n_estimators": 100}


def test_tune_finalist_model_contextualiza_falha(monkeypatch: pytest.MonkeyPatch) -> None:
    splits = _build_explicit_splits()
    fake_search = _FakeSearch(splits[3].to_numpy(copy=True), fail_on_fit=True)
    monkeypatch.setattr(
        model_tuning,
        "build_finalist_grid_search",
        lambda model_name, seed: fake_search,
    )
    with pytest.raises(model_tuning.ModelTuningError, match="falha simulada") as error:
        model_tuning.tune_finalist_model("random_forest", *splits)
    assert isinstance(error.value.__cause__, ValueError)


def test_tune_finalist_model_rejeita_feature_proibida() -> None:
    x_train, y_train, x_validation, y_validation = _build_explicit_splits()
    invalid_x_train = x_train.assign(score_referencia=50)
    with pytest.raises(ValueError, match="exatamente as cinco features oficiais"):
        model_tuning.tune_finalist_model(
            "random_forest",
            invalid_x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_tune_finalist_model_rejeita_indices_sobrepostos() -> None:
    x_train, y_train, x_validation, y_validation = _build_explicit_splits()
    x_validation = x_validation.copy()
    y_validation = y_validation.copy()
    x_validation.index = pd.Index(range(100, 118), name="registro")
    y_validation.index = x_validation.index
    with pytest.raises(ValueError, match="índices sobrepostos"):
        model_tuning.tune_finalist_model(
            "random_forest",
            x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_tune_finalist_models_processa_so_dois_finalistas(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    splits = _build_explicit_splits()
    observed: list[str] = []

    def fake_tune(
        model_name: str,
        x_train: pd.DataFrame,
        y_train: pd.Series,
        x_validation: pd.DataFrame,
        y_validation: pd.Series,
        seed: int,
    ) -> model_tuning.ModelTuningResult:
        observed.append(model_name)
        return model_tuning.ModelTuningResult(
            model_name=model_name,
            best_params=(("classifier__placeholder", 1),),
            cv_best_f1_macro=0.9,
            cv_best_f1_macro_std=0.01,
            validation_f1_macro=0.9,
            candidate_configurations=1,
            total_cv_fits=model_comparison.CV_TOTAL_SPLITS,
            fitted_pipeline=_Predictor(splits[3].to_numpy(copy=True)),
        )

    monkeypatch.setattr(model_tuning, "tune_finalist_model", fake_tune)
    results = model_tuning.tune_finalist_models(*splits)
    assert observed == list(model_tuning.FINALIST_MODEL_NAMES)
    assert tuple(result.model_name for result in results) == model_tuning.FINALIST_MODEL_NAMES
