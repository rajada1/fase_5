from fastapi.testclient import TestClient

from app.main import app
from app.services import dedupe_service


def test_health_should_return_dedupe_stats_snapshot():
    dedupe_service.reset_stats()
    dedupe_service.should_skip_duplicate("diag-health", 60)

    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "ai-analysis-service"
    assert "dedupeStats" in data
    assert data["dedupeStats"]["total"] >= 1


def test_metrics_should_return_prometheus_payload():
    client = TestClient(app)
    response = client.get("/metrics")

    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]
    assert "ai_analysis_messages_received_total" in response.text
