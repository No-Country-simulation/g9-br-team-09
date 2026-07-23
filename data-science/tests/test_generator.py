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
