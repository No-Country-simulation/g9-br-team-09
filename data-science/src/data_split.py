"""Divisão estratificada central do Dataset EnergIAI V2.

Este módulo seleciona exclusivamente as cinco features de produção e o target
oficial, cria os conjuntos de treino, validação e teste e valida a integridade
estrutural da divisão.

O módulo não executa EDA, não calcula estatísticas descritivas do holdout, não
treina modelos e não altera o DataFrame recebido.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split

import schema


__all__ = [
    "DataSplit",
    "create_stratified_data_split",
]


_TRAIN_RATIO = 0.70
_VALIDATION_RATIO = 0.15
_TEST_RATIO = 0.15
_TEMPORARY_RATIO = _VALIDATION_RATIO + _TEST_RATIO

_EXPECTED_TRAIN_SIZE = int(schema.DATASET_SIZE * _TRAIN_RATIO)
_EXPECTED_VALIDATION_SIZE = int(
    schema.DATASET_SIZE * _VALIDATION_RATIO
)
_EXPECTED_TEST_SIZE = (
    schema.DATASET_SIZE
    - _EXPECTED_TRAIN_SIZE
    - _EXPECTED_VALIDATION_SIZE
)

_NUMERIC_FEATURES = (
    "consumo_kwh",
    "quantidade_equipamentos",
    "horas_alto_consumo",
)


@dataclass(frozen=True)
class DataSplit:
    """Conjuntos de treino, validação e teste com índices preservados."""

    x_train: pd.DataFrame
    x_validation: pd.DataFrame
    x_test: pd.DataFrame
    y_train: pd.Series
    y_validation: pd.Series
    y_test: pd.Series


def _validate_seed(seed: int) -> None:
    """Valida a seed usada na divisão reproduzível."""
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise TypeError("seed deve ser um inteiro")


def _validate_required_columns(sample: pd.DataFrame) -> None:
    """Valida a presença das features e do target oficial."""
    if sample.columns.has_duplicates:
        raise ValueError("O dataset possui nomes de colunas duplicados")

    required_columns = (
        *schema.FEATURE_COLUMNS,
        schema.TARGET_COLUMN,
    )
    missing_columns = sorted(
        set(required_columns).difference(sample.columns)
    )

    if missing_columns:
        raise KeyError(
            "Colunas obrigatórias ausentes para o split: "
            + ", ".join(missing_columns)
        )


def _validate_numeric_features(sample: pd.DataFrame) -> None:
    """Valida tipos, finitude e limites das features numéricas."""
    non_numeric_features = [
        feature
        for feature in _NUMERIC_FEATURES
        if not pd.api.types.is_numeric_dtype(sample[feature])
    ]

    if non_numeric_features:
        raise TypeError(
            "Features com tipo não numérico: "
            + ", ".join(non_numeric_features)
        )

    numeric_values = sample.loc[
        :,
        list(_NUMERIC_FEATURES),
    ].to_numpy(dtype=float)

    if not np.isfinite(numeric_values).all():
        raise ValueError(
            "As features numéricas contêm valores não finitos"
        )

    for feature in _NUMERIC_FEATURES:
        minimum, maximum = schema.NUMERIC_LIMITS[feature]
        invalid_values = ~sample[feature].between(
            minimum,
            maximum,
            inclusive="both",
        )

        if invalid_values.any():
            raise ValueError(
                f"{feature} contém valores fora do intervalo "
                f"[{minimum}, {maximum}]"
            )


def _validate_feature_domains(sample: pd.DataFrame) -> None:
    """Valida os domínios das features categóricas e booleanas."""
    if not pd.api.types.is_bool_dtype(
        sample["uso_horario_pico"]
    ):
        raise TypeError(
            "uso_horario_pico deve possuir tipo booleano"
        )

    observed_property_types = set(
        sample["tipo_imovel"].astype(str).unique()
    )
    expected_property_types = set(schema.PROPERTY_TYPES)

    unexpected_property_types = sorted(
        observed_property_types - expected_property_types
    )
    missing_property_types = sorted(
        expected_property_types - observed_property_types
    )

    if unexpected_property_types:
        raise ValueError(
            "Tipos de imóvel inválidos: "
            + ", ".join(unexpected_property_types)
        )

    if missing_property_types:
        raise ValueError(
            "Tipos de imóvel obrigatórios ausentes: "
            + ", ".join(missing_property_types)
        )


def _validate_target(sample: pd.DataFrame) -> None:
    """Valida o domínio completo do target oficial."""
    observed_categories = set(
        sample[schema.TARGET_COLUMN].astype(str).unique()
    )
    expected_categories = set(schema.ENERGY_CATEGORIES)

    unexpected_categories = sorted(
        observed_categories - expected_categories
    )
    missing_categories = sorted(
        expected_categories - observed_categories
    )

    if unexpected_categories:
        raise ValueError(
            "Categorias inválidas no target: "
            + ", ".join(unexpected_categories)
        )

    if missing_categories:
        raise ValueError(
            "Categorias obrigatórias ausentes no target: "
            + ", ".join(missing_categories)
        )


def _validate_input_sample(sample: pd.DataFrame) -> None:
    """Valida o dataset integral recebido para a criação do split."""
    if not isinstance(sample, pd.DataFrame):
        raise TypeError("sample deve ser um pandas.DataFrame")

    if sample.empty:
        raise ValueError("sample não pode estar vazio")

    if len(sample) != schema.DATASET_SIZE:
        raise ValueError(
            "sample deve conter exatamente "
            f"{schema.DATASET_SIZE} registros"
        )

    if sample.index.has_duplicates:
        raise ValueError("O índice do dataset possui valores duplicados")

    _validate_required_columns(sample)

    required_columns = [
        *schema.FEATURE_COLUMNS,
        schema.TARGET_COLUMN,
    ]
    required_data = sample.loc[:, required_columns]

    null_columns = required_data.columns[
        required_data.isna().any()
    ].tolist()

    if null_columns:
        raise ValueError(
            "Colunas utilizadas no split contêm valores nulos: "
            + ", ".join(null_columns)
        )

    _validate_numeric_features(required_data)
    _validate_feature_domains(required_data)
    _validate_target(required_data)


def _validate_split_part(
    part_name: str,
    features: pd.DataFrame,
    target: pd.Series,
    expected_size: int,
) -> None:
    """Valida um subconjunto produzido pela divisão."""
    if len(features) != expected_size:
        raise RuntimeError(
            f"{part_name} possui {len(features)} registros; "
            f"esperado: {expected_size}"
        )

    if len(target) != expected_size:
        raise RuntimeError(
            f"O target de {part_name} possui {len(target)} registros; "
            f"esperado: {expected_size}"
        )

    if tuple(features.columns) != schema.FEATURE_COLUMNS:
        raise RuntimeError(
            f"{part_name} não contém exatamente as cinco "
            "features de produção"
        )

    prohibited_columns = sorted(
        set(features.columns).intersection(
            schema.PROHIBITED_MODEL_FEATURES
        )
    )

    if prohibited_columns:
        raise RuntimeError(
            f"{part_name} contém colunas proibidas: "
            + ", ".join(prohibited_columns)
        )

    if not features.index.equals(target.index):
        raise RuntimeError(
            f"Os índices de X e y estão desalinhados em {part_name}"
        )

    if features.index.has_duplicates:
        raise RuntimeError(
            f"{part_name} possui índices duplicados"
        )

    if features.isna().any().any() or target.isna().any():
        raise RuntimeError(
            f"{part_name} contém valores nulos"
        )

    observed_categories = set(target.astype(str).unique())
    invalid_categories = sorted(
        observed_categories.difference(schema.ENERGY_CATEGORIES)
    )

    if invalid_categories:
        raise RuntimeError(
            f"{part_name} contém categorias inválidas: "
            + ", ".join(invalid_categories)
        )

    if not observed_categories:
        raise RuntimeError(
            f"{part_name} não contém categorias válidas"
        )

    numeric_values = features.loc[
        :,
        list(_NUMERIC_FEATURES),
    ].to_numpy(dtype=float)

    if not np.isfinite(numeric_values).all():
        raise RuntimeError(
            f"{part_name} contém valores numéricos não finitos"
        )


def _validate_split_integrity(
    sample: pd.DataFrame,
    split: DataSplit,
) -> None:
    """Valida tamanhos, alinhamento, sobreposição e reconstrução."""
    _validate_split_part(
        "treino",
        split.x_train,
        split.y_train,
        _EXPECTED_TRAIN_SIZE,
    )
    _validate_split_part(
        "validação",
        split.x_validation,
        split.y_validation,
        _EXPECTED_VALIDATION_SIZE,
    )
    _validate_split_part(
        "teste",
        split.x_test,
        split.y_test,
        _EXPECTED_TEST_SIZE,
    )

    train_indices = set(split.x_train.index)
    validation_indices = set(split.x_validation.index)
    test_indices = set(split.x_test.index)

    if not train_indices.isdisjoint(validation_indices):
        raise RuntimeError(
            "Treino e validação possuem índices sobrepostos"
        )

    if not train_indices.isdisjoint(test_indices):
        raise RuntimeError(
            "Treino e teste possuem índices sobrepostos"
        )

    if not validation_indices.isdisjoint(test_indices):
        raise RuntimeError(
            "Validação e teste possuem índices sobrepostos"
        )

    reconstructed_indices = (
        train_indices
        | validation_indices
        | test_indices
    )

    if reconstructed_indices != set(sample.index):
        raise RuntimeError(
            "A união dos splits não reconstrói todos os índices "
            "do dataset"
        )

    total_records = (
        len(split.x_train)
        + len(split.x_validation)
        + len(split.x_test)
    )

    if total_records != len(sample):
        raise RuntimeError(
            "A soma dos registros dos splits difere do dataset"
        )


def create_stratified_data_split(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> DataSplit:
    """Cria o split estratificado 70/15/15 do Dataset EnergIAI V2."""
    _validate_seed(seed)
    _validate_input_sample(sample)

    features = sample.loc[
        :,
        list(schema.FEATURE_COLUMNS),
    ].copy(deep=True)
    target = sample.loc[
        :,
        schema.TARGET_COLUMN,
    ].copy(deep=True)

    try:
        (
            x_train,
            x_remaining,
            y_train,
            y_remaining,
        ) = train_test_split(
            features,
            target,
            test_size=_TEMPORARY_RATIO,
            random_state=seed,
            stratify=target,
        )

        (
            x_validation,
            x_test,
            y_validation,
            y_test,
        ) = train_test_split(
            x_remaining,
            y_remaining,
            test_size=_TEST_RATIO / _TEMPORARY_RATIO,
            random_state=seed,
            stratify=y_remaining,
        )
    except ValueError as error:
        raise ValueError(
            "Não foi possível criar o split estratificado 70/15/15"
        ) from error

    split = DataSplit(
        x_train=x_train.copy(deep=True),
        x_validation=x_validation.copy(deep=True),
        x_test=x_test.copy(deep=True),
        y_train=y_train.copy(deep=True),
        y_validation=y_validation.copy(deep=True),
        y_test=y_test.copy(deep=True),
    )

    _validate_split_integrity(sample, split)

    return split
