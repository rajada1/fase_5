"""Tests for the LLM service stub."""
import pytest
from app.services.llm_service import (
    NonRetryableAnalysisError,
    analyze_architecture,
)
from app.models.analysis import AnalysisResult


def test_analyze_architecture_returns_stub_result():
    """Stub should return a valid AnalysisResult with placeholder data."""
    result = analyze_architecture("API Gateway -> Load Balancer -> Database")

    assert isinstance(result, AnalysisResult)
    assert len(result.components) > 0
    assert len(result.risks) > 0
    assert len(result.recommendations) > 0


def test_analyze_architecture_raises_on_empty_input():
    """Should raise NonRetryableAnalysisError for empty/blank input."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture("")

    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture("   ")


def test_analyze_architecture_raises_on_none_input():
    """Should raise NonRetryableAnalysisError for None input."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture(None)


def test_analyze_architecture_handles_long_input():
    """Should truncate long input without error."""
    long_text = "A" * 50000
    result = analyze_architecture(long_text)

    assert isinstance(result, AnalysisResult)


def test_analyze_architecture_stub_response_has_correct_structure():
    """Stub response should match the expected contract structure."""
    result = analyze_architecture("Some architecture diagram text")

    # Verify components is a list of strings
    assert all(isinstance(c, str) for c in result.components)

    # Verify risks have required fields
    for risk in result.risks:
        assert risk.type is not None
        assert risk.description is not None

    # Verify recommendations is a list of strings
    assert all(isinstance(r, str) for r in result.recommendations)
