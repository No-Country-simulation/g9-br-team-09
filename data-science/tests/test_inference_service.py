import pytest
import numpy as np

from app.core.exceptions import InferenceError
from app.models.model_loader import ScikitLearnModelAdapter
from app.schemas.prediction import PredictionRequest
from app.services.inference_service import FEATURE_COLUMNS, InferenceService
from tests.conftest import FakeClassifier


def test_inference_uses_argmax_class_mapping_and_expected_severity_score() -> None:
    classifier = FakeClassifier(probabilities=[0.20, 0.65, 0.15])
    service = InferenceService(ScikitLearnModelAdapter(classifier), "test-version")
    request = PredictionRequest(
        consumo_kwh=320,
        uso_horario_pico=False,
        quantidade_equipamentos=5,
        tipo_imovel="APARTAMENTO",
        horas_alto_consumo=2,
    )

    result = service.predict(request)

    assert result.categoria == "EFICIENTE"
    assert result.probabilidade == 0.65
    assert result.score == 28
    assert tuple(classifier.last_features.columns) == FEATURE_COLUMNS
    assert classifier.last_features.shape == (1, 5)
    assert classifier.last_features.to_dict(orient="records") == [
        {
            "consumo_kwh": 320.0,
            "uso_horario_pico": False,
            "quantidade_equipamentos": 5,
            "tipo_imovel": "APARTAMENTO",
            "horas_alto_consumo": 2,
        }
    ]


def test_category_is_not_recomputed_from_score_ranges() -> None:
    classifier = FakeClassifier(probabilities=[0.45, 0.40, 0.15])
    service = InferenceService(ScikitLearnModelAdapter(classifier), "test-version")
    request = PredictionRequest(
        consumo_kwh=220,
        uso_horario_pico=False,
        quantidade_equipamentos=3,
        tipo_imovel="CASA",
        horas_alto_consumo=1,
    )

    result = service.predict(request)

    assert result.categoria == "INEFICIENTE"
    assert result.score == 52


def test_invalid_probabilities_are_rejected() -> None:
    classifier = FakeClassifier(probabilities=[float("nan"), 0.2, 0.8])
    service = InferenceService(ScikitLearnModelAdapter(classifier), "test-version")
    request = PredictionRequest(
        consumo_kwh=220,
        uso_horario_pico=False,
        quantidade_equipamentos=3,
        tipo_imovel="CASA",
        horas_alto_consumo=1,
    )

    with pytest.raises(InferenceError, match="fora do intervalo"):
        service.predict(request)


@pytest.mark.parametrize(
    "probabilities, message",
    [
        ([-0.1, 0.2, 0.9], "fora do intervalo"),
        ([0.1, 0.2, 1.1], "fora do intervalo"),
        ([float("inf"), 0.2, 0.8], "fora do intervalo"),
        ([0.1, 0.2, 0.2], "soma inválida"),
    ],
)
def test_out_of_range_or_incoherent_probabilities_are_rejected(probabilities, message: str) -> None:
    service = InferenceService(ScikitLearnModelAdapter(FakeClassifier(probabilities=probabilities)), "test-version")
    request = PredictionRequest(
        consumo_kwh=220,
        uso_horario_pico=False,
        quantidade_equipamentos=3,
        tipo_imovel="CASA",
        horas_alto_consumo=1,
    )

    with pytest.raises(InferenceError, match=message):
        service.predict(request)


def test_numpy_probability_values_are_converted_to_python_response_types() -> None:
    classifier = FakeClassifier(probabilities=np.array([np.float64(0.2), np.float64(0.1), np.float64(0.7)]))
    service = InferenceService(ScikitLearnModelAdapter(classifier), "test-version")
    request = PredictionRequest(
        consumo_kwh=220,
        uso_horario_pico=False,
        quantidade_equipamentos=3,
        tipo_imovel="CASA",
        horas_alto_consumo=1,
    )

    result = service.predict(request)

    assert result.categoria == "MODERADO"
    assert type(result.probabilidade) is float
    assert type(result.score) is int


@pytest.mark.anyio
async def test_model_is_loaded_once_and_called_per_request(client, loader, classifier, valid_request) -> None:
    assert loader.load_calls == 1

    await client.post("/predict", json=valid_request)
    await client.post("/predict", json=valid_request)

    assert loader.load_calls == 1
    assert classifier.predict_calls == 2
