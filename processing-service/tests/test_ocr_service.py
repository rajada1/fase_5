import pytest
from app.services.ocr_service import process_image

MOCK_DATA = "MOCK_EXTRACTED_DATA: [API Gateway], [Spring Boot Service], [PostgreSQL Database], [SQS Queue], [React Frontend]"

def test_process_image_pdf_fallback(mocker):
    # Business Rule: PDFs should skip Textract and return mock data immediately
    # We mock boto3 to ensure it is NOT called
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    
    result = process_image("architecture_diagram.pdf")
    
    assert result == MOCK_DATA
    mock_boto.assert_not_called()

def test_process_image_png_success(mocker):
    # Business Rule: PNGs should call Textract
    # Mock boto3 and the open built-in
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    mock_client_instance = mock_boto.return_value
    mock_client_instance.detect_document_text.return_value = {
        "Blocks": [{"BlockType": "LINE", "Text": "Microservice A"}, {"BlockType": "LINE", "Text": "Database B"}]
    }
    
    mocker.patch("builtins.open", mocker.mock_open(read_data=b"dummybytes"))
    
    result = process_image("architecture_diagram.png")
    
    assert result == "Microservice A | Database B"
    mock_client_instance.detect_document_text.assert_called_once()
