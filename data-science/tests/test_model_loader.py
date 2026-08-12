from pathlib import Path

import httpx
import pytest

from app.core.config import Settings
from app.core.exceptions import InferenceError, ModelLoadError
from app.main import create_app
from app.models.model_loader import ModelLoader, ScikitLearnModelAdapter
from tests.conftest import FakeClassifier


def test_loader_rejects_missing_artifact(tmp_path: Path) -> None:
    with pytest.raises(ModelLoadError, match="não está disponível"):
        ModelLoader().load(tmp_path / "ausente.joblib")


def test_adapter_rejects_model_without_predict_proba() -> None:
    class MissingProbabilityMethod:
        classes_ = ("EFICIENTE", "MODERADO", "INEFICIENTE")

    with pytest.raises(ModelLoadError, match="predict_proba"):
        ScikitLearnModelAdapter(MissingProbabilityMethod())


def test_adapter_rejects_invalid_classes() -> None:
    class InvalidClasses:
        classes_ = ("EFICIENTE", "MODERADO", "DESCONHECIDO")

        def predict_proba(self, _):
            return [[0.2, 0.3, 0.5]]

    with pytest.raises(ModelLoadError, match="classes incompatíveis"):
        ScikitLearnModelAdapter(InvalidClasses())


def test_adapter_rejects_missing_classes() -> None:
    class MissingClasses:
        def predict_proba(self, _):
            return [[0.2, 0.3, 0.5]]

    with pytest.raises(ModelLoadError, match="classes_"):
        ScikitLearnModelAdapter(MissingClasses())


def test_adapter_rejects_duplicate_classes() -> None:
    class DuplicateClasses:
        classes_ = ("EFICIENTE", "EFICIENTE", "INEFICIENTE")

        def predict_proba(self, _):
            return [[0.2, 0.3, 0.5]]

    with pytest.raises(ModelLoadError, match="categorias esperadas"):
        ScikitLearnModelAdapter(DuplicateClasses())


def test_adapter_rejects_wrong_probability_vector_size() -> None:
    class WrongProbabilityCount:
        classes_ = ("EFICIENTE", "MODERADO", "INEFICIENTE")

        def predict_proba(self, _):
            return [[0.2, 0.8]]

    adapter = ScikitLearnModelAdapter(WrongProbabilityCount())
    with pytest.raises(InferenceError, match="quantidade inválida"):
        adapter.predict_probabilities(None)


@pytest.mark.anyio
async def test_startup_fails_without_configured_artifact(tmp_path: Path) -> None:
    application = create_app(
        settings=Settings(model_path=tmp_path / "ausente.joblib", model_version="test"),
    )

    with pytest.raises(ModelLoadError, match="não está disponível"):
        async with application.router.lifespan_context(application):
            pass


@pytest.mark.anyio
async def test_joblib_load_occurs_once_at_startup(monkeypatch, tmp_path: Path) -> None:
    artifact_path = tmp_path / "modelo.joblib"
    artifact_path.touch()
    calls = 0

    def load_model(path: Path) -> FakeClassifier:
        nonlocal calls
        assert path == artifact_path
        calls += 1
        return FakeClassifier()

    monkeypatch.setattr("app.models.model_loader.joblib.load", load_model)
    application = create_app(
        settings=Settings(model_path=artifact_path, model_version="test"),
    )
    async with application.router.lifespan_context(application):
        transport = httpx.ASGITransport(app=application)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            for _ in range(2):
                response = await client.post(
                    "/predict",
                    json={
                        "consumo_kwh": 420.0,
                        "uso_horario_pico": True,
                        "quantidade_equipamentos": 10,
                        "tipo_imovel": "CASA",
                        "horas_alto_consumo": 8,
                    },
                )
                assert response.status_code == 200

    assert calls == 1
