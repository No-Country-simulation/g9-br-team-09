"""Testes da montagem das amostras sintéticas do Dataset EnergIAI V2."""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import dataset  # noqa: E402
import scenarios  # noqa: E402
import schema  # noqa: E402


def test_amostra_tipica_respeita_estrutura_do_contrato() -> None:
    sample = dataset.generate_typical_sample(100)

    assert sample.shape == (100, 5)
    assert tuple(sample.columns) == schema.FEATURE_COLUMNS
    assert int(sample.isna().sum().sum()) == 0
    assert set(sample["tipo_imovel"].unique()) == set(
        schema.PROPERTY_TYPES
    )
    assert np.issubdtype(
        sample["consumo_kwh"].dtype,
        np.floating,
    )
    assert np.issubdtype(
        sample["quantidade_equipamentos"].dtype,
        np.integer,
    )
    assert np.issubdtype(
        sample["horas_alto_consumo"].dtype,
        np.integer,
    )
    assert sample["uso_horario_pico"].dtype == bool


def test_amostra_tipica_e_reprodutivel_com_a_mesma_seed() -> None:
    first_sample = dataset.generate_typical_sample(
        100,
        seed=schema.RANDOM_SEED,
    )
    second_sample = dataset.generate_typical_sample(
        100,
        seed=schema.RANDOM_SEED,
    )

    assert first_sample.equals(second_sample)


def test_amostra_tipica_rejeita_tamanho_nao_positivo() -> None:
    for sample_size in (0, -1):
        with pytest.raises(
            ValueError,
            match="sample_size deve ser maior que zero",
        ):
            dataset.generate_typical_sample(sample_size)


def test_amostra_de_validacao_respeita_distribuicao_e_unicidade() -> None:
    sample = dataset.generate_typical_sample(
        200,
        seed=schema.RANDOM_SEED,
    )

    counts = sample["tipo_imovel"].value_counts().to_dict()

    assert counts == {
        "CASA": 64,
        "APARTAMENTO": 64,
        "COMERCIO": 32,
        "ESCRITORIO": 20,
        "INDUSTRIA": 10,
        "OUTRO": 10,
    }
    assert int(sample.duplicated().sum()) == 0


def test_amostra_de_validacao_respeita_faixas_tipicas() -> None:
    sample = dataset.generate_typical_sample(
        200,
        seed=schema.RANDOM_SEED,
    )

    for property_type in schema.PROPERTY_TYPES:
        group = sample[
            sample["tipo_imovel"].eq(property_type)
        ]

        for column, limits in scenarios.TYPICAL_RANGES[
            property_type
        ].items():
            assert group[column].between(*limits).all()


@pytest.mark.parametrize("property_type", schema.PROPERTY_TYPES)
def test_score_referencia_respeita_extremos_das_faixas_tipicas(
    property_type: str,
) -> None:
    ranges = scenarios.TYPICAL_RANGES[property_type]
    sample = pd.DataFrame(
        [
            {
                "consumo_kwh": ranges["consumo_kwh"][0],
                "uso_horario_pico": False,
                "quantidade_equipamentos": ranges[
                    "quantidade_equipamentos"
                ][0],
                "tipo_imovel": property_type,
                "horas_alto_consumo": ranges[
                    "horas_alto_consumo"
                ][0],
            },
            {
                "consumo_kwh": ranges["consumo_kwh"][1],
                "uso_horario_pico": True,
                "quantidade_equipamentos": ranges[
                    "quantidade_equipamentos"
                ][1],
                "tipo_imovel": property_type,
                "horas_alto_consumo": ranges[
                    "horas_alto_consumo"
                ][1],
            },
        ],
        columns=schema.FEATURE_COLUMNS,
    )

    scores = dataset.calculate_reference_scores(sample)

    assert scores.tolist() == [0, 100]


