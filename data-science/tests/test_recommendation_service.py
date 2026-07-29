from app.schemas.prediction import EnergyCategory, PredictionRequest
from app.services.recommendation_service import (
    FALLBACK_RECOMMENDATION,
    MAX_RECOMMENDATIONS,
    RecommendationService,
)


def _request(**overrides) -> PredictionRequest:
    values = {
        "consumo_kwh": 220,
        "uso_horario_pico": False,
        "quantidade_equipamentos": 3,
        "tipo_imovel": "CASA",
        "horas_alto_consumo": 2,
    }
    values.update(overrides)
    return PredictionRequest(**values)


def test_recommendations_are_prioritized_unique_and_bounded() -> None:
    recommendations = RecommendationService().recommend(
        _request(consumo_kwh=700, uso_horario_pico=True, quantidade_equipamentos=20, horas_alto_consumo=10),
        EnergyCategory.INEFICIENTE,
        80,
    )

    assert recommendations == [
        "Reduzir o uso de equipamentos durante horários de pico.",
        "Distribuir atividades de alto consumo ao longo do dia.",
        "Monitorar os principais equipamentos para reduzir o consumo mensal.",
    ]
    assert len(recommendations) == len(set(recommendations)) == MAX_RECOMMENDATIONS


def test_recommendation_fallback_is_never_empty() -> None:
    assert RecommendationService().recommend(_request(), EnergyCategory.EFICIENTE, 0) == [FALLBACK_RECOMMENDATION]
