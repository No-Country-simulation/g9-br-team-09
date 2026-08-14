import pytest
import httpx

from app.core.config import Settings
from app.main import create_app
from tests.conftest import FakeClassifier, FakeModelLoader


@pytest.mark.anyio
async def test_predict_returns_backend_compatible_payload(client, valid_request: dict[str, object]) -> None:
    response = await client.post("/predict", json=valid_request)

    assert response.status_code == 200
    assert response.json() == {
        "categoria": "INEFICIENTE",
        "probabilidade": 0.7,
        "score": 80,
        "recomendacoes": [
            "Redistribua as atividades de alto consumo para fora do horário de pico sempre que possível.",
            "Defina um plano de redução gradual e acompanhe sua evolução mensal.",
        ],
        "modelo_versao": "energy-classifier-v2-test",
    }


@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"consumo_kwh": 0},
        {"consumo_kwh": -1},
        {"consumo_kwh": "420", "uso_horario_pico": True, "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 0, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 10.5, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": -1},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 25},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 1, "tipo_imovel": "GALPAO", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": "true", "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": "false", "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": 1, "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 1},
        {"consumo_kwh": 1, "uso_horario_pico": True, "quantidade_equipamentos": 1, "tipo_imovel": "CASA", "horas_alto_consumo": 1, "extra": "forbidden"},
    ],
)
@pytest.mark.anyio
async def test_predict_rejects_invalid_input(client, payload: dict[str, object]) -> None:
    assert (await client.post("/predict", json=payload)).status_code == 422


@pytest.mark.anyio
async def test_predict_hides_model_errors(tmp_path, valid_request: dict[str, object], caplog) -> None:
    failing_loader = FakeModelLoader(FakeClassifier(failure=RuntimeError("internal model detail")))
    settings = Settings(model_path=tmp_path / "modelo-oficial.joblib", model_version="test")

    application = create_app(settings=settings, model_loader=failing_loader)
    async with application.router.lifespan_context(application):
        transport = httpx.ASGITransport(app=application)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            response = await client.post("/predict", json=valid_request)

    assert response.status_code == 500
    assert response.json() == {"detail": "Não foi possível executar a inferência."}
    assert "categoria" not in response.json()
    assert "internal model detail" not in response.text
    assert "Inference failed: O modelo não conseguiu calcular probabilidades." in caplog.text
    assert "internal model detail" not in caplog.text
    assert str(settings.model_path) not in caplog.text


@pytest.mark.anyio
async def test_predict_accepts_all_backend_property_types(client, valid_request: dict[str, object]) -> None:
    for property_type in ("CASA", "APARTAMENTO", "COMERCIO", "ESCRITORIO", "INDUSTRIA", "OUTRO"):
        payload = {**valid_request, "tipo_imovel": property_type}
        assert (await client.post("/predict", json=payload)).status_code == 200


@pytest.mark.anyio
async def test_openapi_documents_predict_request_and_response(client) -> None:
    response = await client.get("/openapi.json")

    assert response.status_code == 200
    operation = response.json()["paths"]["/predict"]["post"]
    assert "requestBody" in operation
    assert "200" in operation["responses"]
    assert "/health" in response.json()["paths"]