def test_score_referencia_possui_tipo_limites_e_reprodutibilidade() -> None:
    sample = dataset.generate_typical_sample(
        200,
        seed=schema.RANDOM_SEED,
    )

    first_scores = dataset.calculate_reference_scores(sample)
    second_scores = dataset.calculate_reference_scores(sample)

    assert first_scores.shape == (200,)
    assert np.issubdtype(first_scores.dtype, np.integer)
    assert int(first_scores.min()) >= 0
    assert int(first_scores.max()) <= 100
    assert np.array_equal(first_scores, second_scores)


def test_score_referencia_rejeita_colunas_obrigatorias_ausentes() -> None:
    sample = dataset.generate_typical_sample(3).drop(
        columns=["consumo_kwh", "tipo_imovel"],
    )

    with pytest.raises(
        ValueError,
        match=(
            "Colunas obrigatórias ausentes: "
            "consumo_kwh, tipo_imovel"
        ),
    ):
        dataset.calculate_reference_scores(sample)


def test_categorias_respeitam_limites_do_score_de_referencia() -> None:
    scores = np.array([0, 30, 31, 60, 61, 100])

    categories = dataset.categorize_reference_scores(scores)

    assert categories.tolist() == [
        "EFICIENTE",
        "EFICIENTE",
        "MODERADO",
        "MODERADO",
        "INEFICIENTE",
        "INEFICIENTE",
    ]
    assert set(categories) == set(schema.ENERGY_CATEGORIES)


@pytest.mark.parametrize(
    ("scores", "error_message"),
    [
        (
            np.array([[0, 30], [31, 60]]),
            "scores deve ser unidimensional",
        ),
        (
            np.array(["0", "30"]),
            "scores deve conter valores numéricos",
        ),
        (
            np.array([0.0, np.nan]),
            "scores deve conter valores finitos",
        ),
        (
            np.array([30.5, 31.0]),
            "scores devem conter valores inteiros",
        ),
        (
            np.array([-1, 101]),
            "scores devem estar entre 0 e 100",
        ),
    ],
)
def test_categorizacao_rejeita_scores_invalidos(
    scores: np.ndarray,
    error_message: str,
) -> None:
    with pytest.raises(ValueError, match=error_message):
        dataset.categorize_reference_scores(scores)


def test_amostra_tipica_rotulada_respeita_estrutura_e_coerencia() -> None:
    sample = dataset.generate_labeled_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    expected_columns = (
        *schema.FEATURE_COLUMNS,
        schema.TARGET_COLUMN,
        "score_referencia",
    )
    scores = sample["score_referencia"].to_numpy()
    expected_categories = dataset.categorize_reference_scores(scores)
    minimum_score, maximum_score = schema.NUMERIC_LIMITS[
        "score_referencia"
    ]

    assert sample.shape == (schema.DATASET_SIZE, 7)
    assert tuple(sample.columns) == expected_columns
    assert int(sample.isna().sum().sum()) == 0
    assert np.issubdtype(scores.dtype, np.integer)
    assert int(scores.min()) >= minimum_score
    assert int(scores.max()) <= maximum_score
    assert np.array_equal(
        sample[schema.TARGET_COLUMN].to_numpy(),
        expected_categories,
    )


def test_amostra_tipica_rotulada_respeita_distribuicao_alvo() -> None:
    sample = dataset.generate_labeled_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    observed = (
        sample[schema.TARGET_COLUMN]
        .value_counts(normalize=True)
        .reindex(schema.ENERGY_CATEGORIES)
    )

    for category, expected_proportion in (
        scenarios.TARGET_CATEGORY_DISTRIBUTION.items()
    ):
        assert abs(
            observed[category] - expected_proportion
        ) <= 0.02


def test_amostra_tipica_rotulada_e_reprodutivel() -> None:
    first_sample = dataset.generate_labeled_typical_sample(
        200,
        seed=schema.RANDOM_SEED,
    )
    second_sample = dataset.generate_labeled_typical_sample(
        200,
        seed=schema.RANDOM_SEED,
    )

    assert first_sample.equals(second_sample)

