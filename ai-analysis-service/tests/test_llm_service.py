"""Tests for the AI analysis service stub."""
import pytest
from app.services.llm_service import (
    NonRetryableAnalysisError,
    analyze_architecture,
)
from app.models.analysis import AnalysisResult


def test_analyze_architecture_returns_stub_result():
    """Stub should return a valid AnalysisResult with placeholder data."""
    result = analyze_architecture("diagrams/test.png", "my-bucket")

    assert isinstance(result, AnalysisResult)
    assert len(result.components) > 0
    assert len(result.risks) > 0
    assert len(result.recommendations) > 0


def test_analyze_architecture_raises_on_empty_s3_key():
    """Should raise NonRetryableAnalysisError for empty s3_key."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture("", "my-bucket")


def test_analyze_architecture_raises_on_none_s3_key():
    """Should raise NonRetryableAnalysisError for None s3_key."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture(None, "my-bucket")


def test_analyze_architecture_stub_response_has_correct_structure():
    """Stub response should match the expected contract structure."""
    result = analyze_architecture("diagrams/arch.pdf", "bucket-name")

    assert all(isinstance(c, str) for c in result.components)
    for risk in result.risks:
        assert risk.type is not None
        assert risk.description is not None
    assert all(isinstance(r, str) for r in result.recommendations)
