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
