from fastapi import APIRouter, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from app.services.dedupe_service import get_stats

router = APIRouter()

@router.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "ai-analysis-service",
        "dedupeStats": get_stats(),
    }


@router.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
