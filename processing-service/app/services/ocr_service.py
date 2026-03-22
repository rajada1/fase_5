import boto3
import logging
from PIL import Image
import pytesseract
from pdf2image import convert_from_path
from app.core.config import settings

logger = logging.getLogger(__name__)


def _create_textract_client():
    endpoint = None
    if settings.AWS_ENDPOINT_URL and 'localhost' not in settings.AWS_ENDPOINT_URL:
        endpoint = settings.AWS_ENDPOINT_URL

    return boto3.client(
        'textract',
        region_name=settings.AWS_REGION,
        endpoint_url=endpoint
    )


def _extract_with_textract_image(file_path: str) -> str:
    client = _create_textract_client()

    with open(file_path, 'rb') as document:
        image_bytes = bytearray(document.read())

    response = client.detect_document_text(Document={'Bytes': image_bytes})

    extracted_text = []
    for item in response.get("Blocks", []):
        if item["BlockType"] == "LINE":
            extracted_text.append(item["Text"])

    return " | ".join(extracted_text).strip()


def _extract_pdf_with_tesseract(file_path: str) -> str:
    pages = convert_from_path(file_path)
    page_texts = []

    for page in pages:
        text = pytesseract.image_to_string(page).strip()
        if text:
            page_texts.append(text)

    return " | ".join(page_texts).strip()


def _extract_image_with_tesseract(file_path: str) -> str:
    image = Image.open(file_path)
    return pytesseract.image_to_string(image).strip()


def process_image(file_path: str) -> str:
    logger.info(f"Iniciando OCR com AWS Textract em {file_path}")

    if file_path.lower().endswith('.pdf'):
        logger.info(f"Usando pipeline OCR de PDF (pdf2image + pytesseract) para {file_path}")
        result = _extract_pdf_with_tesseract(file_path)

        if not result:
            raise RuntimeError("Nenhum texto detectado no PDF pelo pipeline OCR local.")

        logger.info(f"OCR de PDF concluído com sucesso. Tamanho extraído: {len(result)}")
        return result

    try:
        result = _extract_with_textract_image(file_path)
        if result:
            logger.info(f"Textract concluído com sucesso. Tamanho extraído: {len(result)}")
            return result

        logger.warning("Textract retornou conteúdo vazio. Aplicando fallback para pytesseract.")
    except Exception as textract_error:
        logger.warning(f"Textract indisponível ou com falha. Aplicando fallback para pytesseract. Erro: {textract_error}")

    fallback_result = _extract_image_with_tesseract(file_path)
    if fallback_result:
        logger.info(f"Fallback com Tesseract concluído com sucesso. Tamanho extraído: {len(fallback_result)}")
        return fallback_result

    raise RuntimeError("Nenhum texto detectado pelo OCR (Textract e fallback local).")
