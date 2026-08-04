"""Testes da divisão estratificada central do Dataset EnergIAI V2."""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest


SRC_PATH = Path(__file__).parents[1] / "src"
sys.path.insert(0, str(SRC_PATH))

import data_split  # noqa: E402
import dataset  # noqa: E402
import schema  # noqa: E402


@pytest.fixture(scope="module")
def base_sample() -> pd.DataFrame:
    """Gera uma amostra integral válida para os testes do split."""
    return dataset.generate_audited_sample_with_rare_cases(
        schema.DATASET_SIZE,
        seed=schema.RANDOM_SEED,
    )


def test_split_preserva_contrato_determinismo_e_entrada(
    base_sample: pd.DataFrame,
) -> None:
    """Valida o contrato completo da divisão estratificada."""
    sample = base_sample.copy(deep=True)
    original_sample = sample.copy(deep=True)

    first_split = data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )
    second_split = data_split.create_stratified_data_split(
        sample,
        seed=schema.RANDOM_SEED,
    )

    assert isinstance(first_split, data_split.DataSplit)

    assert len(first_split.x_train) == 3_500
    assert len(first_split.x_validation) == 750
    assert len(first_split.x_test) == 750

    assert len(first_split.y_train) == 3_500
    assert len(first_split.y_validation) == 750
    assert len(first_split.y_test) == 750

    split_parts = (
        (first_split.x_train, first_split.y_train),
        (first_split.x_validation, first_split.y_validation),
        (first_split.x_test, first_split.y_test),
    )

    for features, target in split_parts:
        assert tuple(features.columns) == schema.FEATURE_COLUMNS
        assert features.index.equals(target.index)
        assert not features.index.has_duplicates
        assert not features.isna().any().any()
        assert not target.isna().any()

        assert not set(
            schema.PROHIBITED_MODEL_FEATURES
        ).intersection(features.columns)

        assert set(target.astype(str).unique()) == set(
            schema.ENERGY_CATEGORIES
        )

    train_indices = set(first_split.x_train.index)
    validation_indices = set(first_split.x_validation.index)
    test_indices = set(first_split.x_test.index)

    assert train_indices.isdisjoint(validation_indices)
    assert train_indices.isdisjoint(test_indices)
    assert validation_indices.isdisjoint(test_indices)

    assert (
        train_indices
        | validation_indices
        | test_indices
    ) == set(sample.index)

    expected_distribution = (
        sample[schema.TARGET_COLUMN]
        .value_counts(normalize=True)
        .sort_index()
    )

    for target in (
        first_split.y_train,
        first_split.y_validation,
        first_split.y_test,
    ):
        observed_distribution = (
            target.value_counts(normalize=True).sort_index()
        )

        maximum_difference = (
            observed_distribution
            .sub(expected_distribution)
            .abs()
            .max()
        )

        assert maximum_difference <= 0.01

    pd.testing.assert_frame_equal(sample, original_sample)

    for attribute_name in (
        "x_train",
        "x_validation",
        "x_test",
    ):
        pd.testing.assert_frame_equal(
            getattr(first_split, attribute_name),
            getattr(second_split, attribute_name),
        )

    for attribute_name in (
        "y_train",
        "y_validation",
        "y_test",
    ):
        pd.testing.assert_series_equal(
            getattr(first_split, attribute_name),
            getattr(second_split, attribute_name),
        )


@pytest.mark.parametrize(
    "invalid_seed",
    [
        True,
        3.14,
        "42",
        None,
    ],
)
def test_split_rejeita_seed_invalida(
    base_sample: pd.DataFrame,
    invalid_seed: object,
) -> None:
    """Rejeita seed que não seja um inteiro não booleano."""
    with pytest.raises(TypeError, match="seed deve ser um inteiro"):
        data_split.create_stratified_data_split(
            base_sample,
            seed=invalid_seed,
        )


def test_split_rejeita_entrada_que_nao_seja_dataframe() -> None:
    """Rejeita entrada que não seja um pandas.DataFrame."""
    with pytest.raises(
        TypeError,
        match="sample deve ser um pandas.DataFrame",
    ):
        data_split.create_stratified_data_split([])


def test_split_rejeita_dataframe_vazio() -> None:
    """Rejeita um DataFrame sem registros."""
    with pytest.raises(
        ValueError,
        match="sample não pode estar vazio",
    ):
        data_split.create_stratified_data_split(pd.DataFrame())


