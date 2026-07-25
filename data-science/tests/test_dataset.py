"""Testes da montagem das amostras sintéticas do Dataset EnergIAI V2."""

import sys
from pathlib import Path

import numpy as np
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
