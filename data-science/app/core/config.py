"""Runtime configuration for the inference API."""

from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuration supplied by the runtime environment."""

    model_path: Path = Field(validation_alias="MODEL_PATH")
    model_version: str = Field(min_length=1, validation_alias="MODEL_VERSION")

    model_config = SettingsConfigDict(
        env_file=".env.api",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )
