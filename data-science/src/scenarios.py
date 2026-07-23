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
