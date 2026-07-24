"""Testes da alocação inicial do gerador sintético EnergIAI V2."""

import importlib.util
from collections import Counter
from pathlib import Path
from types import ModuleType

import numpy as np
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"


def load_module(module_name: str, filename: str) -> ModuleType:
    """Carrega um módulo diretamente pelo caminho do arquivo."""
    module_path = SRC_PATH / filename
    spec = importlib.util.spec_from_file_location(module_name, module_path)

    if spec is None or spec.loader is None:
        raise ImportError(f"Não foi possível carregar o módulo em {module_path}")

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


generator = load_module("energiai_generator", "generator.py")
schema = load_module("energiai_schema", "schema.py")
scenarios = load_module("energiai_scenarios", "scenarios.py")


def test_alocacao_para_cem_registros_respeita_distribuicao() -> None:
    counts = generator.allocate_counts(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
    )

    assert counts == {
        "CASA": 32,
        "APARTAMENTO": 32,
        "COMERCIO": 16,
        "ESCRITORIO": 10,
        "INDUSTRIA": 5,
        "OUTRO": 5,
    }


def test_alocacao_fracionaria_preserva_tamanho_total() -> None:
    counts = generator.allocate_counts(
        101,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
    )

    assert sum(counts.values()) == 101
    assert set(counts) == set(schema.PROPERTY_TYPES)
    assert all(count >= 0 for count in counts.values())


@pytest.mark.parametrize("sample_size", [0, -1])
def test_alocacao_rejeita_tamanho_invalido(sample_size: int) -> None:
    with pytest.raises(
        ValueError,
        match="sample_size deve ser maior que zero",
    ):
        generator.allocate_counts(
            sample_size,
            scenarios.PROPERTY_TYPE_DISTRIBUTION,
        )


def test_alocacao_rejeita_distribuicao_vazia() -> None:
    with pytest.raises(
        ValueError,
        match="distribution não pode estar vazia",
    ):
        generator.allocate_counts(100, {})


def test_alocacao_rejeita_proporcao_negativa() -> None:
    invalid_distribution = {
        "CASA": 1.10,
        "APARTAMENTO": -0.10,
    }

    with pytest.raises(
        ValueError,
        match="As proporções não podem ser negativas",
    ):
        generator.allocate_counts(100, invalid_distribution)


def test_alocacao_rejeita_distribuicao_que_nao_soma_um() -> None:
    invalid_distribution = {
        "CASA": 0.50,
        "APARTAMENTO": 0.40,
    }

    with pytest.raises(
        ValueError,
        match="As proporções devem somar 1.0",
    ):
        generator.allocate_counts(100, invalid_distribution)


def test_geracao_dos_tipos_de_imovel_e_reprodutivel() -> None:
    first_sample = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )
    second_sample = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )

    assert isinstance(first_sample, np.ndarray)
    assert len(first_sample) == 100
    assert np.array_equal(first_sample, second_sample)
    assert set(first_sample) == set(schema.PROPERTY_TYPES)


def test_geracao_dos_tipos_respeita_as_contagens() -> None:
    property_types = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )

    counts = Counter(str(value) for value in property_types)

    assert counts == {
        "CASA": 32,
        "APARTAMENTO": 32,
        "COMERCIO": 16,
        "ESCRITORIO": 10,
        "INDUSTRIA": 5,
        "OUTRO": 5,
    }


def test_geracao_de_equipamentos_respeita_faixas_e_seed() -> None:
    property_types = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )

    first_sample = generator.generate_equipment_counts(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(schema.RANDOM_SEED + 1),
    )
    second_sample = generator.generate_equipment_counts(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(schema.RANDOM_SEED + 1),
    )

    assert len(first_sample) == len(property_types)
    assert np.issubdtype(first_sample.dtype, np.integer)
    assert np.array_equal(first_sample, second_sample)

    for property_type, equipment_count in zip(
        property_types,
        first_sample,
        strict=True,
    ):
        minimum, maximum = scenarios.TYPICAL_RANGES[
            str(property_type)
        ]["quantidade_equipamentos"]

        assert minimum <= equipment_count <= maximum


