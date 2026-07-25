"""Public schemas for the internal Spring Boot to FastAPI contract."""

from enum import StrEnum
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, StringConstraints, field_validator


class EnergyCategory(StrEnum):
    EFICIENTE = "EFICIENTE"
    MODERADO = "MODERADO"
    INEFICIENTE = "INEFICIENTE"


class PropertyType(StrEnum):
    CASA = "CASA"
    APARTAMENTO = "APARTAMENTO"
    COMERCIO = "COMERCIO"
    ESCRITORIO = "ESCRITORIO"
    INDUSTRIA = "INDUSTRIA"
    OUTRO = "OUTRO"


NonEmptyText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class PredictionRequest(BaseModel):
    """Five production features accepted from the backend only."""

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "examples": [
                {
                    "consumo_kwh": 420.0,
                    "uso_horario_pico": True,
                    "quantidade_equipamentos": 10,
                    "tipo_imovel": "CASA",
                    "horas_alto_consumo": 8,
                }
            ]
        },
    )

    consumo_kwh: Annotated[float, Field(gt=0, allow_inf_nan=False)]
    uso_horario_pico: StrictBool
    quantidade_equipamentos: Annotated[StrictInt, Field(ge=1)]
    tipo_imovel: PropertyType
    horas_alto_consumo: Annotated[StrictInt, Field(ge=0, le=24)]


class PredictionResponse(BaseModel):
    """Prediction payload compatible with MlPredictionResponse."""

    categoria: EnergyCategory
    probabilidade: Annotated[float, Field(ge=0, le=1, allow_inf_nan=False)]
    score: Annotated[int, Field(ge=0, le=100)]
    recomendacoes: Annotated[list[NonEmptyText], Field(min_length=1)]
    modelo_versao: NonEmptyText

    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "categoria": "INEFICIENTE",
                    "probabilidade": 0.81,
                    "score": 81,
                    "recomendacoes": ["Reduzir o uso de equipamentos durante horários de pico."],
                    "modelo_versao": "energy-classifier-v2",
                }
            ]
        }
    )

    @field_validator("recomendacoes")
    @classmethod
    def recommendations_must_be_unique(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("As recomendações não podem ser duplicadas.")
        return value


class HealthResponse(BaseModel):
    """Minimal readiness response without operational details."""

    status: str = "UP"
