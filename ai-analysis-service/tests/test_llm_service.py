import pytest
from app.services.llm_service import analyze_architecture
from app.models.analysis import AnalysisResult
import json

def test_analyze_architecture_success(mocker):
    # Business Rule: Parse OpenAI's JSON response accurately
    mock_openai = mocker.patch("app.services.llm_service.OpenAI")
    mock_client = mock_openai.return_value
    
    mock_choice = mocker.Mock()
    mock_choice.message.content = json.dumps({
        "components": ["API Gateway"],
        "risks": [{"type": "Security", "description": "No auth"}],
        "recommendations": ["Add Cognito"]
    })
    
    mock_response = mocker.Mock()
    mock_response.choices = [mock_choice]
    mock_client.chat.completions.create.return_value = mock_response
    
    result = analyze_architecture("MOCK_EXTRACTED_DATA")
    
    assert isinstance(result, AnalysisResult)
    assert len(result.components) == 1
    assert result.components[0] == "API Gateway"
    assert len(result.risks) == 1
    assert result.risks[0].type == "Security"
    
def test_analyze_architecture_fallback_on_failure(mocker):
    # Business Rule: Provide a resilient fallback with CRITICAL risk if OpenAI API fails
    mock_openai = mocker.patch("app.services.llm_service.OpenAI")
    mock_client = mock_openai.return_value
    mock_client.chat.completions.create.side_effect = Exception("API Timeout")
    
    result = analyze_architecture("MOCK_EXTRACTED_DATA")
    
    assert isinstance(result, AnalysisResult)
    assert len(result.risks) == 1
    assert result.risks[0].severity == "CRITICAL"
    assert "Falha na integração com OpenAI" in result.risks[0].description
