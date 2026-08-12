"""Testes de contrato do artefato oficial de modelagem EnergIAI V2.

Este módulo valida exclusivamente o artefato serializado e seu contrato de
inferência usando entradas sintéticas definidas localmente. Nenhum teste deste
arquivo carrega, consulta ou avalia o holdout oficial.
"""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import pytest
from sklearn.calibration import CalibratedClassifierCV
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.models.model_loader import ModelLoader
from app.schemas.prediction import EnergyCategory
from app.services.inference_service import FEATURE_COLUMNS

pytestmark = pytest.mark.filterwarnings(
    r"ignore:Setting the shape on a NumPy array has been deprecated in NumPy 2\.5\.:DeprecationWarning:joblib\.numpy_pickle"
)

DATA_SCIENCE_DIR = Path(__file__).resolve().parents[1]
MODEL_PATH = DATA_SCIENCE_DIR / "models" / "modelo_energetico_v2.joblib"
METADATA_PATH = (
    DATA_SCIENCE_DIR
    / "models"
    / "modelo_energetico_v2.metadata.json"
)

EXPECTED_MODEL_SHA256 = (
    "ba4a2d8df87d0e0d6f4226a7b782f193a16c9722029c45cf2ab17a707532380e"
)
EXPECTED_MODEL_SIZE = 2_142_851

EXPECTED_FEATURES = (
    "consumo_kwh",
    "uso_horario_pico",
    "quantidade_equipamentos",
    "tipo_imovel",
    "horas_alto_consumo",
)
EXPECTED_NUMERICAL_FEATURES = (
    "consumo_kwh",
    "uso_horario_pico",
    "quantidade_equipamentos",
    "horas_alto_consumo",
)
EXPECTED_CATEGORICAL_FEATURES = ("tipo_imovel",)
EXPECTED_MODEL_CLASSES = (
    "EFICIENTE",
    "INEFICIENTE",
    "MODERADO",
)

SYNTHETIC_FEATURES = pd.DataFrame(
    [
        {
            "consumo_kwh": 220.0,
            "uso_horario_pico": False,
            "quantidade_equipamentos": 5,
            "tipo_imovel": "CASA",
            "horas_alto_consumo": 2,
        },
        {
            "consumo_kwh": 480.0,
            "uso_horario_pico": True,
            "quantidade_equipamentos": 14,
            "tipo_imovel": "COMERCIO",
            "horas_alto_consumo": 9,
        },
        {
            "consumo_kwh": 980.0,
            "uso_horario_pico": True,
            "quantidade_equipamentos": 32,
            "tipo_imovel": "INDUSTRIA",
            "horas_alto_consumo": 16,
        },
    ],
    columns=EXPECTED_FEATURES,
)


def _load_metadata() -> dict[str, Any]:
    return json.loads(
        METADATA_PATH.read_text(encoding="utf-8"),
    )


def _load_official_model() -> CalibratedClassifierCV:
    model = joblib.load(MODEL_PATH)

    assert isinstance(model, CalibratedClassifierCV)

    return model


def test_official_model_artifact_matches_published_identity() -> None:
    assert MODEL_PATH.is_file()
    assert METADATA_PATH.is_file()

    artifact_bytes = MODEL_PATH.read_bytes()
    artifact_hash = hashlib.sha256(artifact_bytes).hexdigest()
    metadata = _load_metadata()

    assert artifact_hash == EXPECTED_MODEL_SHA256
    assert len(artifact_bytes) == EXPECTED_MODEL_SIZE

    assert metadata["artifact"]["path"] == (
        "models/modelo_energetico_v2.joblib"
    )
    assert metadata["artifact"]["sha256"] == EXPECTED_MODEL_SHA256
    assert metadata["artifact"]["size_bytes"] == EXPECTED_MODEL_SIZE
    assert metadata["artifact"]["serialization_format"] == "joblib"

    assert metadata["model_name"] == "random_forest"
    assert metadata["model_version"] == "energy-classifier-v2"


def test_official_model_preserves_frozen_training_contract() -> None:
    metadata = _load_metadata()
    model = _load_official_model()

    assert model.method == "isotonic"
    assert model.ensemble is False
    assert model.n_jobs == 1

    assert isinstance(model.cv, StratifiedKFold)
    assert model.cv.n_splits == 5
    assert model.cv.shuffle is True
    assert model.cv.random_state == 42

    assert len(model.calibrated_classifiers_) == 1

    model_classes = tuple(str(item) for item in model.classes_)
    assert model_classes == EXPECTED_MODEL_CLASSES
    assert model_classes == tuple(metadata["model"]["classes"])

    pipeline = model.estimator
    assert isinstance(pipeline, Pipeline)
    assert tuple(pipeline.named_steps) == (
        "preprocessor",
        "classifier",
    )

    preprocessor = pipeline.named_steps["preprocessor"]
    classifier = pipeline.named_steps["classifier"]

    assert isinstance(preprocessor, ColumnTransformer)
    assert preprocessor.remainder == "drop"
    assert isinstance(classifier, RandomForestClassifier)

    transformers = {
        name: (transformer, tuple(columns))
        for name, transformer, columns in preprocessor.transformers
    }

    numerical_transformer, numerical_features = transformers["numerical"]
    categorical_transformer, categorical_features = transformers[
        "categorical"
    ]

    assert isinstance(numerical_transformer, StandardScaler)
    assert numerical_transformer.with_mean is True
    assert numerical_transformer.with_std is True
    assert numerical_features == EXPECTED_NUMERICAL_FEATURES

    assert isinstance(categorical_transformer, OneHotEncoder)
    assert categorical_transformer.handle_unknown == "ignore"
    assert categorical_transformer.sparse_output is False
    assert categorical_features == EXPECTED_CATEGORICAL_FEATURES

    assert tuple(metadata["model"]["features"]) == EXPECTED_FEATURES
    assert tuple(FEATURE_COLUMNS) == EXPECTED_FEATURES

    expected_hyperparameters = metadata["model"]["hyperparameters"]
    actual_hyperparameters = classifier.get_params(deep=False)

    for parameter, expected_value in expected_hyperparameters.items():
        assert actual_hyperparameters[parameter] == expected_value


def test_official_model_predict_proba_contract_on_synthetic_inputs() -> None:
    model = _load_official_model()

    probabilities_first = model.predict_proba(SYNTHETIC_FEATURES)
    probabilities_second = model.predict_proba(SYNTHETIC_FEATURES)

    assert probabilities_first.shape == (
        len(SYNTHETIC_FEATURES),
        len(EXPECTED_MODEL_CLASSES),
    )
    assert np.isfinite(probabilities_first).all()
    assert (probabilities_first >= 0.0).all()
    assert (probabilities_first <= 1.0).all()

    np.testing.assert_allclose(
        probabilities_first.sum(axis=1),
        np.ones(len(SYNTHETIC_FEATURES)),
        rtol=0.0,
        atol=1e-12,
    )
    np.testing.assert_array_equal(
        probabilities_first,
        probabilities_second,
    )


def test_runtime_loader_accepts_official_artifact() -> None:
    adapter = ModelLoader().load(MODEL_PATH)

    probabilities = adapter.predict_probabilities(
        SYNTHETIC_FEATURES.iloc[[0]].copy(),
    )

    assert set(probabilities) == set(EnergyCategory)
    assert all(
        math.isfinite(probability)
        and 0.0 <= probability <= 1.0
        for probability in probabilities.values()
    )
    assert math.isclose(
        math.fsum(probabilities.values()),
        1.0,
        rel_tol=0.0,
        abs_tol=1e-12,
    )
