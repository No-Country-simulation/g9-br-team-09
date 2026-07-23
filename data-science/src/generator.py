"""Gerador sintético do Dataset EnergIAI V2.

Nesta etapa inicial, o módulo implementa somente a alocação reproduzível
dos tipos de imóvel. As demais features, score, target e casos especiais
serão adicionados e validados em etapas posteriores.
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
