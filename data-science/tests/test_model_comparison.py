"""Testes da comparação da baseline e dos quatro candidatos."""

import inspect
import sys
import warnings
from dataclasses import replace
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import (
    HistGradientBoostingClassifier,
    RandomForestClassifier,
)
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import RepeatedStratifiedKFold
from sklearn.tree import DecisionTreeClassifier


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import model_comparison  # noqa: E402
import modeling_pipeline  # noqa: E402
import schema  # noqa: E402


def _build_feature_frame(
    start_index: int,
    size: int,
) -> tuple[pd.DataFrame, pd.Series]:
    """Cria dados pequenos sem consultar o dataset reservado."""
    categories = tuple(
        schema.ENERGY_CATEGORIES[
            position % len(schema.ENERGY_CATEGORIES)
        ]
        for position in range(size)
    )
    category_position = {
        category: position
        for position, category in enumerate(
            schema.ENERGY_CATEGORIES
        )
    }

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
                for position in range(size)
            ],
            "horas_alto_consumo": [
                2
                + category_position[category] * 7
                + position % 2
                for position, category in enumerate(categories)
            ],
        },
        index=pd.Index(
            range(start_index, start_index + size),
            name="registro",
        ),
    )
    target = pd.Series(
        categories,
        index=features.index,
        name=schema.TARGET_COLUMN,
    )

    return features, target


def _build_explicit_splits(
) -> tuple[pd.DataFrame, pd.Series, pd.DataFrame, pd.Series]:
    """Cria treino e validação explícitos, sem sobreposição."""
    x_train, y_train = _build_feature_frame(100, 60)
    x_validation, y_validation = _build_feature_frame(
        1_000,
        18,
    )

    return x_train, y_train, x_validation, y_validation


def _role_for(model_name: str) -> str:
    return (
        model_comparison.BASELINE_ROLE
        if model_name == model_comparison.BASELINE_MODEL_NAME
        else model_comparison.CANDIDATE_ROLE
    )


def _build_results(
    cv_means: dict[str, float],
    *,
    validation_scores: dict[str, float] | None = None,
    fit_times: dict[str, float] | None = None,
) -> tuple[model_comparison.ModelComparisonResult, ...]:
    """Cria resultados sintéticos para testar somente o ranking."""
    return tuple(
        model_comparison.ModelComparisonResult(
            model_name=model_name,
            role=_role_for(model_name),
            cv_f1_macro_scores=(cv_means[model_name],)
            * model_comparison.CV_TOTAL_SPLITS,
            cv_f1_macro_mean=cv_means[model_name],
            cv_f1_macro_std=0.0,
            validation_f1_macro=(
                validation_scores[model_name]
                if validation_scores is not None
                else cv_means[model_name]
            ),
            fit_time_seconds=(
                fit_times[model_name]
                if fit_times is not None
                else 1.0
            ),
            prediction_time_seconds=0.1,
        )
        for model_name in model_comparison.MODEL_NAMES
    )


def test_constantes_separam_baseline_e_candidatos() -> None:
    assert model_comparison.BASELINE_MODEL_NAME == "dummy"
    assert model_comparison.CANDIDATE_MODEL_NAMES == (
        "regressao_logistica",
        "arvore_decisao",
        "random_forest",
        "hist_gradient_boosting",
    )
    assert model_comparison.MODEL_NAMES == (
        "dummy",
        *model_comparison.CANDIDATE_MODEL_NAMES,
    )
    assert model_comparison.CV_N_SPLITS == 5
    assert model_comparison.CV_N_REPEATS == 3
    assert model_comparison.CV_TOTAL_SPLITS == 15


def test_build_candidate_estimators_congela_contrato() -> None:
    estimators = model_comparison.build_candidate_estimators()

    assert tuple(estimators) == model_comparison.MODEL_NAMES

    dummy = estimators["dummy"]
    logistic = estimators["regressao_logistica"]
    tree = estimators["arvore_decisao"]
    forest = estimators["random_forest"]
    histogram = estimators["hist_gradient_boosting"]

    assert isinstance(dummy, DummyClassifier)
    assert dummy.strategy == "most_frequent"
    assert dummy.random_state == schema.RANDOM_SEED

    assert isinstance(logistic, LogisticRegression)
    assert logistic.solver == "lbfgs"
    assert logistic.max_iter == 2_000
    assert logistic.random_state == schema.RANDOM_SEED

    assert isinstance(tree, DecisionTreeClassifier)
    assert tree.max_depth == 5
    assert tree.random_state == schema.RANDOM_SEED

    assert isinstance(forest, RandomForestClassifier)
    assert forest.n_estimators == 100
    assert forest.random_state == schema.RANDOM_SEED
    assert forest.n_jobs == 1

    assert isinstance(histogram, HistGradientBoostingClassifier)
    assert histogram.max_iter == 100
    assert histogram.early_stopping is False
    assert histogram.categorical_features is None
    assert histogram.random_state == schema.RANDOM_SEED


