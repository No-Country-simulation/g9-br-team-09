"""FastAPI application factory and lifecycle configuration."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.core.config import Settings
from app.core.exceptions import InferenceError
from app.models.model_loader import ModelLoader
from app.services.inference_service import InferenceService


logger = logging.getLogger(__name__)


def create_app(
    *,
    settings: Settings | None = None,
    model_loader: ModelLoader | None = None,
    inference_service: InferenceService | None = None,
) -> FastAPI:
    """Create an API whose model is initialized once during application startup."""

    loader = model_loader or ModelLoader()

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        if inference_service is None:
            active_settings = settings or Settings()
            model = loader.load(active_settings.model_path)
            application.state.inference_service = InferenceService(
                model=model,
                model_version=active_settings.model_version,
            )
        else:
            application.state.inference_service = inference_service
        yield

    application = FastAPI(
        title="EnergiAI Energy Inference API",
        description="Internal model inference API consumed exclusively by the Spring Boot backend.",
        version="1.0.0",
        lifespan=lifespan,
    )
    application.include_router(router)

    @application.exception_handler(InferenceError)
    async def inference_error_handler(_: Request, error: InferenceError) -> JSONResponse:
        logger.error("Inference failed: %s", error)
        return JSONResponse(
            status_code=500,
            content={"detail": "Não foi possível executar a inferência."},
        )

    return application


app = create_app()