def test_selecao_fronteiras_respeita_quota_e_reprodutibilidade() -> None:
    sample = dataset.generate_labeled_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    first_positions = dataset.select_boundary_case_positions(sample)
    second_positions = dataset.select_boundary_case_positions(sample)
    selected = sample.iloc[first_positions]
    ranges = scenarios.REFERENCE_SCORE_CATEGORY_RANGES
    expected_boundary_scores = {
        ranges["EFICIENTE"][1],
        ranges["MODERADO"][0],
        ranges["MODERADO"][1],
        ranges["INEFICIENTE"][0],
    }
    expected_quota = int(
        round(len(sample) * scenarios.BOUNDARY_CASE_RATIO)
    )

    assert first_positions.shape == (expected_quota,)
    assert np.issubdtype(first_positions.dtype, np.integer)
    assert np.array_equal(first_positions, second_positions)
    assert len(np.unique(first_positions)) == expected_quota
    assert np.all(np.diff(first_positions) > 0)
    assert set(selected["score_referencia"].unique()) == (
        expected_boundary_scores
    )


def test_selecao_fronteiras_aceita_quota_zero() -> None:
    sample = dataset.generate_labeled_typical_sample(
        20,
        seed=schema.RANDOM_SEED,
    )

    positions = dataset.select_boundary_case_positions(
        sample,
        ratio=0.0,
    )

    assert positions.shape == (0,)
    assert np.issubdtype(positions.dtype, np.integer)


@pytest.mark.parametrize(
    "ratio",
    [-0.01, 1.01, np.nan, np.inf],
)
def test_selecao_fronteiras_rejeita_proporcao_invalida(
    ratio: float,
) -> None:
    sample = dataset.generate_labeled_typical_sample(
        20,
        seed=schema.RANDOM_SEED,
    )

    with pytest.raises(
        ValueError,
        match="ratio deve estar entre 0 e 1",
    ):
        dataset.select_boundary_case_positions(sample, ratio=ratio)


def test_selecao_fronteiras_rejeita_score_ausente() -> None:
    sample = dataset.generate_typical_sample(20)

    with pytest.raises(
        ValueError,
        match="Coluna obrigatória ausente: score_referencia",
    ):
        dataset.select_boundary_case_positions(sample)


def test_selecao_fronteiras_rejeita_candidatos_insuficientes() -> None:
    sample = pd.DataFrame(
        {"score_referencia": [30, 40, 50, 70]}
    )

    with pytest.raises(
        ValueError,
        match=(
            "Casos de fronteira insuficientes "
            "para a proporção solicitada"
        ),
    ):
        dataset.select_boundary_case_positions(sample, ratio=1.0)


def test_amostra_tipica_auditada_respeita_schema_e_invariantes() -> None:
    original = dataset.generate_labeled_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    audited = dataset.generate_audited_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    preserved_columns = (
        *schema.FEATURE_COLUMNS,
        schema.TARGET_COLUMN,
        "score_referencia",
    )

    assert len(audited) == schema.DATASET_SIZE
    assert tuple(audited.columns) == schema.DATASET_COLUMNS
    assert audited.loc[:, list(preserved_columns)].equals(original)
    assert not audited.isna().any().any()
    assert audited["caso_fronteira"].dtype == bool
    assert audited["caso_raro"].dtype == bool
    assert audited["outlier_plausivel"].dtype == bool


def test_amostra_tipica_auditada_marca_fronteiras_e_lote() -> None:
    audited = dataset.generate_audited_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    expected_boundary_count = int(
        round(schema.DATASET_SIZE * scenarios.BOUNDARY_CASE_RATIO)
    )
    boundary_mask = audited["caso_fronteira"]
    expected_lot = (
        f"energiai-v2-seed-{schema.RANDOM_SEED}"
        f"-size-{schema.DATASET_SIZE}"
    )

    assert int(boundary_mask.sum()) == expected_boundary_count
    assert int((audited["tipo_cenario"] == "FRONTEIRA").sum()) == (
        expected_boundary_count
    )
    assert audited.loc[
        boundary_mask,
        "tipo_cenario",
    ].eq("FRONTEIRA").all()
    assert audited.loc[
        ~boundary_mask,
        "tipo_cenario",
    ].eq("TIPICO").all()
    assert not audited["caso_raro"].any()
    assert not audited["outlier_plausivel"].any()
    assert audited["lote_geracao"].nunique() == 1
    assert audited["lote_geracao"].iat[0] == expected_lot


