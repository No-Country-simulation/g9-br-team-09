"""Montagem das amostras sintéticas do Dataset EnergIAI V2.

Nesta etapa, o módulo reúne as cinco features observáveis já implementadas
em uma amostra típica reproduzível. Target, score, campos de auditoria e
casos especiais serão adicionados em etapas posteriores.
"""

import numpy as np
import pandas as pd

import generator
import scenarios
import schema


def _normalize_feature_by_property_type(
    sample: pd.DataFrame,
    feature: str,
) -> np.ndarray:
    """Normaliza uma feature conforme a faixa típica de cada imóvel."""
    normalized_values = np.empty(len(sample), dtype=float)

    for property_type in schema.PROPERTY_TYPES:
        mask = sample["tipo_imovel"].eq(property_type).to_numpy()

        if not np.any(mask):
            continue

        minimum, maximum = scenarios.TYPICAL_RANGES[
            property_type
        ][feature]

        if minimum >= maximum:
            raise ValueError(
                f"A faixa de {feature} deve possuir amplitude positiva"
            )

        normalized_values[mask] = np.clip(
            (
                sample.loc[mask, feature].to_numpy()
                - minimum
            )
            / (maximum - minimum),
            0.0,
            1.0,
        )

    return normalized_values


def calculate_reference_scores(
    sample: pd.DataFrame,
) -> np.ndarray:
    """Calcula o score sintético de referência entre 0 e 100."""
    missing_columns = [
        column
        for column in schema.FEATURE_COLUMNS
        if column not in sample.columns
    ]
    if missing_columns:
        raise ValueError(
            "Colunas obrigatórias ausentes: "
            + ", ".join(missing_columns)
        )

    normalized_consumption = _normalize_feature_by_property_type(
        sample,
        "consumo_kwh",
    )
    normalized_equipment = _normalize_feature_by_property_type(
        sample,
        "quantidade_equipamentos",
    )
    normalized_hours = _normalize_feature_by_property_type(
        sample,
        "horas_alto_consumo",
    )
    peak_usage = (
        sample["uso_horario_pico"]
        .astype(float)
        .to_numpy()
    )
    parameters = scenarios.REFERENCE_SCORE_PARAMETERS

    raw_score = (
        parameters["consumption_weight"]
        * normalized_consumption
        + parameters["equipment_weight"]
        * normalized_equipment
        + parameters["hours_weight"]
        * normalized_hours
        + parameters["peak_weight"]
        * peak_usage
        + parameters[
            "consumption_hours_interaction_weight"
        ]
        * normalized_consumption
        * normalized_hours
        + parameters[
            "equipment_hours_interaction_weight"
        ]
        * normalized_equipment
        * normalized_hours
        + parameters["consumption_quadratic_weight"]
        * np.square(normalized_consumption)
    )

    minimum_score, maximum_score = schema.NUMERIC_LIMITS[
        "score_referencia"
    ]
    scaled_score = (
        parameters["score_intercept"]
        + parameters["score_scale"] * raw_score
    )

    return np.clip(
        np.rint(scaled_score),
        minimum_score,
        maximum_score,
    ).astype(int)


def generate_typical_sample(
    sample_size: int,
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Gera uma amostra típica com as cinco features de produção."""
    if sample_size <= 0:
        raise ValueError("sample_size deve ser maior que zero")

    property_types = generator.generate_property_types(
        sample_size,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        seed,
    )
    equipment_counts = generator.generate_equipment_counts(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(seed + 1),
    )
    high_consumption_hours = (
        generator.generate_high_consumption_hours(
            property_types,
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(seed + 2),
        )
    )
    peak_usage = generator.generate_peak_usage(
        property_types,
        equipment_counts,
        high_consumption_hours,
        scenarios.TYPICAL_RANGES,
        scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
        np.random.default_rng(seed + 3),
    )
    consumption = generator.generate_consumption(
        property_types,
        equipment_counts,
        high_consumption_hours,
        peak_usage,
        scenarios.TYPICAL_RANGES,
        scenarios.CONSUMPTION_GENERATION_PARAMETERS,
        np.random.default_rng(seed + 4),
    )

    sample = pd.DataFrame(
        {
            "consumo_kwh": consumption,
            "uso_horario_pico": peak_usage,
            "quantidade_equipamentos": equipment_counts,
            "tipo_imovel": property_types,
            "horas_alto_consumo": high_consumption_hours,
        }
    )

    return sample.loc[:, list(schema.FEATURE_COLUMNS)]