def test_split_rejeita_quantidade_incorreta(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita dataset fora do tamanho contratual de 5.000 registros."""
    invalid_sample = base_sample.iloc[:-1].copy(deep=True)

    with pytest.raises(
        ValueError,
        match="sample deve conter exatamente 5000 registros",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_indices_duplicados(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita dataset com índices duplicados."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_index = invalid_sample.index.to_list()
    invalid_index[1] = invalid_index[0]
    invalid_sample.index = invalid_index

    with pytest.raises(
        ValueError,
        match="O índice do dataset possui valores duplicados",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_nomes_de_colunas_duplicados(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita dataset com nomes de colunas duplicados."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_columns = invalid_sample.columns.to_list()
    invalid_columns[-1] = schema.FEATURE_COLUMNS[0]
    invalid_sample.columns = invalid_columns

    with pytest.raises(
        ValueError,
        match="O dataset possui nomes de colunas duplicados",
    ):
        data_split.create_stratified_data_split(invalid_sample)


@pytest.mark.parametrize(
    "missing_column",
    [
        schema.FEATURE_COLUMNS[0],
        schema.TARGET_COLUMN,
    ],
)
def test_split_rejeita_coluna_obrigatoria_ausente(
    base_sample: pd.DataFrame,
    missing_column: str,
) -> None:
    """Rejeita ausência de feature oficial ou target."""
    invalid_sample = base_sample.drop(
        columns=[missing_column]
    ).copy(deep=True)

    with pytest.raises(
        KeyError,
        match="Colunas obrigatórias ausentes para o split",
    ):
        data_split.create_stratified_data_split(invalid_sample)


@pytest.mark.parametrize(
    "null_column",
    [
        "consumo_kwh",
        schema.TARGET_COLUMN,
    ],
)
def test_split_rejeita_valor_nulo(
    base_sample: pd.DataFrame,
    null_column: str,
) -> None:
    """Rejeita nulo nas colunas utilizadas pelo split."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample.loc[
        invalid_sample.index[0],
        null_column,
    ] = pd.NA

    with pytest.raises(
        ValueError,
        match="Colunas utilizadas no split contêm valores nulos",
    ):
        data_split.create_stratified_data_split(invalid_sample)


@pytest.mark.parametrize(
    "non_finite_value",
    [
        np.inf,
        -np.inf,
    ],
)
def test_split_rejeita_valor_nao_finito(
    base_sample: pd.DataFrame,
    non_finite_value: float,
) -> None:
    """Rejeita infinito positivo ou negativo."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample.loc[
        invalid_sample.index[0],
        "consumo_kwh",
    ] = non_finite_value

    with pytest.raises(
        ValueError,
        match="As features numéricas contêm valores não finitos",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_feature_numerica_com_tipo_invalido(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita feature numérica convertida para texto."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample["consumo_kwh"] = (
        invalid_sample["consumo_kwh"].astype(str)
    )

    with pytest.raises(
        TypeError,
        match="Features com tipo não numérico: consumo_kwh",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_feature_numerica_fora_do_limite(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita valor numérico fora do domínio contratual."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample.loc[
        invalid_sample.index[0],
        "consumo_kwh",
    ] = schema.NUMERIC_LIMITS["consumo_kwh"][1] + 1.0

    with pytest.raises(
        ValueError,
        match="consumo_kwh contém valores fora do intervalo",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_horario_pico_nao_booleano(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita coluna de horário de pico que não seja booleana."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample["uso_horario_pico"] = (
        invalid_sample["uso_horario_pico"].astype(str)
    )

    with pytest.raises(
        TypeError,
        match="uso_horario_pico deve possuir tipo booleano",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_tipo_de_imovel_invalido(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita categoria de imóvel fora do domínio oficial."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample.loc[
        invalid_sample.index[0],
        "tipo_imovel",
    ] = "FAZENDA"

    with pytest.raises(
        ValueError,
        match="Tipos de imóvel inválidos: FAZENDA",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_tipo_de_imovel_obrigatorio_ausente(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita dataset sem um dos seis tipos de imóvel."""
    invalid_sample = base_sample.copy(deep=True)
    missing_property_type = schema.PROPERTY_TYPES[0]
    replacement_property_type = schema.PROPERTY_TYPES[1]

    invalid_sample.loc[
        invalid_sample["tipo_imovel"] == missing_property_type,
        "tipo_imovel",
    ] = replacement_property_type

    with pytest.raises(
        ValueError,
        match="Tipos de imóvel obrigatórios ausentes",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_categoria_invalida(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita categoria do target fora do domínio oficial."""
    invalid_sample = base_sample.copy(deep=True)
    invalid_sample.loc[
        invalid_sample.index[0],
        schema.TARGET_COLUMN,
    ] = "DESCONHECIDA"

    with pytest.raises(
        ValueError,
        match="Categorias inválidas no target: DESCONHECIDA",
    ):
        data_split.create_stratified_data_split(invalid_sample)


def test_split_rejeita_categoria_obrigatoria_ausente(
    base_sample: pd.DataFrame,
) -> None:
    """Rejeita dataset sem uma das três categorias oficiais."""
    invalid_sample = base_sample.copy(deep=True)
    missing_category = schema.ENERGY_CATEGORIES[0]
    replacement_category = schema.ENERGY_CATEGORIES[1]

    invalid_sample.loc[
        invalid_sample[schema.TARGET_COLUMN] == missing_category,
        schema.TARGET_COLUMN,
    ] = replacement_category

    with pytest.raises(
        ValueError,
        match="Categorias obrigatórias ausentes no target",
    ):
        data_split.create_stratified_data_split(invalid_sample)