def test_build_candidate_estimators_retorna_novas_instancias() -> None:
    first = model_comparison.build_candidate_estimators()
    second = model_comparison.build_candidate_estimators()

    for model_name in model_comparison.MODEL_NAMES:
        assert first[model_name] is not second[model_name]
        assert (
            first[model_name].get_params()
            == second[model_name].get_params()
        )


@pytest.mark.parametrize(
    "invalid_seed",
    (
        True,
        42.0,
        "42",
        None,
    ),
)
def test_build_candidate_estimators_rejeita_seed_invalida(
    invalid_seed: object,
) -> None:
    with pytest.raises(
        TypeError,
        match="seed deve ser um inteiro",
    ):
        model_comparison.build_candidate_estimators(
            invalid_seed
        )


def test_compare_candidate_models_retorna_cinco_resultados() -> None:
    results = model_comparison.compare_candidate_models(
        *_build_explicit_splits()
    )

    assert tuple(
        result.model_name
        for result in results
    ) == model_comparison.MODEL_NAMES
    assert tuple(
        result.role
        for result in results
    ) == (
        model_comparison.BASELINE_ROLE,
        model_comparison.CANDIDATE_ROLE,
        model_comparison.CANDIDATE_ROLE,
        model_comparison.CANDIDATE_ROLE,
        model_comparison.CANDIDATE_ROLE,
    )

    for result in results:
        assert len(result.cv_f1_macro_scores) == model_comparison.CV_TOTAL_SPLITS
        assert all(
            0.0 <= score <= 1.0
            for score in result.cv_f1_macro_scores
        )
        assert 0.0 <= result.cv_f1_macro_mean <= 1.0
        assert result.cv_f1_macro_std >= 0.0
        assert 0.0 <= result.validation_f1_macro <= 1.0
        assert np.isfinite(result.fit_time_seconds)
        assert result.fit_time_seconds >= 0.0
        assert np.isfinite(
            result.prediction_time_seconds
        )
        assert result.prediction_time_seconds >= 0.0


