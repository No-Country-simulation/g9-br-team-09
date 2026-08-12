"""Testes do contrato programático do Dataset EnergIAI V2."""

import importlib.util
from pathlib import Path
from types import ModuleType


SCHEMA_PATH = Path(__file__).parents[1] / "src" / "schema.py"


def load_schema() -> ModuleType:
    """Carrega o módulo schema.py diretamente pelo caminho do arquivo."""
    spec = importlib.util.spec_from_file_location("energiai_schema", SCHEMA_PATH)

    if spec is None or spec.loader is None:
        raise ImportError(f"Não foi possível carregar o schema em {SCHEMA_PATH}")

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


schema = load_schema()


def test_schema_possui_cinco_features_oficiais() -> None:
    assert schema.FEATURE_COLUMNS == (
        "consumo_kwh",
        "uso_horario_pico",
        "quantidade_equipamentos",
        "tipo_imovel",
        "horas_alto_consumo",
    )


def test_schema_possui_tipos_de_imovel_oficiais() -> None:
    assert schema.PROPERTY_TYPES == (
        "CASA",
        "APARTAMENTO",
        "COMERCIO",
        "ESCRITORIO",
        "INDUSTRIA",
        "OUTRO",
    )


def test_schema_possui_categorias_oficiais() -> None:
    assert schema.ENERGY_CATEGORIES == (
        "EFICIENTE",
        "MODERADO",
        "INEFICIENTE",
    )


def test_schema_possui_seed_e_quantidade_oficiais() -> None:
    assert schema.RANDOM_SEED == 42
    assert schema.DATASET_SIZE == 5_000


def test_limites_numericos_oficiais() -> None:
    assert schema.NUMERIC_LIMITS["consumo_kwh"] == (60.0, 2_500.0)
    assert schema.NUMERIC_LIMITS["quantidade_equipamentos"] == (1, 60)
    assert schema.NUMERIC_LIMITS["horas_alto_consumo"] == (0, 24)
    assert schema.NUMERIC_LIMITS["score_referencia"] == (0, 100)


def test_features_nao_incluem_campos_proibidos() -> None:
    assert set(schema.FEATURE_COLUMNS).isdisjoint(
        schema.PROHIBITED_MODEL_FEATURES
    )
