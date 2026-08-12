"""Explainable, priority-ordered recommendations for inference results."""

from collections.abc import Callable
from dataclasses import dataclass

from app.schemas.prediction import EnergyCategory, PredictionRequest


MAX_RECOMMENDATIONS = 3
PEAK_HOURS_THRESHOLD = 8
HIGH_CONSUMPTION_KWH_THRESHOLD = 500.0
HIGH_EQUIPMENT_COUNT_THRESHOLD = 15


@dataclass(frozen=True)
class RecommendationContext:
    request: PredictionRequest
    category: EnergyCategory
    score: int


@dataclass(frozen=True)
class RecommendationRule:
    code: str
    priority: int
    message: str
    justification: str
    trigger_variables: tuple[str, ...]
    applies: Callable[[RecommendationContext], bool]


def _uses_peak_hours(context: RecommendationContext) -> bool:
    return context.request.uso_horario_pico


def _has_extended_high_consumption(context: RecommendationContext) -> bool:
    return context.request.horas_alto_consumo >= PEAK_HOURS_THRESHOLD


def _has_high_consumption(context: RecommendationContext) -> bool:
    return context.request.consumo_kwh >= HIGH_CONSUMPTION_KWH_THRESHOLD


def _has_many_devices(context: RecommendationContext) -> bool:
    return context.request.quantidade_equipamentos >= HIGH_EQUIPMENT_COUNT_THRESHOLD


def _has_high_severity(context: RecommendationContext) -> bool:
    return context.category is EnergyCategory.INEFICIENTE or context.score >= 70


RECOMMENDATION_RULES = (
    RecommendationRule(
        code="PEAK_HOURS",
        priority=10,
        message="Reduzir o uso de equipamentos durante horários de pico.",
        justification="O consumo em horário de pico aumenta a pressão sobre a demanda.",
        trigger_variables=("uso_horario_pico",),
        applies=_uses_peak_hours,
    ),
    RecommendationRule(
        code="EXTENDED_HIGH_CONSUMPTION",
        priority=20,
        message="Distribuir atividades de alto consumo ao longo do dia.",
        justification="Muitas horas de alto consumo concentram o uso de energia.",
        trigger_variables=("horas_alto_consumo",),
        applies=_has_extended_high_consumption,
    ),
    RecommendationRule(
        code="HIGH_CONSUMPTION",
        priority=30,
        message="Monitorar os principais equipamentos para reduzir o consumo mensal.",
        justification="O consumo informado está acima do limite operacional do motor de recomendações.",
        trigger_variables=("consumo_kwh",),
        applies=_has_high_consumption,
    ),
    RecommendationRule(
        code="MANY_DEVICES",
        priority=40,
        message="Priorizar o uso de equipamentos essenciais e evitar stand-by desnecessário.",
        justification="Uma quantidade elevada de equipamentos amplia oportunidades de redução.",
        trigger_variables=("quantidade_equipamentos",),
        applies=_has_many_devices,
    ),
    RecommendationRule(
        code="HIGH_SEVERITY",
        priority=50,
        message="Revisar os hábitos de consumo e definir um plano de redução gradual.",
        justification="A predição indica severidade elevada de ineficiência.",
        trigger_variables=("categoria", "score"),
        applies=_has_high_severity,
    ),
)

FALLBACK_RECOMMENDATION = "Acompanhar o consumo mensal e manter práticas de uso consciente de energia."


class RecommendationService:
    """Builds a bounded and non-empty message list from rule results."""

    def recommend(
        self,
        request: PredictionRequest,
        category: EnergyCategory,
        score: int,
    ) -> list[str]:
        context = RecommendationContext(request=request, category=category, score=score)
        messages: list[str] = []

        for rule in sorted(RECOMMENDATION_RULES, key=lambda item: item.priority):
            if rule.applies(context) and rule.message not in messages:
                messages.append(rule.message)
            if len(messages) == MAX_RECOMMENDATIONS:
                break

        return messages or [FALLBACK_RECOMMENDATION]
