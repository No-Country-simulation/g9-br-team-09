"""Protocols required from a serialized classification model."""

from collections.abc import Sequence
from typing import Any, Protocol

import pandas as pd


class ProbabilisticClassifier(Protocol):
    """Minimal contract supported by the inference adapter."""

    classes_: Sequence[Any]

    def predict_proba(self, features: pd.DataFrame) -> Any:
        """Return one probability vector for each feature record."""
