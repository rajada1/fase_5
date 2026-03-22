from app.services.ocr_service import process_image


def test_process_image_pdf_uses_local_ocr_pipeline(mocker):
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    mocker.patch("app.services.ocr_service.convert_from_path", return_value=[object(), object()])
    mock_image_to_string = mocker.patch("app.services.ocr_service.pytesseract.image_to_string")
    mock_image_to_string.side_effect = ["API Gateway", "PostgreSQL"]

    result = process_image("architecture_diagram.pdf")

    assert result == "API Gateway | PostgreSQL"
    mock_boto.assert_not_called()


def test_process_image_png_success(mocker):
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    mock_client_instance = mock_boto.return_value
    mock_client_instance.detect_document_text.return_value = {
        "Blocks": [{"BlockType": "LINE", "Text": "Microservice A"}, {"BlockType": "LINE", "Text": "Database B"}]
    }

    mocker.patch("builtins.open", mocker.mock_open(read_data=b"dummybytes"))

    result = process_image("architecture_diagram.png")

    assert result == "Microservice A | Database B"
    mock_client_instance.detect_document_text.assert_called_once()


def test_process_image_png_falls_back_to_tesseract_when_textract_fails(mocker):
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    mock_client_instance = mock_boto.return_value
    mock_client_instance.detect_document_text.side_effect = Exception("Textract unavailable")

    mocker.patch("builtins.open", mocker.mock_open(read_data=b"dummybytes"))
    mock_image_open = mocker.patch("app.services.ocr_service.Image.open")
    mock_image_open.return_value = object()
    mocker.patch("app.services.ocr_service.pytesseract.image_to_string", return_value="Fallback OCR text")

    result = process_image("architecture_diagram.png")

    assert result == "Fallback OCR text"


def test_process_image_png_unreadable_image_should_raise_runtime_error(mocker):
    # Simula o Textract falhando ou não encontrando blocos
    mock_boto = mocker.patch("app.services.ocr_service.boto3.client")
    mock_client_instance = mock_boto.return_value
    mock_client_instance.detect_document_text.return_value = {"Blocks": []}

    mocker.patch("builtins.open", mocker.mock_open(read_data=b"blackimagebytes"))
    
    # Simula o PyTesseract também retornando vazio (imagem preta)
    mocker.patch("app.services.ocr_service.Image.open", return_value=object())
    mocker.patch("app.services.ocr_service.pytesseract.image_to_string", return_value="")
    mocker.patch("app.services.ocr_service.settings.AWS_ENDPOINT_URL", "")

    import pytest
    with pytest.raises(RuntimeError, match="Nenhum texto detectado"):
        process_image("unreadable_diagram.png")
