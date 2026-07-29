"""Exceptions that preserve the API boundary from implementation details."""


class ModelLoadError(RuntimeError):
    """Raised when the configured model artifact cannot be used."""


class InferenceError(RuntimeError):
    """Raised when a configured model cannot complete an inference."""
