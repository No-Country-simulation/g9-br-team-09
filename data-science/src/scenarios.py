"""Configurações dos cenários sintéticos do Dataset EnergIAI V2.

Este módulo registra proporções, tipos de cenário e faixas típicas aprovadas.
Ele não implementa geração de registros, cálculo de score ou classificação.
"""

from typing import Final


PROPERTY_TYPE_DISTRIBUTION: Final[dict[str, float]] = {
    "CASA": 0.32,
    "APARTAMENTO": 0.32,
    "COMERCIO": 0.16,
    "ESCRITORIO": 0.10,
    "INDUSTRIA": 0.05,
    "OUTRO": 0.05,
}

SCENARIO_TYPES: Final[tuple[str, ...]] = (
    "TIPICO",
    "FRONTEIRA",
    "RARO_EXTREMO",
)

TYPICAL_RANGES: Final[
    dict[str, dict[str, tuple[float, float]]]
] = {
    "CASA": {
        "consumo_kwh": (180.0, 520.0),
        "quantidade_equipamentos": (4, 22),
        "horas_alto_consumo": (1, 12),
    },
    "APARTAMENTO": {
        "consumo_kwh": (140.0, 390.0),
        "quantidade_equipamentos": (3, 18),
        "horas_alto_consumo": (1, 11),
    },
    "COMERCIO": {
        "consumo_kwh": (240.0, 560.0),
        "quantidade_equipamentos": (5, 28),
        "horas_alto_consumo": (2, 14),
    },
    "ESCRITORIO": {
        "consumo_kwh": (180.0, 700.0),
        "quantidade_equipamentos": (5, 35),
        "horas_alto_consumo": (3, 14),
    },
    "INDUSTRIA": {
        "consumo_kwh": (300.0, 1_400.0),
        "quantidade_equipamentos": (8, 50),
        "horas_alto_consumo": (4, 20),
    },
    "OUTRO": {
        "consumo_kwh": (120.0, 800.0),
        "quantidade_equipamentos": (2, 30),
        "horas_alto_consumo": (1, 16),
    },
}

BOUNDARY_CASE_RATIO: Final[float] = 0.03
RARE_CASE_RATIO: Final[float] = 0.05
PLAUSIBLE_OUTLIER_RATIO: Final[float] = 0.03

RARE_CASE_FEATURES: Final[tuple[str, ...]] = (
    "consumo_kwh",
    "quantidade_equipamentos",
    "horas_alto_consumo",
)

RARE_CASE_DIRECTIONS: Final[tuple[str, ...]] = (
    "ABAIXO",
    "ACIMA",
)

RARE_CASE_GENERATION_PARAMETERS: Final[dict[str, float]] = {
    "consumption_minimum_step_ratio": 0.02,
    "maximum_typical_width_ratio": 0.15,
    "maximum_available_gap_ratio": 0.50,
}

RARE_CASE_RANDOM_SEED_OFFSET: Final[int] = 5

TARGET_CATEGORY_DISTRIBUTION: Final[dict[str, float]] = {
    "EFICIENTE": 0.30,
    "MODERADO": 0.40,
    "INEFICIENTE": 0.30,
}

REFERENCE_SCORE_CATEGORY_RANGES: Final[
    dict[str, tuple[int, int]]
] = {
    "EFICIENTE": (0, 30),
    "MODERADO": (31, 60),
    "INEFICIENTE": (61, 100),
}

REFERENCE_SCORE_PARAMETERS: Final[dict[str, float]] = {
    "consumption_weight": 0.30,
    "equipment_weight": 0.18,
    "hours_weight": 0.18,
    "peak_weight": 0.08,
    "consumption_hours_interaction_weight": 0.12,
    "equipment_hours_interaction_weight": 0.08,
    "consumption_quadratic_weight": 0.06,
    "score_intercept": -0.918129,
    "score_scale": 113.330515,
}

PEAK_USAGE_PROBABILITY_PARAMETERS: Final[dict[str, float]] = {
    "intercept": 0.15,
    "equipment_weight": 0.25,
    "hours_weight": 0.35,
    "interaction_weight": 0.20,
    "minimum_probability": 0.05,
    "maximum_probability": 0.95,
}

CONSUMPTION_GENERATION_PARAMETERS: Final[dict[str, float]] = {
    "equipment_weight": 0.30,
    "hours_weight": 0.30,
    "peak_weight": 0.15,
    "interaction_weight": 0.25,
    "noise_standard_deviation": 0.04,
    "minimum_normalized_consumption": 0.0,
    "maximum_normalized_consumption": 1.0,
}
