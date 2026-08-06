"""Testes das fábricas de pré-processamento e pipelines."""

import re
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import modeling_pipeline  # noqa: E402
import schema  # noqa: E402


def _build_training_data() -> tuple[pd.DataFrame, pd.Series]:
    """Cria dados pequenos sem consultar o dataset ou o holdout."""
    features = pd.DataFrame(
        {
            "consumo_kwh": [
                100.0,
                180.0,
                260.0,
                340.0,
                420.0,
                500.0,
                580.0,
                660.0,
                740.0,
            ],
            "uso_horario_pico": [
                False,
                False,
                True,
                False,
                True,
                True,
                False,
                True,
                True,
            ],
            "quantidade_equipamentos": [
                3,
                5,
                8,
                10,
                13,
                16,
                19,
                22,
                25,
            ],
            "tipo_imovel": [
                "CASA",
                "APARTAMENTO",
                "COMERCIO",
                "CASA",
                "APARTAMENTO",
                "COMERCIO",
                "CASA",
                "APARTAMENTO",
                "COMERCIO",
            ],
            "horas_alto_consumo": [
                1,
                2,
                4,
                5,
                7,
                9,
                11,
                13,
                15,
            ],
        },
        index=pd.Index(
            range(100, 109),
            name="registro",
        ),
    )

    target = pd.Series(
        [
            "EFICIENTE",
            "EFICIENTE",
            "MODERADO",
            "MODERADO",
            "INEFICIENTE",
            "INEFICIENTE",
            "EFICIENTE",
            "MODERADO",
            "INEFICIENTE",
        ],
        index=features.index,
        name=schema.TARGET_COLUMN,
    )

    return features, target


def test_build_preprocessor_retorna_transformador_nao_ajustado() -> None:
    preprocessor = modeling_pipeline.build_preprocessor()

    assert isinstance(preprocessor, ColumnTransformer)
    assert not hasattr(preprocessor, "transformers_")


@pytest.mark.parametrize(
    "feature",
    schema.FEATURE_COLUMNS,
)
def test_build_preprocessor_suporta_cada_feature(
    feature: str,
) -> None:
    features, target = _build_training_data()

    preprocessor = modeling_pipeline.build_preprocessor(
        (feature,)
    )
    transformed = preprocessor.fit_transform(
        features.loc[:, [feature]],
        target,
    )

    assert transformed.shape[0] == len(features)
    assert transformed.shape[1] >= 1
    assert np.isfinite(transformed).all()


@pytest.mark.parametrize(
    (
        "feature_columns",
        "expected_exception",
        "expected_message",
    ),
    (
        (
            (),
            ValueError,
            "feature_columns não pode estar vazio",
        ),
        (
            "consumo_kwh",
            TypeError,
            "sequência de strings",
        ),
        (
            ("consumo_kwh", 1),
            TypeError,
            "somente strings",
        ),
        (
            ("consumo_kwh", "consumo_kwh"),
            ValueError,
            "contém duplicatas",
        ),
        (
            ("score_referencia",),
            ValueError,
            "Features inválidas",
        ),
    ),
)
def test_build_preprocessor_rejeita_features_invalidas(
    feature_columns: object,
    expected_exception: type[Exception],
    expected_message: str,
) -> None:
    with pytest.raises(
        expected_exception,
        match=re.escape(expected_message),
    ):
        modeling_pipeline.build_preprocessor(
            feature_columns
        )


def test_build_preprocessor_ignora_categoria_desconhecida() -> None:
    features, target = _build_training_data()
    original_features = features.copy(deep=True)

    preprocessor = modeling_pipeline.build_preprocessor()
    transformed_training = preprocessor.fit_transform(
        features,
        target,
    )

    unknown_features = features.iloc[[0]].copy()
    unknown_features.loc[
        :,
        "tipo_imovel",
    ] = "DESCONHECIDO"

    transformed_unknown = preprocessor.transform(
        unknown_features
    )

    encoder = preprocessor.named_transformers_[
        "categorical"
    ]
    encoded_unknown = encoder.transform(
        unknown_features.loc[:, ["tipo_imovel"]]
    )

    assert encoder.handle_unknown == "ignore"
    assert transformed_unknown.shape[1] == (
        transformed_training.shape[1]
    )
    assert np.isfinite(transformed_unknown).all()
    assert np.all(encoded_unknown == 0.0)
    pd.testing.assert_frame_equal(
        features,
        original_features,
    )


def test_build_model_pipeline_clona_estimador() -> None:
    estimator = LogisticRegression(
        max_iter=2_000,
        random_state=schema.RANDOM_SEED,
    )

    pipeline = modeling_pipeline.build_model_pipeline(
        estimator
    )

    assert isinstance(pipeline, Pipeline)
    assert tuple(pipeline.named_steps) == (
        "preprocessor",
        "classifier",
    )
    assert pipeline.named_steps["classifier"] is not estimator
    assert (
        pipeline.named_steps["classifier"].get_params()
        == estimator.get_params()
    )
    assert not hasattr(estimator, "classes_")


def test_build_model_pipeline_ajusta_sem_alterar_entradas() -> None:
    features, target = _build_training_data()
    original_features = features.copy(deep=True)
    original_target = target.copy(deep=True)

    estimator = LogisticRegression(
        max_iter=2_000,
        random_state=schema.RANDOM_SEED,
    )
    pipeline = modeling_pipeline.build_model_pipeline(
        estimator
    )

    pipeline.fit(features, target)
    predictions = pipeline.predict(features)

    assert len(predictions) == len(features)
    assert set(predictions).issubset(
        schema.ENERGY_CATEGORIES
    )
    assert not hasattr(estimator, "classes_")
    pd.testing.assert_frame_equal(
        features,
        original_features,
    )
    pd.testing.assert_series_equal(
        target,
        original_target,
    )


def test_build_model_pipeline_rejeita_estimador_invalido() -> None:
    with pytest.raises(
        TypeError,
        match="estimador scikit-learn",
    ):
        modeling_pipeline.build_model_pipeline(object())
