"""Montagem das amostras sintéticas do Dataset EnergIAI V2.

O módulo reúne as cinco features observáveis em uma amostra típica
reproduzível e permite acrescentar score de referência e categoria.
Campos de auditoria e casos especiais serão adicionados em etapas posteriores.
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


def categorize_reference_scores(
    scores: np.ndarray,
) -> np.ndarray:
    """Converte scores inteiros em categorias energéticas."""
    values = np.asarray(scores)

    if values.ndim != 1:
        raise ValueError("scores deve ser unidimensional")

    if not np.issubdtype(values.dtype, np.number):
        raise ValueError("scores deve conter valores numéricos")

    if not np.isfinite(values).all():
        raise ValueError("scores deve conter valores finitos")

    if not np.equal(values, np.rint(values)).all():
        raise ValueError("scores devem conter valores inteiros")

    minimum_score, maximum_score = schema.NUMERIC_LIMITS[
        "score_referencia"
    ]

    if (
        (values < minimum_score)
        | (values > maximum_score)
    ).any():
        raise ValueError("scores devem estar entre 0 e 100")

    categories = np.empty(values.shape, dtype=object)

    for category in schema.ENERGY_CATEGORIES:
        minimum, maximum = (
            scenarios.REFERENCE_SCORE_CATEGORY_RANGES[
                category
            ]
        )
        mask = (values >= minimum) & (values <= maximum)
        categories[mask] = category

    return categories.astype(str)


def select_boundary_case_positions(
    sample: pd.DataFrame,
    ratio: float = scenarios.BOUNDARY_CASE_RATIO,
    seed: int = schema.RANDOM_SEED,
) -> np.ndarray:
    """Seleciona posições reproduzíveis próximas às fronteiras do target."""
    if "score_referencia" not in sample.columns:
        raise ValueError("Coluna obrigatória ausente: score_referencia")

    if not np.isfinite(ratio) or not 0.0 <= ratio <= 1.0:
        raise ValueError("ratio deve estar entre 0 e 1")

    scores = sample["score_referencia"].to_numpy()
    categorize_reference_scores(scores)

    category_pairs = zip(
        schema.ENERGY_CATEGORIES[:-1],
        schema.ENERGY_CATEGORIES[1:],
        strict=True,
    )
    boundary_scores = np.array(
        [
            boundary
            for lower_category, upper_category in category_pairs
            for boundary in (
                scenarios.REFERENCE_SCORE_CATEGORY_RANGES[
                    lower_category
                ][1],
                scenarios.REFERENCE_SCORE_CATEGORY_RANGES[
                    upper_category
                ][0],
            )
        ],
        dtype=int,
    )
    candidate_positions = np.flatnonzero(
        np.isin(scores, boundary_scores)
    )
    quota = int(round(len(sample) * ratio))

    if quota > len(candidate_positions):
        raise ValueError(
            "Casos de fronteira insuficientes para a proporção solicitada"
        )

    if quota == 0:
        return np.empty(0, dtype=int)

    rng = np.random.default_rng(seed)

    return np.sort(
        rng.choice(
            candidate_positions,
            size=quota,
            replace=False,
        )
    )


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


def generate_labeled_typical_sample(
    sample_size: int,
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Gera amostra típica com features, categoria e score de referência."""
    sample = generate_typical_sample(sample_size, seed)
    scores = calculate_reference_scores(sample)

    labeled_sample = sample.copy()
    labeled_sample[schema.TARGET_COLUMN] = (
        categorize_reference_scores(scores)
    )
    labeled_sample["score_referencia"] = scores

    columns = (
        *schema.FEATURE_COLUMNS,
        schema.TARGET_COLUMN,
        "score_referencia",
    )

    return labeled_sample.loc[:, list(columns)]


def generate_audited_typical_sample(
    sample_size: int,
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Integra os campos iniciais de auditoria à amostra rotulada."""
    audited_sample = generate_labeled_typical_sample(sample_size, seed)

    boundary_positions = select_boundary_case_positions(
        audited_sample,
        seed=seed,
    )
    boundary_index = audited_sample.index[boundary_positions]

    audited_sample["tipo_cenario"] = "TIPICO"
    audited_sample["caso_fronteira"] = False
    audited_sample["caso_raro"] = False
    audited_sample["outlier_plausivel"] = False
    audited_sample["lote_geracao"] = (
        f"energiai-v2-seed-{seed}-size-{sample_size}"
    )

    audited_sample.loc[boundary_index, "tipo_cenario"] = "FRONTEIRA"
    audited_sample.loc[boundary_index, "caso_fronteira"] = True

    return audited_sample.loc[:, list(schema.DATASET_COLUMNS)]
