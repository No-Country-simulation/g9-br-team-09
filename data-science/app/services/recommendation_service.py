"""Explainable, priority-ordered recommendations for inference results."""

from collections.abc import Callable
from dataclasses import dataclass

from app.schemas.prediction import EnergyCategory, PredictionRequest, PropertyType


MAX_RECOMMENDATIONS = 3
PEAK_HOURS_THRESHOLD = 8
HIGH_CONSUMPTION_KWH_THRESHOLD = 500.0
HIGH_EQUIPMENT_COUNT_THRESHOLD = 15
LOW_INEFFICIENCY_SCORE_THRESHOLD = 30


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
    return context.category is EnergyCategory.INEFICIENTE and context.score >= 70


def _uses_peak_hours_for_extended_periods(context: RecommendationContext) -> bool:
    return _uses_peak_hours(context) and _has_extended_high_consumption(context)


def _has_high_consumption_for_extended_periods(context: RecommendationContext) -> bool:
    return _has_high_consumption(context) and _has_extended_high_consumption(context)


def _is_residential(context: RecommendationContext) -> bool:
    return context.request.tipo_imovel in (PropertyType.CASA, PropertyType.APARTAMENTO)


def _is_residential_using_peak_hours(context: RecommendationContext) -> bool:
    return (
        _is_residential(context)
        and _uses_peak_hours(context)
        and not _has_extended_high_consumption(context)
    )


def _is_commerce_with_high_consumption(context: RecommendationContext) -> bool:
    return context.request.tipo_imovel is PropertyType.COMERCIO and _has_high_consumption(context)


def _is_industry_with_many_devices(context: RecommendationContext) -> bool:
    return context.request.tipo_imovel is PropertyType.INDUSTRIA and _has_many_devices(context)


def _is_efficient_without_critical_factors(context: RecommendationContext) -> bool:
    return (
        context.category is EnergyCategory.EFICIENTE
        and context.score < LOW_INEFFICIENCY_SCORE_THRESHOLD
        and not _uses_peak_hours(context)
        and not _has_extended_high_consumption(context)
        and not _has_high_consumption(context)
        and not _has_many_devices(context)
    )


def _is_efficient_residence_without_critical_factors(context: RecommendationContext) -> bool:
    return _is_residential(context) and _is_efficient_without_critical_factors(context)


def _is_non_residential_efficient_without_critical_factors(context: RecommendationContext) -> bool:
    return not _is_residential(context) and _is_efficient_without_critical_factors(context)


def _uses_peak_hours_without_contextual_combination(context: RecommendationContext) -> bool:
    return (
        _uses_peak_hours(context)
        and not _is_residential(context)
        and not _has_extended_high_consumption(context)
    )


def _has_extended_consumption_without_contextual_combination(
    context: RecommendationContext,
) -> bool:
    return (
        _has_extended_high_consumption(context)
        and not _uses_peak_hours(context)
        and not _has_high_consumption(context)
    )


def _has_high_consumption_without_contextual_combination(context: RecommendationContext) -> bool:
    return (
        _has_high_consumption(context)
        and context.request.tipo_imovel is not PropertyType.COMERCIO
        and not _has_extended_high_consumption(context)
    )


def _has_many_devices_without_contextual_combination(context: RecommendationContext) -> bool:
    return _has_many_devices(context) and context.request.tipo_imovel is not PropertyType.INDUSTRIA


