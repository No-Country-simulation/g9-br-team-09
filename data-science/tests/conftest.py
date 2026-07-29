from collections.abc import AsyncIterator
from pathlib import Path

import httpx
import pytest

from app.core.config import Settings
from app.main import create_app
from app.models.model_loader import ScikitLearnModelAdapter


class FakeClassifier:
    classes_ = ("INEFICIENTE", "EFICIENTE", "MODERADO")

    def __init__(self, probabilities: list[float] | None = None, failure: Exception | None = None) -> None:
        self.probabilities = [0.70, 0.10, 0.20] if probabilities is None else probabilities
        self.failure = failure
        self.predict_calls = 0
        self.last_features = None

    def predict_proba(self, features):
        self.predict_calls += 1
        self.last_features = features.copy()
        if self.failure is not None:
            raise self.failure
        return [self.probabilities]


class FakeModelLoader:
    def __init__(self, classifier: FakeClassifier) -> None:
        self.classifier = classifier
        self.load_calls = 0

    def load(self, _: Path) -> ScikitLearnModelAdapter:
        self.load_calls += 1
        return ScikitLearnModelAdapter(self.classifier)


@pytest.fixture
def valid_request() -> dict[str, object]:
    return {
        "consumo_kwh": 420.0,
        "uso_horario_pico": True,
        "quantidade_equipamentos": 10,
        "tipo_imovel": "CASA",
        "horas_alto_consumo": 8,
    }


@pytest.fixture
def classifier() -> FakeClassifier:
    return FakeClassifier()


@pytest.fixture
def loader(classifier: FakeClassifier) -> FakeModelLoader:
    return FakeModelLoader(classifier)


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture
async def client(tmp_path: Path, loader: FakeModelLoader) -> AsyncIterator[httpx.AsyncClient]:
    settings = Settings(model_path=tmp_path / "modelo-oficial.joblib", model_version="energy-classifier-v2-test")
    application = create_app(settings=settings, model_loader=loader)
    async with application.router.lifespan_context(application):
        transport = httpx.ASGITransport(app=application)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as async_client:
            yield async_client