def test_compare_candidate_models_configura_cv_congelada(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    observed: list[dict[str, object]] = []
    original_cross_val_score = model_comparison.cross_val_score

    def recording_cross_val_score(
        estimator: object,
        features: pd.DataFrame,
        target: pd.Series,
        **kwargs: object,
    ) -> np.ndarray:
        observed.append(dict(kwargs))
        return original_cross_val_score(
            estimator,
            features,
            target,
            **kwargs,
        )

    monkeypatch.setattr(
        model_comparison,
        "cross_val_score",
        recording_cross_val_score,
    )

    model_comparison.compare_candidate_models(
        *_build_explicit_splits()
    )

    assert len(observed) == len(model_comparison.MODEL_NAMES)

    for call in observed:
        cv = call["cv"]
        assert isinstance(cv, RepeatedStratifiedKFold)
        assert cv.n_repeats == model_comparison.CV_N_REPEATS
        assert cv.get_n_splits() == model_comparison.CV_TOTAL_SPLITS
        assert cv.random_state == schema.RANDOM_SEED
        assert call["n_jobs"] == 1
        assert call["error_score"] == "raise"
        assert call["scoring"] == "f1_macro"


def test_compare_candidate_models_e_reprodutivel_e_preserva_entradas(
) -> None:
    splits = _build_explicit_splits()
    originals = tuple(
        value.copy(deep=True)
        for value in splits
    )

    first_results = model_comparison.compare_candidate_models(
        *splits
    )
    second_results = model_comparison.compare_candidate_models(
        *splits
    )

    assert [
        result.cv_f1_macro_scores
        for result in first_results
    ] == [
        result.cv_f1_macro_scores
        for result in second_results
    ]
    assert [
        result.validation_f1_macro
        for result in first_results
    ] == [
        result.validation_f1_macro
        for result in second_results
    ]
    assert (
        model_comparison.select_provisional_finalists(
            first_results
        ).ranked_model_names
        == model_comparison.select_provisional_finalists(
            second_results
        ).ranked_model_names
    )

    pd.testing.assert_frame_equal(splits[0], originals[0])
    pd.testing.assert_series_equal(splits[1], originals[1])
    pd.testing.assert_frame_equal(splits[2], originals[2])
    pd.testing.assert_series_equal(splits[3], originals[3])


@pytest.mark.parametrize(
    ("mutation", "expected_message"),
    (
        (
            lambda frame: frame.assign(
                score_referencia=50
            ),
            "exatamente as cinco features oficiais",
        ),
        (
            lambda frame: frame.loc[
                :,
                list(reversed(schema.FEATURE_COLUMNS)),
            ],
            "exatamente as cinco features oficiais",
        ),
        (
            lambda frame: frame.assign(
                consumo_kwh=np.nan
            ),
            "valores nulos",
        ),
        (
            lambda frame: frame.assign(
                consumo_kwh=np.inf
            ),
            "valores não finitos",
        ),
        (
            lambda frame: frame.assign(
                uso_horario_pico=1
            ),
            "tipo booleano",
        ),
        (
            lambda frame: frame.assign(
                tipo_imovel="INVALIDO"
            ),
            "tipos de imóvel inválidos",
        ),
    ),
)
def test_compare_candidate_models_rejeita_features_invalidas(
    mutation: object,
    expected_message: str,
) -> None:
    x_train, y_train, x_validation, y_validation = (
        _build_explicit_splits()
    )
    invalid_x_train = mutation(x_train)

    with pytest.raises(
        (TypeError, ValueError),
        match=expected_message,
    ):
        model_comparison.compare_candidate_models(
            invalid_x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_compare_candidate_models_rejeita_colunas_duplicadas() -> None:
    x_train, y_train, x_validation, y_validation = (
        _build_explicit_splits()
    )
    duplicated_x_train = pd.concat(
        [
            x_train,
            x_train.loc[:, ["consumo_kwh"]],
        ],
        axis=1,
    )

    with pytest.raises(
        ValueError,
        match="nomes de colunas duplicados",
    ):
        model_comparison.compare_candidate_models(
            duplicated_x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_compare_candidate_models_rejeita_categoria_invalida() -> None:
    x_train, y_train, x_validation, y_validation = (
        _build_explicit_splits()
    )
    invalid_y_train = y_train.copy()
    invalid_y_train.iloc[0] = "INVALIDA"

    with pytest.raises(
        ValueError,
        match="categorias inválidas",
    ):
        model_comparison.compare_candidate_models(
            x_train,
            invalid_y_train,
            x_validation,
            y_validation,
        )


def test_compare_candidate_models_rejeita_indices_sobrepostos() -> None:
    x_train, y_train, x_validation, y_validation = (
        _build_explicit_splits()
    )
    x_validation = x_validation.copy()
    y_validation = y_validation.copy()
    x_validation.index = pd.Index(
        range(100, 118),
        name="registro",
    )
    y_validation.index = x_validation.index

    with pytest.raises(
        ValueError,
        match="índices sobrepostos",
    ):
        model_comparison.compare_candidate_models(
            x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_compare_candidate_models_rejeita_target_desalinhado() -> None:
    splits = list(_build_explicit_splits())
    splits[1] = splits[1].sample(
        frac=1.0,
        random_state=schema.RANDOM_SEED,
    )

    with pytest.raises(
        ValueError,
        match="índices desalinhados",
    ):
        model_comparison.compare_candidate_models(*splits)


def test_compare_candidate_models_rejeita_target_incompleto() -> None:
    x_train, y_train, x_validation, y_validation = (
        _build_explicit_splits()
    )
    invalid_y_train = y_train.replace(
        "INEFICIENTE",
        "MODERADO",
    )

    with pytest.raises(
        ValueError,
        match="não contém todas as categorias obrigatórias",
    ):
        model_comparison.compare_candidate_models(
            x_train,
            invalid_y_train,
            x_validation,
            y_validation,
        )


def test_compare_candidate_models_rejeita_classe_insuficiente_para_cv(
) -> None:
    x_train, y_train = _build_feature_frame(100, 12)
    x_validation, y_validation = _build_feature_frame(1_000, 18)

    with pytest.raises(
        ValueError,
        match="ao menos 5 registros de cada categoria para CV",
    ):
        model_comparison.compare_candidate_models(
            x_train,
            y_train,
            x_validation,
            y_validation,
        )


def test_select_provisional_finalists_exclui_dummy_do_ranking() -> None:
    results = _build_results(
        {
            "dummy": 1.00,
            "regressao_logistica": 0.91,
            "arvore_decisao": 0.80,
            "random_forest": 0.90,
            "hist_gradient_boosting": 0.70,
        }
    )

    selection = (
        model_comparison.select_provisional_finalists(
            results
        )
    )

    assert "dummy" not in selection.ranked_model_names
    assert selection.ranked_model_names == (
        "regressao_logistica",
        "random_forest",
        "arvore_decisao",
        "hist_gradient_boosting",
    )
    assert selection.provisional_finalists == (
        "regressao_logistica",
        "random_forest",
    )


def test_select_provisional_finalists_sinaliza_gap_menor_que_limite(
) -> None:
    results = _build_results(
        {
            "dummy": 0.20,
            "regressao_logistica": 0.91,
            "arvore_decisao": 0.891,
            "random_forest": 0.90,
            "hist_gradient_boosting": 0.70,
        }
    )

    selection = (
        model_comparison.select_provisional_finalists(
            results
        )
    )

    assert selection.provisional_finalists == (
        "regressao_logistica",
        "random_forest",
    )
    assert selection.cutoff_f1_gap == pytest.approx(0.009)
    assert selection.requires_cutoff_review is True


def test_select_provisional_finalists_nao_sinaliza_gap_igual_ao_limite(
) -> None:
    results = _build_results(
        {
            "dummy": 0.20,
            "regressao_logistica": 0.91,
            "arvore_decisao": 0.56,
            "random_forest": 0.57,
            "hist_gradient_boosting": 0.20,
        }
    )

    selection = (
        model_comparison.select_provisional_finalists(
            results
        )
    )

    assert selection.cutoff_f1_gap == pytest.approx(0.01)
    assert selection.requires_cutoff_review is False


def test_select_provisional_finalists_ordena_pela_media_da_cv() -> None:
    cv_means = {
        "dummy": 0.20,
        "regressao_logistica": 0.90,
        "arvore_decisao": 0.80,
        "random_forest": 0.70,
        "hist_gradient_boosting": 0.60,
    }
    validation_scores = {
        "dummy": 0.20,
        "regressao_logistica": 0.10,
        "arvore_decisao": 0.99,
        "random_forest": 0.98,
        "hist_gradient_boosting": 0.97,
    }
    results = _build_results(
        cv_means,
        validation_scores=validation_scores,
    )

    selection = (
        model_comparison.select_provisional_finalists(
            results
        )
    )

    assert selection.ranked_model_names[0] == (
        "regressao_logistica"
    )
    assert selection.provisional_finalists == (
        "regressao_logistica",
        "arvore_decisao",
    )


def test_select_provisional_finalists_ignora_tempos_no_ranking() -> None:
    scores = {
        "dummy": 0.99,
        "regressao_logistica": 0.80,
        "arvore_decisao": 0.80,
        "random_forest": 0.80,
        "hist_gradient_boosting": 0.80,
    }
    fit_times = {
        "dummy": 50.0,
        "regressao_logistica": 40.0,
        "arvore_decisao": 30.0,
        "random_forest": 20.0,
        "hist_gradient_boosting": 0.01,
    }
    results = _build_results(
        scores,
        fit_times=fit_times,
    )

    selection = (
        model_comparison.select_provisional_finalists(
            results
        )
    )

    assert (
        selection.ranked_model_names
        == model_comparison.CANDIDATE_MODEL_NAMES
    )
    assert selection.provisional_finalists == (
        "regressao_logistica",
        "arvore_decisao",
    )


def test_compare_candidate_models_traduz_falha_de_cv(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def failing_cross_validation(*args: object, **kwargs: object) -> None:
        raise ValueError("falha simulada na CV")

    monkeypatch.setattr(
        model_comparison,
        "cross_val_score",
        failing_cross_validation,
    )

    with pytest.raises(
        model_comparison.ModelComparisonError,
        match=(
            "Falha na validação cruzada do modelo dummy: "
            "falha simulada na CV"
        ),
    ) as captured_error:
        model_comparison.compare_candidate_models(
            *_build_explicit_splits()
        )

    assert isinstance(
        captured_error.value.__cause__,
        ValueError,
    )


def test_compare_candidate_models_traduz_falha_de_treinamento(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingModel:
        def fit(self, features: object, target: object) -> None:
            raise ValueError("falha simulada")

    monkeypatch.setattr(
        model_comparison,
        "cross_val_score",
        lambda *args, **kwargs: np.full(
            model_comparison.CV_TOTAL_SPLITS,
            0.5,
        ),
    )
    monkeypatch.setattr(
        modeling_pipeline,
        "build_model_pipeline",
        lambda estimator, feature_columns: FailingModel(),
    )

    with pytest.raises(
        model_comparison.ModelComparisonError,
        match=(
            "Falha ao avaliar o modelo dummy: "
            "falha simulada"
        ),
    ) as captured_error:
        model_comparison.compare_candidate_models(
            *_build_explicit_splits()
        )

    assert isinstance(
        captured_error.value.__cause__,
        ValueError,
    )


def test_compare_candidate_models_promove_convergence_warning(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class WarningModel:
        def fit(
            self,
            features: object,
            target: object,
        ) -> "WarningModel":
            warnings.warn(
                "convergência simulada",
                ConvergenceWarning,
                stacklevel=2,
            )
            return self

    monkeypatch.setattr(
        model_comparison,
        "cross_val_score",
        lambda *args, **kwargs: np.full(
            model_comparison.CV_TOTAL_SPLITS,
            0.5,
        ),
    )
    monkeypatch.setattr(
        modeling_pipeline,
        "build_model_pipeline",
        lambda estimator, feature_columns: WarningModel(),
    )

    with pytest.raises(
        model_comparison.ModelConvergenceError,
        match=(
            "Falha de convergência ao avaliar o modelo dummy"
        ),
    ):
        model_comparison.compare_candidate_models(
            *_build_explicit_splits()
        )


def test_compare_candidate_models_rejeita_predicoes_instaveis(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class AlternatingModel:
        def __init__(self) -> None:
            self.call_count = 0

        def fit(
            self,
            features: object,
            target: object,
        ) -> "AlternatingModel":
            return self

        def predict(
            self,
            features: pd.DataFrame,
        ) -> np.ndarray:
            self.call_count += 1
            category = (
                "EFICIENTE"
                if self.call_count % 2
                else "MODERADO"
            )

            return np.full(len(features), category)

    monkeypatch.setattr(
        model_comparison,
        "cross_val_score",
        lambda *args, **kwargs: np.full(
            model_comparison.CV_TOTAL_SPLITS,
            0.5,
        ),
    )
    monkeypatch.setattr(
        modeling_pipeline,
        "build_model_pipeline",
        lambda estimator, feature_columns: AlternatingModel(),
    )

    with pytest.raises(
        model_comparison.ModelComparisonError,
        match="Predições não determinísticas",
    ):
        model_comparison.compare_candidate_models(
            *_build_explicit_splits()
        )


@pytest.mark.parametrize(
    ("mutation", "expected_exception"),
    (
        (
            lambda result: replace(
                result,
                role="invalid",
            ),
            ValueError,
        ),
        (
            lambda result: replace(
                result,
                cv_f1_macro_scores=(0.5,) * 4,
            ),
            model_comparison.ModelComparisonError,
        ),
        (
            lambda result: replace(
                result,
                cv_f1_macro_mean=float("nan"),
            ),
            ValueError,
        ),
        (
            lambda result: replace(
                result,
                cv_f1_macro_std=-1.0,
            ),
            ValueError,
        ),
        (
            lambda result: replace(
                result,
                validation_f1_macro=float("nan"),
            ),
            ValueError,
        ),
    ),
)
def test_select_provisional_finalists_rejeita_resultado_invalido(
    mutation: object,
    expected_exception: type[Exception],
) -> None:
    results = list(
        _build_results(
            {
                "dummy": 0.20,
                "regressao_logistica": 0.90,
                "arvore_decisao": 0.80,
                "random_forest": 0.70,
                "hist_gradient_boosting": 0.60,
            }
        )
    )
    results[0] = mutation(results[0])

    with pytest.raises(expected_exception):
        model_comparison.select_provisional_finalists(
            tuple(results)
        )


@pytest.mark.parametrize(
    "invalid_results",
    (
        (),
        [],
    ),
)
def test_select_provisional_finalists_rejeita_colecao_invalida(
    invalid_results: object,
) -> None:
    with pytest.raises((TypeError, ValueError)):
        model_comparison.select_provisional_finalists(
            invalid_results
        )


def test_compare_candidate_models_expoe_somente_splits_permitidos(
) -> None:
    parameter_names = tuple(
        inspect.signature(
            model_comparison.compare_candidate_models
        ).parameters
    )

    assert parameter_names == (
        "x_train",
        "y_train",
        "x_validation",
        "y_validation",
        "seed",
    )


def test_model_comparison_nao_referencia_conjunto_reservado() -> None:
    source = inspect.getsource(model_comparison).lower()

    forbidden_tokens = (
        "x_test",
        "y_test",
        "test_size",
        "holdout",
    )

    assert all(
        token not in source
        for token in forbidden_tokens
    )
