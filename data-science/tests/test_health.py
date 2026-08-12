import pytest


@pytest.mark.anyio
async def test_health_returns_exact_ready_payload(client) -> None:
    response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
