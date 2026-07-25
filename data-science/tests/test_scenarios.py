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


def test_parametros_de_probabilidade_de_pico_possuem_chaves_esperadas() -> None:
    expected_parameters = {
        "intercept",
        "equipment_weight",
        "hours_weight",
        "interaction_weight",
        "minimum_probability",
        "maximum_probability",
    }

    assert set(
        scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS
    ) == expected_parameters


def test_parametros_de_probabilidade_de_pico_respeitam_limites() -> None:
    parameters = scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS

    assert 0.0 <= parameters["minimum_probability"] < 1.0
    assert 0.0 < parameters["maximum_probability"] <= 1.0
    assert (
        parameters["minimum_probability"]
        < parameters["maximum_probability"]
    )

    assert (
        parameters["minimum_probability"]
        <= parameters["intercept"]
        <= parameters["maximum_probability"]
    )

    for parameter_name in (
        "equipment_weight",
        "hours_weight",
        "interaction_weight",
    ):
        assert 0.0 <= parameters[parameter_name] <= 1.0


def test_probabilidade_teorica_de_pico_permanece_no_intervalo() -> None:
    parameters = scenarios.PEAK_USAGE_PROBABILITY_PARAMETERS

    minimum_probability = parameters["intercept"]
    maximum_probability = (
        parameters["intercept"]
        + parameters["equipment_weight"]
        + parameters["hours_weight"]
        + parameters["interaction_weight"]
    )

    assert (
        parameters["minimum_probability"]
        <= minimum_probability
        <= parameters["maximum_probability"]
    )
    assert (
        parameters["minimum_probability"]
        <= maximum_probability
        <= parameters["maximum_probability"]
    )

def test_parametros_de_geracao_de_consumo_possuem_chaves_esperadas() -> None:
    expected_parameters = {
        "equipment_weight",
        "hours_weight",
        "peak_weight",
        "interaction_weight",
        "noise_standard_deviation",
        "minimum_normalized_consumption",
        "maximum_normalized_consumption",
    }

    assert set(
        scenarios.CONSUMPTION_GENERATION_PARAMETERS
    ) == expected_parameters


def test_pesos_de_geracao_de_consumo_somam_um() -> None:
    parameters = scenarios.CONSUMPTION_GENERATION_PARAMETERS
    weight_names = (
        "equipment_weight",
        "hours_weight",
        "peak_weight",
        "interaction_weight",
    )

    for weight_name in weight_names:
        assert 0.0 < parameters[weight_name] <= 1.0

    assert sum(
        parameters[weight_name]
        for weight_name in weight_names
    ) == pytest.approx(1.0)


def test_parametros_de_geracao_de_consumo_respeitam_limites() -> None:
    parameters = scenarios.CONSUMPTION_GENERATION_PARAMETERS

    assert parameters["noise_standard_deviation"] == pytest.approx(0.04)
    assert parameters["minimum_normalized_consumption"] == pytest.approx(
        0.0
    )
    assert parameters["maximum_normalized_consumption"] == pytest.approx(
        1.0
    )
    assert (
        parameters["minimum_normalized_consumption"]
        < parameters["maximum_normalized_consumption"]
    )


def test_distribuicao_alvo_das_categorias() -> None:
    assert set(
        scenarios.TARGET_CATEGORY_DISTRIBUTION
    ) == set(schema.ENERGY_CATEGORIES)
    assert scenarios.TARGET_CATEGORY_DISTRIBUTION == {
        "EFICIENTE": 0.30,
        "MODERADO": 0.40,
        "INEFICIENTE": 0.30,
    }
    assert sum(
        scenarios.TARGET_CATEGORY_DISTRIBUTION.values()
    ) == pytest.approx(1.0)


def test_faixas_do_score_de_referencia() -> None:
    assert set(
        scenarios.REFERENCE_SCORE_CATEGORY_RANGES
    ) == set(schema.ENERGY_CATEGORIES)
    assert scenarios.REFERENCE_SCORE_CATEGORY_RANGES == {
        "EFICIENTE": (0, 30),
        "MODERADO": (31, 60),
        "INEFICIENTE": (61, 100),
    }

    score_minimum, score_maximum = schema.NUMERIC_LIMITS[
        "score_referencia"
    ]
    previous_maximum = int(score_minimum) - 1

    for category in schema.ENERGY_CATEGORIES:
        minimum, maximum = (
            scenarios.REFERENCE_SCORE_CATEGORY_RANGES[category]
        )

        assert minimum == previous_maximum + 1
        assert minimum <= maximum
        previous_maximum = maximum

    assert previous_maximum == int(score_maximum)


def test_parametros_do_score_de_referencia() -> None:
    assert scenarios.REFERENCE_SCORE_PARAMETERS == {
        "consumption_weight": 0.30,
        "equipment_weight": 0.18,
        "hours_weight": 0.18,
        "peak_weight": 0.08,
        "consumption_hours_interaction_weight": 0.12,
        "equipment_hours_interaction_weight": 0.08,
        "consumption_quadratic_weight": 0.06,
        "score_intercept": -0.918129,
        "score_scale": 113.330515,
    }


def test_pesos_do_score_de_referencia_somam_um() -> None:
    parameters = scenarios.REFERENCE_SCORE_PARAMETERS
    weight_names = (
        "consumption_weight",
        "equipment_weight",
        "hours_weight",
        "peak_weight",
        "consumption_hours_interaction_weight",
        "equipment_hours_interaction_weight",
        "consumption_quadratic_weight",
    )

    assert sum(
        parameters[name]
        for name in weight_names
    ) == pytest.approx(1.0)
    assert parameters["score_scale"] > 0.0