RECOMMENDATION_RULES = (
    RecommendationRule(
        code="PEAK_HOURS_EXTENDED",
        priority=10,
        message="Redistribua as atividades de alto consumo para fora do horário de pico sempre que possível.",
        justification="O uso em horário de pico ocorre junto com oito ou mais horas de alto consumo.",
        trigger_variables=("uso_horario_pico", "horas_alto_consumo"),
        applies=_uses_peak_hours_for_extended_periods,
    ),
    RecommendationRule(
        code="HIGH_CONSUMPTION_EXTENDED",
        priority=20,
        message="Priorize a redução do tempo de operação dos equipamentos de maior consumo.",
        justification="O consumo mensal elevado ocorre junto com oito ou mais horas de alto consumo.",
        trigger_variables=("consumo_kwh", "horas_alto_consumo"),
        applies=_has_high_consumption_for_extended_periods,
    ),
    RecommendationRule(
        code="INDUSTRY_MANY_DEVICES",
        priority=30,
        message=(
            "Identifique os equipamentos industriais de maior consumo e avalie "
            "desligamentos fora dos períodos operacionais."
        ),
        justification="O imóvel é uma indústria com quinze ou mais equipamentos.",
        trigger_variables=("tipo_imovel", "quantidade_equipamentos"),
        applies=_is_industry_with_many_devices,
    ),
    RecommendationRule(
        code="COMMERCE_HIGH_CONSUMPTION",
        priority=40,
        message="Revise iluminação, climatização e equipamentos do comércio para reduzir o consumo mensal.",
        justification="O imóvel é um comércio com consumo mensal igual ou superior a 500 kWh.",
        trigger_variables=("tipo_imovel", "consumo_kwh"),
        applies=_is_commerce_with_high_consumption,
    ),
    RecommendationRule(
        code="RESIDENTIAL_PEAK_HOURS",
        priority=50,
        message="Evite concentrar equipamentos residenciais de alta potência nos períodos de maior demanda.",
        justification="O imóvel é residencial e utiliza energia em horário de pico.",
        trigger_variables=("tipo_imovel", "uso_horario_pico"),
        applies=_is_residential_using_peak_hours,
    ),
    RecommendationRule(
        code="HIGH_SEVERITY",
        priority=60,
        message="Defina um plano de redução gradual e acompanhe sua evolução mensal.",
        justification="A categoria é INEFICIENTE e o score é igual ou superior a 70.",
        trigger_variables=("categoria", "score"),
        applies=_has_high_severity,
    ),
    RecommendationRule(
        code="PEAK_HOURS",
        priority=70,
        message="Reduza o uso de equipamentos durante horários de pico.",
        justification="A análise informa uso de energia em horário de pico.",
        trigger_variables=("uso_horario_pico",),
        applies=_uses_peak_hours_without_contextual_combination,
    ),
    RecommendationRule(
        code="EXTENDED_HIGH_CONSUMPTION",
        priority=80,
        message="Distribua as atividades de alto consumo ao longo do dia.",
        justification="A análise informa oito ou mais horas de alto consumo.",
        trigger_variables=("horas_alto_consumo",),
        applies=_has_extended_consumption_without_contextual_combination,
    ),
    RecommendationRule(
        code="HIGH_CONSUMPTION",
        priority=90,
        message="Monitore os principais equipamentos para reduzir o consumo mensal.",
        justification="O consumo mensal é igual ou superior a 500 kWh.",
        trigger_variables=("consumo_kwh",),
        applies=_has_high_consumption_without_contextual_combination,
    ),
    RecommendationRule(
        code="MANY_DEVICES",
        priority=100,
        message="Priorize equipamentos essenciais e evite stand-by desnecessário.",
        justification="A análise informa quinze ou mais equipamentos.",
        trigger_variables=("quantidade_equipamentos",),
        applies=_has_many_devices_without_contextual_combination,
    ),
    RecommendationRule(
        code="EFFICIENT_RESIDENTIAL_PROFILE",
        priority=110,
        message=(
            "Seu perfil residencial apresenta baixo índice de ineficiência; "
            "mantenha os hábitos atuais e acompanhe o consumo mensal."
        ),
        justification=(
            "O imóvel é residencial, a categoria é EFICIENTE, o score é menor "
            "que 30 e não há fatores críticos."
        ),
        trigger_variables=(
            "tipo_imovel",
            "categoria",
            "score",
            "uso_horario_pico",
            "horas_alto_consumo",
            "consumo_kwh",
            "quantidade_equipamentos",
        ),
        applies=_is_efficient_residence_without_critical_factors,
    ),
    RecommendationRule(
        code="EFFICIENT_PROFILE",
        priority=120,
        message=(
            "Seu perfil apresenta baixo índice de ineficiência; mantenha os "
            "hábitos atuais e acompanhe o consumo mensal."
        ),
        justification="A categoria é EFICIENTE, o score é menor que 30 e não há fatores críticos.",
        trigger_variables=(
            "categoria",
            "score",
            "uso_horario_pico",
            "horas_alto_consumo",
            "consumo_kwh",
            "quantidade_equipamentos",
        ),
        applies=_is_non_residential_efficient_without_critical_factors,
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
