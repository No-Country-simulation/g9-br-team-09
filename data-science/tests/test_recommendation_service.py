from app.schemas.prediction import EnergyCategory, PredictionRequest
from app.services.recommendation_service import (
    FALLBACK_RECOMMENDATION,
    MAX_RECOMMENDATIONS,
    RECOMMENDATION_RULES,
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


def _recommend(
    category: EnergyCategory = EnergyCategory.MODERADO,
    score: int = 50,
    **request_overrides,
) -> list[str]:
    return RecommendationService().recommend(_request(**request_overrides), category, score)


def _message(rule_code: str) -> str:
    return next(rule.message for rule in RECOMMENDATION_RULES if rule.code == rule_code)


def test_efficient_residential_profile_reinforces_positive_habits() -> None:
    assert _recommend(EnergyCategory.EFICIENTE, 18) == [
        _message("EFFICIENT_RESIDENTIAL_PROFILE")
    ]


def test_residence_using_peak_hours_receives_residential_guidance() -> None:
    recommendations = _recommend(uso_horario_pico=True)

    assert recommendations == [_message("RESIDENTIAL_PEAK_HOURS")]


def test_commerce_with_high_consumption_receives_commercial_guidance() -> None:
    recommendations = _recommend(tipo_imovel="COMERCIO", consumo_kwh=650)

    assert recommendations == [_message("COMMERCE_HIGH_CONSUMPTION")]


def test_industry_with_many_devices_receives_operational_guidance() -> None:
    recommendations = _recommend(tipo_imovel="INDUSTRIA", quantidade_equipamentos=20)

    assert recommendations == [_message("INDUSTRY_MANY_DEVICES")]


def test_high_consumption_combined_with_many_hours_has_priority() -> None:
    recommendations = _recommend(consumo_kwh=700, horas_alto_consumo=10)

    assert recommendations == [_message("HIGH_CONSUMPTION_EXTENDED")]


def test_high_severity_uses_category_and_score() -> None:
    assert _recommend(EnergyCategory.INEFICIENTE, 80) == [_message("HIGH_SEVERITY")]
    assert _message("HIGH_SEVERITY") not in _recommend(EnergyCategory.INEFICIENTE, 69)
    assert _message("HIGH_SEVERITY") not in _recommend(EnergyCategory.MODERADO, 80)


def test_recommendations_are_deterministic_prioritized_unique_and_bounded() -> None:
    request_overrides = {
        "consumo_kwh": 700,
        "uso_horario_pico": True,
        "quantidade_equipamentos": 20,
        "horas_alto_consumo": 10,
    }
    recommendations = _recommend(EnergyCategory.INEFICIENTE, 80, **request_overrides)

    assert recommendations == [
        _message("PEAK_HOURS_EXTENDED"),
        _message("HIGH_CONSUMPTION_EXTENDED"),
        _message("HIGH_SEVERITY"),
    ]
    assert len(recommendations) == len(set(recommendations)) == MAX_RECOMMENDATIONS
    assert _recommend(EnergyCategory.INEFICIENTE, 80, **request_overrides) == recommendations


def test_rule_metadata_is_unique_and_explains_objective_conditions() -> None:
    assert len({rule.code for rule in RECOMMENDATION_RULES}) == len(RECOMMENDATION_RULES)
    assert len({rule.priority for rule in RECOMMENDATION_RULES}) == len(RECOMMENDATION_RULES)
    assert len({rule.message for rule in RECOMMENDATION_RULES}) == len(RECOMMENDATION_RULES)
    assert all(rule.justification and rule.trigger_variables for rule in RECOMMENDATION_RULES)


def test_recommendation_fallback_is_never_empty() -> None:
    assert _recommend(tipo_imovel="OUTRO") == [FALLBACK_RECOMMENDATION]