def test_amostra_tipica_auditada_e_reprodutivel() -> None:
    first_sample = dataset.generate_audited_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    second_sample = dataset.generate_audited_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    assert first_sample.equals(second_sample)


def test_selecao_raros_respeita_quota_e_exclui_fronteiras() -> None:
    sample = dataset.generate_audited_typical_sample(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    original = sample.copy(deep=True)

    first_positions = dataset.select_rare_case_positions(
        sample,
        seed=schema.RANDOM_SEED,
    )
    second_positions = dataset.select_rare_case_positions(
        sample,
        seed=schema.RANDOM_SEED,
    )

    expected_quota = int(
        round(len(sample) * scenarios.RARE_CASE_RATIO)
    )
    boundary_positions = np.flatnonzero(
        sample["caso_fronteira"].to_numpy()
    )

    assert first_positions.shape == (expected_quota,)
    assert np.issubdtype(first_positions.dtype, np.integer)
    assert np.array_equal(first_positions, second_positions)
    assert len(np.unique(first_positions)) == expected_quota
    assert np.all(np.diff(first_positions) > 0)
    assert np.intersect1d(
        first_positions,
        boundary_positions,
    ).size == 0
    assert not sample.iloc[first_positions]["caso_fronteira"].any()
    assert sample.equals(original)


def test_selecao_raros_aceita_quota_zero() -> None:
    sample = dataset.generate_audited_typical_sample(
        20,
        seed=schema.RANDOM_SEED,
    )

    positions = dataset.select_rare_case_positions(
        sample,
        ratio=0.0,
    )

    assert positions.shape == (0,)
    assert np.issubdtype(positions.dtype, np.integer)


@pytest.mark.parametrize(
    "ratio",
    [-0.01, 1.01, np.nan, np.inf],
)
def test_selecao_raros_rejeita_proporcao_invalida(
    ratio: float,
) -> None:
    sample = dataset.generate_audited_typical_sample(
        20,
        seed=schema.RANDOM_SEED,
    )

    with pytest.raises(
        ValueError,
        match="ratio deve estar entre 0 e 1",
    ):
        dataset.select_rare_case_positions(sample, ratio=ratio)


def test_selecao_raros_rejeita_flag_ausente() -> None:
    sample = dataset.generate_labeled_typical_sample(
        20,
        seed=schema.RANDOM_SEED,
    )

    with pytest.raises(
        ValueError,
        match="Coluna obrigatória ausente: caso_fronteira",
    ):
        dataset.select_rare_case_positions(sample)


def test_selecao_raros_rejeita_flag_nao_booleana() -> None:
    sample = pd.DataFrame(
        {"caso_fronteira": [0, 1, 0, 1]}
    )

    with pytest.raises(
        ValueError,
        match="caso_fronteira deve possuir tipo booleano",
    ):
        dataset.select_rare_case_positions(sample)


def test_selecao_raros_rejeita_flag_nula() -> None:
    sample = pd.DataFrame(
        {
            "caso_fronteira": pd.Series(
                [True, False, pd.NA],
                dtype="boolean",
            )
        }
    )

    with pytest.raises(
        ValueError,
        match="caso_fronteira não pode conter valores nulos",
    ):
        dataset.select_rare_case_positions(sample)


def test_selecao_raros_rejeita_candidatos_insuficientes() -> None:
    sample = pd.DataFrame(
        {"caso_fronteira": [True, True, True, False]}
    )

    with pytest.raises(
        ValueError,
        match=(
            "Casos não fronteiriços insuficientes "
            "para a proporção de raros solicitada"
        ),
    ):
        dataset.select_rare_case_positions(
            sample,
            ratio=1.0,
        )

def test_atribuicoes_raros_sao_balanceadas_e_reprodutiveis() -> None:
    positions = np.arange(250, dtype=int)
    original = positions.copy()

    first_assignments = dataset.build_rare_case_assignments(
        positions,
        seed=schema.RANDOM_SEED,
    )
    second_assignments = dataset.build_rare_case_assignments(
        positions,
        seed=schema.RANDOM_SEED,
    )

    returned_positions = [
        position
        for position, _, _ in first_assignments
    ]
    features = [
        feature
        for _, feature, _ in first_assignments
    ]
    directions = [
        direction
        for _, _, direction in first_assignments
    ]

    feature_counts = {
        feature: features.count(feature)
        for feature in scenarios.RARE_CASE_FEATURES
    }
    direction_counts = {
        direction: directions.count(direction)
        for direction in scenarios.RARE_CASE_DIRECTIONS
    }

    assert first_assignments == second_assignments
    assert returned_positions == positions.tolist()
    assert np.array_equal(positions, original)
    assert feature_counts == {
        "consumo_kwh": 84,
        "quantidade_equipamentos": 83,
        "horas_alto_consumo": 83,
    }
    assert direction_counts == {
        "ABAIXO": 126,
        "ACIMA": 124,
    }


def test_atribuicoes_raros_aceitam_entrada_vazia() -> None:
    assignments = dataset.build_rare_case_assignments(
        np.array([], dtype=int)
    )

    assert assignments == []


def test_atribuicoes_raros_rejeitam_matriz() -> None:
    positions = np.array([[1, 2], [3, 4]], dtype=int)

    with pytest.raises(
        ValueError,
        match="positions deve ser unidimensional",
    ):
        dataset.build_rare_case_assignments(positions)


def test_atribuicoes_raros_rejeitam_valores_nao_inteiros() -> None:
    positions = np.array([1.0, 2.0, 3.0])

    with pytest.raises(
        ValueError,
        match="positions deve conter valores inteiros",
    ):
        dataset.build_rare_case_assignments(positions)


def test_atribuicoes_raros_rejeitam_posicoes_duplicadas() -> None:
    positions = np.array([1, 2, 2, 3], dtype=int)

    with pytest.raises(
        ValueError,
        match="positions não pode conter valores duplicados",
    ):
        dataset.build_rare_case_assignments(positions)


def test_atribuicoes_raros_rejeitam_features_vazias(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(scenarios, "RARE_CASE_FEATURES", ())

    with pytest.raises(
        ValueError,
        match="RARE_CASE_FEATURES não pode estar vazio",
    ):
        dataset.build_rare_case_assignments(
            np.array([1, 2], dtype=int)
        )


def test_atribuicoes_raros_rejeitam_direcoes_invalidas(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        scenarios,
        "RARE_CASE_DIRECTIONS",
        ("ABAIXO",),
    )

    with pytest.raises(
        ValueError,
        match=(
            "RARE_CASE_DIRECTIONS deve possuir "
            "exatamente duas direções"
        ),
    ):
        dataset.build_rare_case_assignments(
            np.array([1, 2], dtype=int)
        )

@pytest.mark.parametrize(
    ("property_type", "feature", "direction"),
    [
        (property_type, feature, direction)
        for property_type in schema.PROPERTY_TYPES
        for feature in scenarios.RARE_CASE_FEATURES
        for direction in scenarios.RARE_CASE_DIRECTIONS
    ],
)
def test_valor_raro_respeita_faixas_e_limites(
    property_type: str,
    feature: str,
    direction: str,
) -> None:
    random_generator = np.random.default_rng(schema.RANDOM_SEED)

    value = dataset._generate_rare_feature_value(
        property_type,
        feature,
        direction,
        random_generator,
    )

    typical_minimum, typical_maximum = (
        scenarios.TYPICAL_RANGES[property_type][feature]
    )
    absolute_minimum, absolute_maximum = (
        schema.NUMERIC_LIMITS[feature]
    )

    assert absolute_minimum <= value <= absolute_maximum

    if direction == "ABAIXO":
        assert value < typical_minimum
    else:
        assert value > typical_maximum

    if feature == "consumo_kwh":
        assert isinstance(value, float)
        assert value == round(value, 2)
    else:
        assert isinstance(value, int)


def test_valor_raro_e_reproduzivel() -> None:
    first_generator = np.random.default_rng(schema.RANDOM_SEED)
    second_generator = np.random.default_rng(schema.RANDOM_SEED)

    first_values = [
        dataset._generate_rare_feature_value(
            property_type,
            feature,
            direction,
            first_generator,
        )
        for property_type in schema.PROPERTY_TYPES
        for feature in scenarios.RARE_CASE_FEATURES
        for direction in scenarios.RARE_CASE_DIRECTIONS
    ]
    second_values = [
        dataset._generate_rare_feature_value(
            property_type,
            feature,
            direction,
            second_generator,
        )
        for property_type in schema.PROPERTY_TYPES
        for feature in scenarios.RARE_CASE_FEATURES
        for direction in scenarios.RARE_CASE_DIRECTIONS
    ]

    assert first_values == second_values


def test_valor_raro_rejeita_tipo_imovel_invalido() -> None:
    with pytest.raises(
        ValueError,
        match="Tipo de imóvel inválido",
    ):
        dataset._generate_rare_feature_value(
            "INVALIDO",
            "consumo_kwh",
            "ABAIXO",
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_valor_raro_rejeita_feature_invalida() -> None:
    with pytest.raises(
        ValueError,
        match="Feature rara inválida",
    ):
        dataset._generate_rare_feature_value(
            "CASA",
            "feature_invalida",
            "ABAIXO",
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_valor_raro_rejeita_direcao_invalida() -> None:
    with pytest.raises(
        ValueError,
        match="Direção rara inválida",
    ):
        dataset._generate_rare_feature_value(
            "CASA",
            "consumo_kwh",
            "INVALIDA",
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_mutacoes_raras_alteram_somente_celulas_atribuidas() -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    original = sample.copy(deep=True)
    assignments = [
        (0, "consumo_kwh", "ABAIXO"),
        (1, "quantidade_equipamentos", "ACIMA"),
        (2, "horas_alto_consumo", "ABAIXO"),
    ]

    mutated = dataset.apply_rare_case_feature_mutations(
        sample,
        assignments,
        seed=schema.RANDOM_SEED,
    )

    expected_changes = pd.DataFrame(
        False,
        index=sample.index,
        columns=sample.columns,
    )

    for position, feature, direction in assignments:
        expected_changes.iat[
            position,
            sample.columns.get_loc(feature),
        ] = True

        row = mutated.iloc[position]
        property_type = str(row["tipo_imovel"])
        value = row[feature]

        typical_minimum, typical_maximum = (
            scenarios.TYPICAL_RANGES[property_type][feature]
        )
        absolute_minimum, absolute_maximum = (
            schema.NUMERIC_LIMITS[feature]
        )

        assert absolute_minimum <= value <= absolute_maximum

        if direction == "ABAIXO":
            assert value < typical_minimum
        else:
            assert value > typical_maximum

    assert mutated is not sample
    assert mutated.index.equals(sample.index)
    assert mutated.columns.equals(sample.columns)
    pd.testing.assert_frame_equal(sample, original)
    pd.testing.assert_frame_equal(
        sample.ne(mutated),
        expected_changes,
    )


def test_mutacoes_raras_sao_reproduziveis() -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    assignments = [
        (0, "consumo_kwh", "ABAIXO"),
        (1, "quantidade_equipamentos", "ACIMA"),
        (2, "horas_alto_consumo", "ABAIXO"),
    ]

    first = dataset.apply_rare_case_feature_mutations(
        sample,
        assignments,
        seed=schema.RANDOM_SEED,
    )
    second = dataset.apply_rare_case_feature_mutations(
        sample,
        assignments,
        seed=schema.RANDOM_SEED,
    )

    pd.testing.assert_frame_equal(first, second)


def test_mutacoes_raras_aceitam_atribuicoes_vazias() -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )

    mutated = dataset.apply_rare_case_feature_mutations(
        sample,
        [],
        seed=schema.RANDOM_SEED,
    )

    assert mutated is not sample
    pd.testing.assert_frame_equal(mutated, sample)


@pytest.mark.parametrize(
    "missing_column",
    (
        "tipo_imovel",
        *scenarios.RARE_CASE_FEATURES,
    ),
)
def test_mutacoes_raras_rejeitam_coluna_obrigatoria_ausente(
    missing_column: str,
) -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    incomplete_sample = sample.drop(columns=[missing_column])

    with pytest.raises(
        ValueError,
        match="Colunas obrigatórias ausentes",
    ):
        dataset.apply_rare_case_feature_mutations(
            incomplete_sample,
            [],
            seed=schema.RANDOM_SEED,
        )


def test_mutacoes_raras_rejeitam_colunas_duplicadas() -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    duplicated_sample = pd.concat(
        [
            sample,
            sample[["consumo_kwh"]],
        ],
        axis=1,
    )

    with pytest.raises(
        ValueError,
        match="A amostra não pode possuir colunas duplicadas",
    ):
        dataset.apply_rare_case_feature_mutations(
            duplicated_sample,
            [],
            seed=schema.RANDOM_SEED,
        )


@pytest.mark.parametrize(
    "position",
    [
        True,
        1.5,
        "1",
    ],
)
def test_mutacoes_raras_rejeitam_posicao_nao_inteira(
    position: object,
) -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    assignments = [
        (position, "consumo_kwh", "ABAIXO"),
    ]

    with pytest.raises(
        ValueError,
        match="As posições das atribuições devem ser inteiras",
    ):
        dataset.apply_rare_case_feature_mutations(
            sample,
            assignments,
            seed=schema.RANDOM_SEED,
        )


@pytest.mark.parametrize(
    "position",
    [
        -1,
        4,
    ],
)
def test_mutacoes_raras_rejeitam_posicao_fora_da_amostra(
    position: int,
) -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    assignments = [
        (position, "consumo_kwh", "ABAIXO"),
    ]

    with pytest.raises(
        ValueError,
        match="Posição rara fora da amostra",
    ):
        dataset.apply_rare_case_feature_mutations(
            sample,
            assignments,
            seed=schema.RANDOM_SEED,
        )


def test_mutacoes_raras_rejeitam_posicoes_duplicadas() -> None:
    sample = dataset.generate_typical_sample(
        4,
        seed=schema.RANDOM_SEED,
    )
    original = sample.copy(deep=True)
    assignments = [
        (0, "consumo_kwh", "ABAIXO"),
        (0, "quantidade_equipamentos", "ACIMA"),
    ]

    with pytest.raises(
        ValueError,
        match="Posição rara duplicada: 0",
    ):
        dataset.apply_rare_case_feature_mutations(
            sample,
            assignments,
            seed=schema.RANDOM_SEED,
        )

    pd.testing.assert_frame_equal(sample, original)


def test_amostra_auditada_com_raros_integra_cenarios_e_rotulos() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    rare_flags = sample["caso_raro"]
    boundary_flags = sample["caso_fronteira"]
    outlier_flags = sample["outlier_plausivel"]
    expected_outlier_count = int(
        round(
            schema.DATASET_SIZE
            * scenarios.PLAUSIBLE_OUTLIER_RATIO
        )
    )
    recalculated_scores = dataset.calculate_reference_scores(sample)
    recalculated_categories = dataset.categorize_reference_scores(
        recalculated_scores
    )

    scenario_counts = (
        sample["tipo_cenario"]
        .value_counts()
        .to_dict()
    )

    assert sample.shape == (
        schema.DATASET_SIZE,
        len(schema.DATASET_COLUMNS),
    )
    assert tuple(sample.columns) == schema.DATASET_COLUMNS
    assert scenario_counts == {
        "TIPICO": 4600,
        "RARO_EXTREMO": 250,
        "FRONTEIRA": 150,
    }
    assert int(rare_flags.sum()) == 250
    assert int(boundary_flags.sum()) == 150
    assert not bool((rare_flags & boundary_flags).any())
    assert int(outlier_flags.sum()) == expected_outlier_count
    assert not bool((outlier_flags & ~rare_flags).any())
    assert not bool((outlier_flags & boundary_flags).any())
    assert sample.loc[
        rare_flags,
        "tipo_cenario",
    ].eq("RARO_EXTREMO").all()
    assert np.array_equal(
        sample["score_referencia"].to_numpy(),
        recalculated_scores,
    )
    assert np.array_equal(
        sample[schema.TARGET_COLUMN].to_numpy(),
        recalculated_categories,
    )


def test_outliers_plausiveis_ultrapassam_iqr_e_respeitam_limites() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    reference_sample = sample.loc[
        ~sample["caso_raro"]
        & ~sample["caso_fronteira"]
    ]
    outlier_sample = sample.loc[
        sample["outlier_plausivel"]
    ]

    expected_outlier_count = int(
        round(
            schema.DATASET_SIZE
            * scenarios.PLAUSIBLE_OUTLIER_RATIO
        )
    )

    assert len(outlier_sample) == expected_outlier_count

    for _, row in outlier_sample.iterrows():
        property_type = str(row["tipo_imovel"])

        features_outside_typical_range = [
            feature
            for feature in scenarios.RARE_CASE_FEATURES
            if (
                row[feature]
                < scenarios.TYPICAL_RANGES[
                    property_type
                ][feature][0]
                or row[feature]
                > scenarios.TYPICAL_RANGES[
                    property_type
                ][feature][1]
            )
        ]

        assert len(features_outside_typical_range) == 1

        feature = features_outside_typical_range[0]

        reference_values = reference_sample.loc[
            reference_sample["tipo_imovel"].eq(
                property_type
            ),
            feature,
        ].astype(float)

        assert not reference_values.empty

        q1 = float(reference_values.quantile(0.25))
        q3 = float(reference_values.quantile(0.75))
        iqr = q3 - q1

        lower_fence = q1 - 1.5 * iqr
        upper_fence = q3 + 1.5 * iqr

        absolute_minimum, absolute_maximum = (
            schema.NUMERIC_LIMITS[feature]
        )
        value = float(row[feature])

        assert absolute_minimum < value < absolute_maximum
        assert value < lower_fence or value > upper_fence


def test_amostra_auditada_com_raros_tem_uma_feature_fora_da_faixa() -> None:
    sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    rare_sample = sample.loc[sample["caso_raro"]]

    outside_counts: list[int] = []

    for _, row in rare_sample.iterrows():
        property_type = str(row["tipo_imovel"])
        outside_count = 0

        for feature in scenarios.RARE_CASE_FEATURES:
            typical_minimum, typical_maximum = (
                scenarios.TYPICAL_RANGES[property_type][feature]
            )
            absolute_minimum, absolute_maximum = (
                schema.NUMERIC_LIMITS[feature]
            )
            value = row[feature]

            assert absolute_minimum <= value <= absolute_maximum

            if value < typical_minimum or value > typical_maximum:
                outside_count += 1

        outside_counts.append(outside_count)

    assert len(outside_counts) == 250
    assert set(outside_counts) == {1}


def test_amostra_auditada_com_raros_e_reproduzivel() -> None:
    first_sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )
    second_sample = dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )

    pd.testing.assert_frame_equal(first_sample, second_sample)
