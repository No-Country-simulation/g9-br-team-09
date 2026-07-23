"""Contrato programático do Dataset EnergIAI V2.

Este módulo centraliza nomes de colunas, domínios e limites definidos na
Especificação V2. Ele não implementa geração de dados, regras de target,
validações ou treinamento de modelos.
"""

from typing import Final


RANDOM_SEED: Final[int] = 42
DATASET_SIZE: Final[int] = 5_000

FEATURE_COLUMNS: Final[tuple[str, ...]] = (
    "consumo_kwh",
    "uso_horario_pico",
    "quantidade_equipamentos",
    "tipo_imovel",
    "horas_alto_consumo",
)

TARGET_COLUMN: Final[str] = "categoria"

AUDIT_COLUMNS: Final[tuple[str, ...]] = (
    "score_referencia",
    "tipo_cenario",
    "caso_fronteira",
    "caso_raro",
    "outlier_plausivel",
    "lote_geracao",
)

DATASET_COLUMNS: Final[tuple[str, ...]] = (
    *FEATURE_COLUMNS,
    TARGET_COLUMN,
    *AUDIT_COLUMNS,
)

PROPERTY_TYPES: Final[tuple[str, ...]] = (
    "CASA",
    "APARTAMENTO",
    "COMERCIO",
    "ESCRITORIO",
    "INDUSTRIA",
    "OUTRO",
)

ENERGY_CATEGORIES: Final[tuple[str, ...]] = (
    "EFICIENTE",
    "MODERADO",
    "INEFICIENTE",
)

NUMERIC_LIMITS: Final[dict[str, tuple[float, float]]] = {
    "consumo_kwh": (60.0, 2_500.0),
    "quantidade_equipamentos": (1, 60),
    "horas_alto_consumo": (0, 24),
    "score_referencia": (0, 100),
}

PROHIBITED_MODEL_FEATURES: Final[tuple[str, ...]] = (
    TARGET_COLUMN,
    "score_referencia",
    "tipo_cenario",
    "caso_fronteira",
    "caso_raro",
    "outlier_plausivel",
    "lote_geracao",
    "recomendacoes",
    "probabilidade",
    "custo_estimado_mensal",
)
