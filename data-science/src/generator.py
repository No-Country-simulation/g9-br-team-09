"""Gerador sintético do Dataset EnergIAI V2.

Nesta etapa, o módulo implementa:

- alocação reproduzível dos tipos de imóvel;
- geração embaralhada dos tipos de imóvel;
- geração da quantidade de equipamentos nas faixas típicas.

As demais features, score, target e casos especiais serão adicionados e
validados em etapas posteriores.
"""

from collections.abc import Mapping
from math import floor

import numpy as np
from numpy.typing import NDArray


def allocate_counts(
    sample_size: int,
    distribution: Mapping[str, float],
) -> dict[str, int]:
    """Converte proporções em contagens inteiras que somam a amostra."""
    if sample_size <= 0:
        raise ValueError("sample_size deve ser maior que zero")

    if not distribution:
        raise ValueError("distribution não pode estar vazia")

    if any(proportion < 0 for proportion in distribution.values()):
        raise ValueError("As proporções não podem ser negativas")

    total_probability = sum(distribution.values())

    if not np.isclose(total_probability, 1.0):
        raise ValueError("As proporções devem somar 1.0")

    exact_counts = {
        property_type: sample_size * proportion
        for property_type, proportion in distribution.items()
    }

    counts = {
        property_type: floor(exact_count)
        for property_type, exact_count in exact_counts.items()
    }

    remaining = sample_size - sum(counts.values())

    largest_remainders = sorted(
        distribution,
        key=lambda property_type: (
            exact_counts[property_type] - counts[property_type],
            property_type,
        ),
        reverse=True,
    )

    for property_type in largest_remainders[:remaining]:
        counts[property_type] += 1

    return counts


def generate_property_types(
    sample_size: int,
    distribution: Mapping[str, float],
    seed: int,
) -> NDArray[np.str_]:
    """Gera e embaralha os tipos de imóvel de forma reproduzível."""
    counts = allocate_counts(sample_size, distribution)

    property_types = np.array(
        [
            property_type
            for property_type, count in counts.items()
            for _ in range(count)
        ],
        dtype=str,
    )

    random_generator = np.random.default_rng(seed)
    random_generator.shuffle(property_types)

    return property_types


def generate_equipment_counts(
    property_types: NDArray[np.str_],
    typical_ranges: Mapping[
        str,
        Mapping[str, tuple[float, float]],
    ],
    random_generator: np.random.Generator,
) -> NDArray[np.int_]:
    """Gera equipamentos dentro da faixa típica de cada imóvel."""
    if property_types.size == 0:
        raise ValueError("property_types não pode estar vazio")

    equipment_counts = np.empty(property_types.size, dtype=int)

    for property_type in np.unique(property_types):
        property_type_name = str(property_type)
        property_ranges = typical_ranges.get(property_type_name)

        if property_ranges is None:
            raise ValueError(
                "Tipo de imóvel sem faixas configuradas: "
                f"{property_type_name}"
            )

        equipment_range = property_ranges.get(
            "quantidade_equipamentos"
        )

        if equipment_range is None:
            raise ValueError(
                "Faixa de quantidade_equipamentos ausente para: "
                f"{property_type_name}"
            )

        minimum, maximum = equipment_range

        if (
            not float(minimum).is_integer()
            or not float(maximum).is_integer()
        ):
            raise ValueError(
                "A faixa de quantidade_equipamentos deve ser inteira"
            )

        minimum_int = int(minimum)
        maximum_int = int(maximum)

        if minimum_int > maximum_int:
            raise ValueError(
                "O mínimo de quantidade_equipamentos "
                "não pode superar o máximo"
            )

        property_mask = property_types == property_type
        property_count = int(np.count_nonzero(property_mask))

        equipment_counts[property_mask] = (
            random_generator.integers(
                minimum_int,
                maximum_int + 1,
                size=property_count,
            )
        )

    return equipment_counts