def test_geracao_de_equipamentos_rejeita_tipos_vazios() -> None:
    with pytest.raises(
        ValueError,
        match="property_types não pode estar vazio",
    ):
        generator.generate_equipment_counts(
            np.array([], dtype=str),
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_equipamentos_rejeita_imovel_desconhecido() -> None:
    with pytest.raises(
        ValueError,
        match="Tipo de imóvel sem faixas configuradas: DESCONHECIDO",
    ):
        generator.generate_equipment_counts(
            np.array(["DESCONHECIDO"], dtype=str),
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_equipamentos_rejeita_faixa_ausente() -> None:
    invalid_ranges = {
        "CASA": {
            "consumo_kwh": (180.0, 520.0),
        },
    }

    with pytest.raises(
        ValueError,
        match="Faixa de quantidade_equipamentos ausente para: CASA",
    ):
        generator.generate_equipment_counts(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_equipamentos_rejeita_faixa_nao_inteira() -> None:
    invalid_ranges = {
        "CASA": {
            "quantidade_equipamentos": (4.5, 22),
        },
    }

    with pytest.raises(
        ValueError,
        match="A faixa de quantidade_equipamentos deve ser inteira",
    ):
        generator.generate_equipment_counts(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_equipamentos_rejeita_faixa_invertida() -> None:
    invalid_ranges = {
        "CASA": {
            "quantidade_equipamentos": (22, 4),
        },
    }

    with pytest.raises(
        ValueError,
        match=(
            "O mínimo de quantidade_equipamentos "
            "não pode superar o máximo"
        ),
    ):
        generator.generate_equipment_counts(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_horas_respeita_faixas_e_seed() -> None:
    property_types = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )

    first_sample = generator.generate_high_consumption_hours(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(schema.RANDOM_SEED + 2),
    )
    second_sample = generator.generate_high_consumption_hours(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(schema.RANDOM_SEED + 2),
    )

    assert len(first_sample) == len(property_types)
    assert np.issubdtype(first_sample.dtype, np.integer)
    assert np.array_equal(first_sample, second_sample)

    for property_type, high_consumption_hours in zip(
        property_types,
        first_sample,
        strict=True,
    ):
        minimum, maximum = scenarios.TYPICAL_RANGES[
            str(property_type)
        ]["horas_alto_consumo"]

        assert minimum <= high_consumption_hours <= maximum


def test_geracao_de_horas_rejeita_tipos_vazios() -> None:
    with pytest.raises(
        ValueError,
        match="property_types não pode estar vazio",
    ):
        generator.generate_high_consumption_hours(
            np.array([], dtype=str),
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_horas_rejeita_imovel_desconhecido() -> None:
    with pytest.raises(
        ValueError,
        match="Tipo de imóvel sem faixas configuradas: DESCONHECIDO",
    ):
        generator.generate_high_consumption_hours(
            np.array(["DESCONHECIDO"], dtype=str),
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_horas_rejeita_faixa_ausente() -> None:
    invalid_ranges = {
        "CASA": {
            "consumo_kwh": (180.0, 520.0),
        },
    }

    with pytest.raises(
        ValueError,
        match="Faixa de horas_alto_consumo ausente para: CASA",
    ):
        generator.generate_high_consumption_hours(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_horas_rejeita_faixa_nao_inteira() -> None:
    invalid_ranges = {
        "CASA": {
            "horas_alto_consumo": (1.5, 12),
        },
    }

    with pytest.raises(
        ValueError,
        match="A faixa de horas_alto_consumo deve ser inteira",
    ):
        generator.generate_high_consumption_hours(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_horas_rejeita_faixa_invertida() -> None:
    invalid_ranges = {
        "CASA": {
            "horas_alto_consumo": (12, 1),
        },
    }

    with pytest.raises(
        ValueError,
        match=(
            "O mínimo de horas_alto_consumo "
            "não pode superar o máximo"
        ),
    ):
        generator.generate_high_consumption_hours(
            np.array(["CASA"], dtype=str),
            invalid_ranges,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_respeita_tipo_tamanho_e_seed() -> None:
    property_types = generator.generate_property_types(
        100,
        scenarios.PROPERTY_TYPE_DISTRIBUTION,
        schema.RANDOM_SEED,
    )
    equipment_counts = generator.generate_equipment_counts(
        property_types,
        scenarios.TYPICAL_RANGES,
        np.random.default_rng(schema.RANDOM_SEED + 1),
    )
    high_consumption_hours = (
        generator.generate_high_consumption_hours(
            property_types,
            scenarios.TYPICAL_RANGES,
            np.random.default_rng(schema.RANDOM_SEED + 2),
        )
    )

    first_sample = generator.generate_peak_usage(
        property_types,
        equipment_counts,
        high_consumption_hours,
        scenarios.TYPICAL_RANGES,
        scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
        np.random.default_rng(schema.RANDOM_SEED + 3),
    )
    second_sample = generator.generate_peak_usage(
        property_types,
        equipment_counts,
        high_consumption_hours,
        scenarios.TYPICAL_RANGES,
        scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
        np.random.default_rng(schema.RANDOM_SEED + 3),
    )

    assert len(first_sample) == len(property_types)
    assert np.issubdtype(first_sample.dtype, np.bool_)
    assert np.array_equal(first_sample, second_sample)


def test_geracao_de_pico_reflete_extremos_observaveis() -> None:
    parameters = {
        "intercept": 0.0,
        "equipment_weight": 0.25,
        "hours_weight": 0.35,
        "interaction_weight": 0.40,
        "minimum_probability": 0.0,
        "maximum_probability": 1.0,
    }

    peak_usage = generator.generate_peak_usage(
        np.array(["CASA", "CASA"], dtype=str),
        np.array([4, 22], dtype=int),
        np.array([1, 12], dtype=int),
        scenarios.TYPICAL_RANGES,
        parameters,
        np.random.default_rng(schema.RANDOM_SEED),
    )

    assert peak_usage.tolist() == [False, True]


def test_geracao_de_pico_rejeita_tipos_vazios() -> None:
    with pytest.raises(
        ValueError,
        match="property_types não pode estar vazio",
    ):
        generator.generate_peak_usage(
            np.array([], dtype=str),
            np.array([], dtype=int),
            np.array([], dtype=int),
            scenarios.TYPICAL_RANGES,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_equipamentos_com_tamanho_diferente() -> None:
    with pytest.raises(
        ValueError,
        match=(
            "equipment_counts deve possuir o mesmo tamanho "
            "de property_types"
        ),
    ):
        generator.generate_peak_usage(
            np.array(["CASA", "CASA"], dtype=str),
            np.array([10], dtype=int),
            np.array([5, 6], dtype=int),
            scenarios.TYPICAL_RANGES,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_horas_com_tamanho_diferente() -> None:
    with pytest.raises(
        ValueError,
        match=(
            "high_consumption_hours deve possuir o mesmo tamanho "
            "de property_types"
        ),
    ):
        generator.generate_peak_usage(
            np.array(["CASA", "CASA"], dtype=str),
            np.array([10, 11], dtype=int),
            np.array([5], dtype=int),
            scenarios.TYPICAL_RANGES,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_parametro_ausente() -> None:
    invalid_parameters = dict(
        scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS
    )
    del invalid_parameters["hours_weight"]

    with pytest.raises(
        ValueError,
        match="Parâmetros de probabilidade ausentes: hours_weight",
    ):
        generator.generate_peak_usage(
            np.array(["CASA"], dtype=str),
            np.array([10], dtype=int),
            np.array([5], dtype=int),
            scenarios.TYPICAL_RANGES,
            invalid_parameters,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_imovel_desconhecido() -> None:
    with pytest.raises(
        ValueError,
        match="Tipo de imóvel sem faixas configuradas: DESCONHECIDO",
    ):
        generator.generate_peak_usage(
            np.array(["DESCONHECIDO"], dtype=str),
            np.array([10], dtype=int),
            np.array([5], dtype=int),
            scenarios.TYPICAL_RANGES,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_faixa_de_equipamentos_ausente() -> None:
    invalid_ranges = {
        "CASA": {
            "horas_alto_consumo": (1, 12),
        },
    }

    with pytest.raises(
        ValueError,
        match="Faixa de quantidade_equipamentos ausente para: CASA",
    ):
        generator.generate_peak_usage(
            np.array(["CASA"], dtype=str),
            np.array([10], dtype=int),
            np.array([5], dtype=int),
            invalid_ranges,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_faixa_de_horas_ausente() -> None:
    invalid_ranges = {
        "CASA": {
            "quantidade_equipamentos": (4, 22),
        },
    }

    with pytest.raises(
        ValueError,
        match="Faixa de horas_alto_consumo ausente para: CASA",
    ):
        generator.generate_peak_usage(
            np.array(["CASA"], dtype=str),
            np.array([10], dtype=int),
            np.array([5], dtype=int),
            invalid_ranges,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_amplitude_de_equipamentos_invalida() -> None:
    invalid_ranges = {
        "CASA": {
            "quantidade_equipamentos": (4, 4),
            "horas_alto_consumo": (1, 12),
        },
    }

    with pytest.raises(
        ValueError,
        match=(
            "A faixa de quantidade_equipamentos "
            "deve possuir amplitude positiva"
        ),
    ):
        generator.generate_peak_usage(
            np.array(["CASA"], dtype=str),
            np.array([4], dtype=int),
            np.array([5], dtype=int),
            invalid_ranges,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )


def test_geracao_de_pico_rejeita_amplitude_de_horas_invalida() -> None:
    invalid_ranges = {
        "CASA": {
            "quantidade_equipamentos": (4, 22),
            "horas_alto_consumo": (1, 1),
        },
    }

    with pytest.raises(
        ValueError,
        match=(
            "A faixa de horas_alto_consumo "
            "deve possuir amplitude positiva"
        ),
    ):
        generator.generate_peak_usage(
            np.array(["CASA"], dtype=str),
            np.array([10], dtype=int),
            np.array([1], dtype=int),
            invalid_ranges,
            scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS,
            np.random.default_rng(schema.RANDOM_SEED),
        )
