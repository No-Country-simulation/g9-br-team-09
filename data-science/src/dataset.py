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

_PLAUSIBLE_OUTLIER_IQR_MULTIPLIER = 1.5
_PLAUSIBLE_OUTLIER_RANDOM_SEED_OFFSET = 11


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


def select_rare_case_positions(
    sample: pd.DataFrame,
    ratio: float = scenarios.RARE_CASE_RATIO,
    seed: int = schema.RANDOM_SEED,
) -> np.ndarray:
    """Seleciona posições reproduzíveis para casos raros não fronteiriços."""
    if "caso_fronteira" not in sample.columns:
        raise ValueError("Coluna obrigatória ausente: caso_fronteira")

    if not pd.api.types.is_bool_dtype(sample["caso_fronteira"]):
        raise ValueError("caso_fronteira deve possuir tipo booleano")

    if sample["caso_fronteira"].isna().any():
        raise ValueError("caso_fronteira não pode conter valores nulos")

    if not np.isfinite(ratio) or not 0.0 <= ratio <= 1.0:
        raise ValueError("ratio deve estar entre 0 e 1")

    boundary_flags = sample["caso_fronteira"].to_numpy()
    candidate_positions = np.flatnonzero(~boundary_flags)
    quota = int(round(len(sample) * ratio))

    if quota > len(candidate_positions):
        raise ValueError(
            "Casos não fronteiriços insuficientes "
            "para a proporção de raros solicitada"
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

def build_rare_case_assignments(
    positions: np.ndarray,
    seed: int = schema.RANDOM_SEED,
) -> list[tuple[int, str, str]]:
    """Distribui posições raras entre features e direções reproduzíveis."""
    values = np.asarray(positions)

    if values.ndim != 1:
        raise ValueError("positions deve ser unidimensional")

    if not np.issubdtype(values.dtype, np.integer):
        raise ValueError("positions deve conter valores inteiros")

    if len(np.unique(values)) != len(values):
        raise ValueError("positions não pode conter valores duplicados")

    if values.size == 0:
        return []

    features = scenarios.RARE_CASE_FEATURES
    directions = scenarios.RARE_CASE_DIRECTIONS

    if not features:
        raise ValueError("RARE_CASE_FEATURES não pode estar vazio")

    if len(directions) != 2:
        raise ValueError(
            "RARE_CASE_DIRECTIONS deve possuir exatamente duas direções"
        )

    base_count, remainder = divmod(values.size, len(features))
    assignments: list[tuple[str, str]] = []

    for feature_index, feature in enumerate(features):
        feature_count = base_count + int(feature_index < remainder)
        below_count = (feature_count + 1) // 2
        above_count = feature_count - below_count

        assignments.extend(
            (feature, directions[0])
            for _ in range(below_count)
        )
        assignments.extend(
            (feature, directions[1])
            for _ in range(above_count)
        )

    rng = np.random.default_rng(
        seed + scenarios.RARE_CASE_RANDOM_SEED_OFFSET
    )
    rng.shuffle(assignments)

    return [
        (int(position), feature, direction)
        for position, (feature, direction) in zip(
            values,
            assignments,
            strict=True,
        )
    ]

def _generate_rare_feature_value(
    property_type: str,
    feature: str,
    direction: str,
    random_generator: np.random.Generator,
) -> float | int:
    """Gera um valor raro válido para uma feature numérica."""
    if property_type not in schema.PROPERTY_TYPES:
        raise ValueError(f"Tipo de imóvel inválido: {property_type}")

    if feature not in scenarios.RARE_CASE_FEATURES:
        raise ValueError(f"Feature rara inválida: {feature}")

    if direction not in scenarios.RARE_CASE_DIRECTIONS:
        raise ValueError(f"Direção rara inválida: {direction}")

    typical_minimum, typical_maximum = (
        scenarios.TYPICAL_RANGES[property_type][feature]
    )
    absolute_minimum, absolute_maximum = schema.NUMERIC_LIMITS[feature]
    typical_width = typical_maximum - typical_minimum

    if typical_width <= 0:
        raise ValueError(
            f"A faixa típica de {feature} deve possuir amplitude positiva"
        )

    below_direction, above_direction = scenarios.RARE_CASE_DIRECTIONS

    if direction == below_direction:
        available_gap = typical_minimum - absolute_minimum
    else:
        available_gap = absolute_maximum - typical_maximum

    if available_gap <= 0:
        raise ValueError(
            f"Não há espaço válido para gerar {feature} na direção {direction}"
        )

    parameters = scenarios.RARE_CASE_GENERATION_PARAMETERS
    maximum_step = min(
        typical_width * parameters["maximum_typical_width_ratio"],
        available_gap * parameters["maximum_available_gap_ratio"],
    )

    if maximum_step <= 0:
        raise ValueError(
            f"O deslocamento máximo de {feature} deve ser positivo"
        )

    if feature == "consumo_kwh":
        minimum_step = min(
            maximum_step,
            max(
                1.0,
                typical_width
                * parameters["consumption_minimum_step_ratio"],
            ),
        )
        step = (
            random_generator.uniform(minimum_step, maximum_step)
            if maximum_step > minimum_step
            else maximum_step
        )
        value = (
            typical_minimum - step
            if direction == below_direction
            else typical_maximum + step
        )

        return round(
            float(np.clip(value, absolute_minimum, absolute_maximum)),
            2,
        )

    maximum_integer_step = max(
        1,
        min(
            int(np.ceil(maximum_step)),
            max(1, int(available_gap) // 2),
        ),
    )
    step = int(
        random_generator.integers(
            1,
            maximum_integer_step + 1,
        )
    )
    value = (
        int(typical_minimum) - step
        if direction == below_direction
        else int(typical_maximum) + step
    )

    return int(
        np.clip(
            value,
            int(absolute_minimum),
            int(absolute_maximum),
        )
    )

def apply_rare_case_feature_mutations(
    sample: pd.DataFrame,
    assignments: list[tuple[int, str, str]],
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Aplica mutações raras reproduzíveis em uma cópia da amostra."""
    required_columns = {
        "tipo_imovel",
        *scenarios.RARE_CASE_FEATURES,
    }
    missing_columns = sorted(required_columns.difference(sample.columns))

    if missing_columns:
        raise ValueError(
            "Colunas obrigatórias ausentes: "
            + ", ".join(missing_columns)
        )

    if not sample.columns.is_unique:
        raise ValueError("A amostra não pode possuir colunas duplicadas")

    mutated_sample = sample.copy(deep=True)
    random_generator = np.random.default_rng(
        seed + scenarios.RARE_CASE_RANDOM_SEED_OFFSET
    )
    used_positions: set[int] = set()

    for position, feature, direction in assignments:
        if (
            isinstance(position, (bool, np.bool_))
            or not isinstance(position, (int, np.integer))
        ):
            raise ValueError(
                "As posições das atribuições devem ser inteiras"
            )

        normalized_position = int(position)

        if not 0 <= normalized_position < len(mutated_sample):
            raise ValueError(
                f"Posição rara fora da amostra: {normalized_position}"
            )

        if normalized_position in used_positions:
            raise ValueError(
                f"Posição rara duplicada: {normalized_position}"
            )

        property_type = str(
            mutated_sample.iloc[normalized_position]["tipo_imovel"]
        )
        rare_value = _generate_rare_feature_value(
            property_type,
            feature,
            direction,
            random_generator,
        )
        feature_position = mutated_sample.columns.get_loc(feature)

        mutated_sample.iat[
            normalized_position,
            feature_position,
        ] = rare_value

        used_positions.add(normalized_position)

    return mutated_sample

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


def _value_beyond_iqr_fence(
    feature: str,
    direction: str,
    lower_fence: float,
    upper_fence: float,
) -> float | int:
    """Retorna o menor valor representável além da cerca do IQR."""
    if feature == "consumo_kwh":
        if direction == "ABAIXO":
            return round(
                np.floor(lower_fence * 100.0) / 100.0 - 0.01,
                2,
            )

        return round(
            np.ceil(upper_fence * 100.0) / 100.0 + 0.01,
            2,
        )

    if direction == "ABAIXO":
        return int(np.floor(lower_fence)) - 1

    return int(np.ceil(upper_fence)) + 1


def apply_plausible_outlier_mutations(
    sample: pd.DataFrame,
    assignments: list[tuple[int, str, str]],
    ratio: float = scenarios.PLAUSIBLE_OUTLIER_RATIO,
    seed: int = schema.RANDOM_SEED,
) -> tuple[pd.DataFrame, np.ndarray]:
    """Seleciona e aplica outliers IQR como subconjunto dos casos raros."""
    required_columns = {
        "tipo_imovel",
        "caso_fronteira",
        *scenarios.RARE_CASE_FEATURES,
    }
    missing_columns = sorted(
        required_columns.difference(sample.columns)
    )

    if missing_columns:
        raise ValueError(
            "Colunas obrigatórias ausentes: "
            + ", ".join(missing_columns)
        )

    if not np.isfinite(ratio) or not 0.0 <= ratio <= 1.0:
        raise ValueError("ratio deve estar entre 0 e 1")

    quota = int(round(len(sample) * ratio))

    if quota == 0:
        return sample.copy(deep=True), np.empty(0, dtype=int)

    if quota > len(assignments):
        raise ValueError(
            "Casos raros insuficientes para a proporção de outliers"
        )

    rare_positions = np.array(
        [
            position
            for position, _, _ in assignments
        ],
        dtype=int,
    )

    reference_mask = (
        ~sample["caso_fronteira"].to_numpy()
    )
    reference_mask[rare_positions] = False

    candidates: list[
        tuple[int, str, float | int, bool]
    ] = []

    for position, feature, direction in assignments:
        row = sample.iloc[position]
        property_type = str(row["tipo_imovel"])

        property_mask = (
            sample["tipo_imovel"]
            .eq(property_type)
            .to_numpy()
        )

        reference_values = sample.loc[
            reference_mask & property_mask,
            feature,
        ].astype(float)

        if reference_values.empty:
            raise ValueError(
                "Amostra de referência insuficiente para "
                f"{property_type} e {feature}"
            )

        q1 = float(reference_values.quantile(0.25))
        q3 = float(reference_values.quantile(0.75))
        iqr = q3 - q1

        lower_fence = (
            q1
            - _PLAUSIBLE_OUTLIER_IQR_MULTIPLIER
            * iqr
        )
        upper_fence = (
            q3
            + _PLAUSIBLE_OUTLIER_IQR_MULTIPLIER
            * iqr
        )

        candidate_value = _value_beyond_iqr_fence(
            feature,
            direction,
            lower_fence,
            upper_fence,
        )

        absolute_minimum, absolute_maximum = (
            schema.NUMERIC_LIMITS[feature]
        )

        eligible = (
            absolute_minimum
            <= candidate_value
            <= absolute_maximum
            and (
                candidate_value < lower_fence
                or candidate_value > upper_fence
            )
        )

        if not eligible:
            continue

        current_value = float(row[feature])
        currently_detected = (
            current_value < lower_fence
            or current_value > upper_fence
        )

        candidates.append(
            (
                int(position),
                feature,
                candidate_value,
                currently_detected,
            )
        )

    mandatory_positions = {
        position
        for (
            position,
            _,
            _,
            currently_detected,
        ) in candidates
        if currently_detected
    }

    if len(mandatory_positions) > quota:
        raise ValueError(
            "Casos já detectados excedem a cota de outliers"
        )

    eligible_positions = {
        position
        for position, _, _, _ in candidates
    }

    available_positions = np.array(
        sorted(
            eligible_positions
            - mandatory_positions
        ),
        dtype=int,
    )

    remaining_quota = (
        quota - len(mandatory_positions)
    )

    if remaining_quota > len(available_positions):
        raise ValueError(
            "Candidatos detectáveis insuficientes "
            "para a cota de outliers"
        )

    random_generator = np.random.default_rng(
        seed
        + _PLAUSIBLE_OUTLIER_RANDOM_SEED_OFFSET
    )

    additional_positions = set(
        random_generator.choice(
            available_positions,
            size=remaining_quota,
            replace=False,
        )
        .astype(int)
        .tolist()
    )

    selected_positions = (
        mandatory_positions
        | additional_positions
    )

    mutated_sample = sample.copy(deep=True)

    for position, feature, candidate_value, _ in candidates:
        if position not in selected_positions:
            continue

        feature_position = (
            mutated_sample.columns.get_loc(feature)
        )

        mutated_sample.iat[
            position,
            feature_position,
        ] = candidate_value

    return (
        mutated_sample,
        np.array(
            sorted(selected_positions),
            dtype=int,
        ),
    )


def generate_audited_sample_with_rare_cases(
    sample_size: int,
    seed: int = schema.RANDOM_SEED,
) -> pd.DataFrame:
    """Integra casos raros e outliers plausíveis à amostra auditada."""
    audited_sample = generate_audited_typical_sample(
        sample_size,
        seed=seed,
    )
    rare_positions = select_rare_case_positions(
        audited_sample,
        seed=seed,
    )
    assignments = build_rare_case_assignments(
        rare_positions,
        seed=seed,
    )
    rare_sample = apply_rare_case_feature_mutations(
        audited_sample,
        assignments,
        seed=seed,
    )
    rare_sample, outlier_positions = (
        apply_plausible_outlier_mutations(
            rare_sample,
            assignments,
            seed=seed,
        )
    )

    recalculated_scores = calculate_reference_scores(
        rare_sample
    )
    recalculated_categories = (
        categorize_reference_scores(
            recalculated_scores
        )
    )

    rare_index = rare_sample.index[rare_positions]
    outlier_index = rare_sample.index[
        outlier_positions
    ]

    rare_sample[schema.TARGET_COLUMN] = (
        recalculated_categories
    )
    rare_sample["score_referencia"] = (
        recalculated_scores
    )

    rare_sample.loc[
        rare_index,
        "tipo_cenario",
    ] = "RARO_EXTREMO"
    rare_sample.loc[
        rare_index,
        "caso_raro",
    ] = True
    rare_sample.loc[
        outlier_index,
        "outlier_plausivel",
    ] = True

    return rare_sample.loc[
        :,
        list(schema.DATASET_COLUMNS),
    ]
