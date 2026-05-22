"""Tests for the AI analysis service (IADT integration)."""
import json
import pytest
from unittest.mock import patch, MagicMock
from app.services.llm_service import (
    NonRetryableAnalysisError,
    analyze_architecture,
    _parse_iadt_result,
)
from app.models.analysis import AnalysisResult


def test_analyze_architecture_raises_on_empty_s3_key():
    """Should raise NonRetryableAnalysisError for empty s3_key."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture("", "my-bucket")


def test_analyze_architecture_raises_on_none_s3_key():
    """Should raise NonRetryableAnalysisError for None s3_key."""
    with pytest.raises(NonRetryableAnalysisError):
        analyze_architecture(None, "my-bucket")


@patch("app.services.llm_service._poll_dynamodb_for_result")
@patch("app.services.llm_service._copy_to_iadt_bucket")
def test_analyze_architecture_returns_valid_result(mock_copy, mock_poll):
    """Should return a valid AnalysisResult when IADT pipeline completes."""
    mock_copy.return_value = "diagrams/test.png"
    mock_poll.return_value = {
        "elements_detected": {"L": [{"S": "sqs"}, {"S": "s3"}, {"S": "lambda"}]},
        "analysis_report": {"S": json.dumps({
            "analise_componentes": [
                {"servico": "SQS", "caso_uso": "Mensageria assíncrona", "limitacoes": "Limites de cota", "comparativo": "vs Kafka", "pilares_well_architected": ["Confiabilidade"]},
            ],
            "riscos_identificados": [
                {"risco": "Cold starts em Lambda", "impacto": "Latência inicial"},
            ],
            "recomendacoes_melhoria": [
                {"recomendacao": "Usar provisionamento de concorrência", "beneficio": "Menor latência"},
            ],
        })},
        "status": {"S": "completed"},
    }

    result = analyze_architecture("diagrams/test.png", "my-bucket")

    assert isinstance(result, AnalysisResult)
    assert len(result.components) > 0
    assert len(result.risks) > 0
    assert len(result.recommendations) > 0
    mock_copy.assert_called_once_with("diagrams/test.png", "my-bucket")
    mock_poll.assert_called_once_with("diagrams/test.png")


@patch("app.services.llm_service._copy_to_iadt_bucket")
def test_analyze_architecture_raises_on_copy_failure(mock_copy):
    """Should raise NonRetryableAnalysisError if copy to IADT bucket fails."""
    mock_copy.side_effect = Exception("S3 access denied")

    with pytest.raises(NonRetryableAnalysisError, match="Falha ao copiar"):
        analyze_architecture("diagrams/test.png", "my-bucket")


def test_parse_iadt_result_with_full_data():
    """Should correctly parse a complete IADT DynamoDB result."""
    dynamo_item = {
        "elements_detected": {"L": [{"S": "sqs"}, {"S": "s3"}, {"S": "lambda"}]},
        "analysis_report": {"S": json.dumps({
            "analise_componentes": [
                {"servico": "SQS", "caso_uso": "Fila de mensagens"},
                {"servico": "Lambda", "caso_uso": "Serverless compute"},
            ],
            "riscos_identificados": [
                {"risco": "Single point of failure", "impacto": "Indisponibilidade total"},
            ],
            "recomendacoes_melhoria": [
                {"recomendacao": "Adicionar redundância", "beneficio": "Alta disponibilidade"},
            ],
        })},
    }

    result = _parse_iadt_result(dynamo_item)

    assert isinstance(result, AnalysisResult)
    assert "sqs" in result.components
    assert "s3" in result.components
    assert "lambda" in result.components
    assert any("SQS" in c for c in result.components)
    assert len(result.risks) == 1
    assert result.risks[0].type == "Single point of failure"
    assert len(result.recommendations) == 1


def test_parse_iadt_result_with_empty_data():
    """Should handle empty IADT result gracefully."""
    dynamo_item = {
        "elements_detected": {"L": []},
        "analysis_report": {"S": "{}"},
    }

    result = _parse_iadt_result(dynamo_item)

    assert isinstance(result, AnalysisResult)
    assert len(result.components) > 0  # Should have fallback message
    assert len(result.risks) > 0
    assert len(result.recommendations) > 0


def test_parse_iadt_result_with_malformed_json():
    """Should handle malformed JSON in analysis_report."""
    dynamo_item = {
        "elements_detected": {"L": [{"S": "ec2"}]},
        "analysis_report": {"S": "not valid json"},
    }

    result = _parse_iadt_result(dynamo_item)

    assert isinstance(result, AnalysisResult)
    assert "ec2" in result.components
