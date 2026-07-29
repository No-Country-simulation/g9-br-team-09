"""Loading and validation of Joblib classification artifacts."""

from collections.abc import Sequence
from pathlib import Path
from typing import Any

import joblib
import pandas as pd

from app.core.exceptions import InferenceError, ModelLoadError
from app.models.protocols import ProbabilisticClassifier
from app.schemas.prediction import EnergyCategory


EXPECTED_CATEGORIES = frozenset(EnergyCategory)


class ScikitLearnModelAdapter:
    """Adapts a Joblib-loaded classifier without assuming class order."""

    def __init__(self, model: ProbabilisticClassifier) -> None:
        self._model = model
        self._categories = self._validate_model(model)

    @staticmethod
    def _validate_model(model: Any) -> tuple[EnergyCategory, ...]:
        if not callable(getattr(model, "predict_proba", None)):
            raise ModelLoadError("O artefato não fornece predict_proba.")

        classes = getattr(model, "classes_", None)
        if classes is None:
            raise ModelLoadError("O artefato não fornece classes_.")

        try:
            category_order = tuple(EnergyCategory(str(item).upper()) for item in classes)
        except (TypeError, ValueError) as error:
            raise ModelLoadError("O artefato possui classes incompatíveis.") from error

        if len(category_order) != len(EXPECTED_CATEGORIES) or set(category_order) != EXPECTED_CATEGORIES:
            raise ModelLoadError("O artefato não possui todas as categorias esperadas.")

        return category_order

    def predict_probabilities(self, features: pd.DataFrame) -> dict[EnergyCategory, float]:
        try:
            raw_probabilities = self._model.predict_proba(features)
            probabilities: Sequence[Any] = raw_probabilities[0]
        except Exception as error:
            raise InferenceError("O modelo não conseguiu calcular probabilidades.") from error

        if len(probabilities) != len(self._categories):
            raise InferenceError("O modelo retornou uma quantidade inválida de probabilidades.")

        try:
            return {
                category: float(probability)
                for category, probability in zip(self._categories, probabilities, strict=True)
            }
        except (TypeError, ValueError) as error:
            raise InferenceError("O modelo retornou probabilidades inválidas.") from error


class ModelLoader:
    """Loads the official Joblib artifact configured for this runtime."""

    def load(self, model_path: Path) -> ScikitLearnModelAdapter:
        if not model_path.is_file():
            raise ModelLoadError("O artefato configurado não está disponível.")

        try:
            model = joblib.load(model_path)
        except Exception as error:
            raise ModelLoadError("Não foi possível carregar o artefato configurado.") from error

        return ScikitLearnModelAdapter(model)
