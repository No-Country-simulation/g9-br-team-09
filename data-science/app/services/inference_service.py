"""Inference orchestration independent from HTTP and artifact loading."""

import math

import pandas as pd

from app.core.exceptions import InferenceError
from app.models.model_loader import ScikitLearnModelAdapter
from app.schemas.prediction import EnergyCategory, PredictionRequest, PredictionResponse
from app.services.recommendation_service import RecommendationService


FEATURE_COLUMNS = (
    "consumo_kwh",
    "uso_horario_pico",
    "quantidade_equipamentos",
    "tipo_imovel",
    "horas_alto_consumo",
)
ENERGY_CATEGORY_ORDER = (
    EnergyCategory.EFICIENTE,
    EnergyCategory.MODERADO,
    EnergyCategory.INEFICIENTE,
)
SEVERITY_WEIGHTS = {
    EnergyCategory.EFICIENTE: 0,
    EnergyCategory.MODERADO: 50,
    EnergyCategory.INEFICIENTE: 100,
}
PROBABILITY_SUM_TOLERANCE = 1e-6


class InferenceService:
    """Produces a prediction from a loaded classifier and business-safe rules."""

    def __init__(
        self,
        model: ScikitLearnModelAdapter,
        model_version: str,
        recommendation_service: RecommendationService | None = None,
    ) -> None:
        self._model = model
        self._model_version = model_version
        self._recommendation_service = recommendation_service or RecommendationService()

    def predict(self, request: PredictionRequest) -> PredictionResponse:
        features = pd.DataFrame(
            [[
                request.consumo_kwh,
                request.uso_horario_pico,
                request.quantidade_equipamentos,
                request.tipo_imovel.value,
                request.horas_alto_consumo,
            ]],
            columns=FEATURE_COLUMNS,
        )
        probabilities = self._model.predict_probabilities(features)
        self._validate_probabilities(probabilities)

        category = max(ENERGY_CATEGORY_ORDER, key=probabilities.__getitem__)
        probability = probabilities[category]
        score = int(round(sum(SEVERITY_WEIGHTS[item] * probabilities[item] for item in ENERGY_CATEGORY_ORDER)))
        recommendations = self._recommendation_service.recommend(request, category, score)

        return PredictionResponse(
            categoria=category,
            probabilidade=probability,
            score=score,
            recomendacoes=recommendations,
            modelo_versao=self._model_version,
        )

    @staticmethod
    def _validate_probabilities(probabilities: dict[EnergyCategory, float]) -> None:
        if set(probabilities) != set(ENERGY_CATEGORY_ORDER):
            raise InferenceError("O modelo não retornou todas as probabilidades esperadas.")
        if any(not math.isfinite(value) or not 0 <= value <= 1 for value in probabilities.values()):
            raise InferenceError("O modelo retornou probabilidades fora do intervalo permitido.")
        if not math.isclose(
            math.fsum(probabilities.values()),
            1.0,
            rel_tol=0.0,
            abs_tol=PROBABILITY_SUM_TOLERANCE,
        ):
            raise InferenceError("O modelo retornou probabilidades com soma inválida.")
