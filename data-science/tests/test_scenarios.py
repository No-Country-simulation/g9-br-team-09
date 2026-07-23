"""Testes das configurações dos cenários sintéticos do EnergIAI V2."""

import importlib.util
from pathlib import Path
from types import ModuleType

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


schema = load_module("energiai_schema", "schema.py")
scenarios = load_module("energiai_scenarios", "scenarios.py")


def test_distribuicao_dos_imoveis_soma_um() -> None:
    assert sum(scenarios.PROPERTY_TYPE_DISTRIBUTION.values()) == pytest.approx(
        1.0
    )


def test_distribuicao_contem_todos_os_tipos_de_imovel() -> None:
    assert set(scenarios.PROPERTY_TYPE_DISTRIBUTION) == set(
        schema.PROPERTY_TYPES
    )


def test_faixas_tipicas_contem_todos_os_tipos_de_imovel() -> None:
    assert set(scenarios.TYPICAL_RANGES) == set(schema.PROPERTY_TYPES)


def test_faixas_tipicas_possuem_as_tres_features_numericas() -> None:
    expected_features = {
        "consumo_kwh",
        "quantidade_equipamentos",
        "horas_alto_consumo",
    }

    for ranges in scenarios.TYPICAL_RANGES.values():
        assert set(ranges) == expected_features


def test_faixas_tipicas_respeitam_limites_absolutos() -> None:
    for ranges in scenarios.TYPICAL_RANGES.values():
        for feature, (minimum, maximum) in ranges.items():
            absolute_minimum, absolute_maximum = schema.NUMERIC_LIMITS[feature]

            assert minimum <= maximum
            assert absolute_minimum <= minimum
            assert maximum <= absolute_maximum


def test_tipos_de_cenario_estao_definidos() -> None:
    assert scenarios.SCENARIO_TYPES == (
        "TIPICO",
        "FRONTEIRA",
        "RARO_EXTREMO",
    )


def test_proporcoes_dos_casos_especiais() -> None:
    assert scenarios.BOUNDARY_CASE_RATIO == pytest.approx(0.03)
    assert scenarios.RARE_CASE_RATIO == pytest.approx(0.05)
    assert scenarios.PLAUSIBLE_OUTLIER_RATIO == pytest.approx(0.03)
    assert (
        scenarios.PLAUSIBLE_OUTLIER_RATIO
        <= scenarios.RARE_CASE_RATIO
    )
