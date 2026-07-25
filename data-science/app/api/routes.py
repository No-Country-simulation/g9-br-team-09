"""FastAPI routes for the internal inference contract."""

from typing import cast

from fastapi import APIRouter, Request, status

from app.schemas.prediction import HealthResponse, PredictionRequest, PredictionResponse
from app.services.inference_service import InferenceService


router = APIRouter(tags=["Inference"])


def _inference_service(request: Request) -> InferenceService:
    return cast(InferenceService, request.app.state.inference_service)


@router.get("/health", response_model=HealthResponse, status_code=status.HTTP_200_OK)
def health(request: Request) -> HealthResponse:
    """Return readiness only after the inference service has been configured."""

    _inference_service(request)
    return HealthResponse()


@router.post(
    "/predict",
    response_model=PredictionResponse,
    status_code=status.HTTP_200_OK,
    summary="Execute energy model inference",
)
def predict(request_body: PredictionRequest, request: Request) -> PredictionResponse:
    """Predict category, confidence, severity score and recommendations."""

    return _inference_service(request).predict(request_body)
