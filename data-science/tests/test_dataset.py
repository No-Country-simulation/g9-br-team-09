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
