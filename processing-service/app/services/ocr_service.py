import boto3
import logging
from app.core.config import settings

logger = logging.getLogger(__name__)

def process_image(file_path: str) -> str:
    logger.info(f"Starting AWS Textract OCR on {file_path}")
    if file_path.lower().endswith('.pdf'):
        logger.warning(f"A API detect_document_text não suporta PDFs de forma síncrona. Realizando mock de dados para {file_path}.")
        return "MOCK_EXTRACTED_DATA: [API Gateway], [Spring Boot Service], [PostgreSQL Database], [SQS Queue], [React Frontend]"
        
    try:
        # Prevent routing Textract to LocalStack (which lacks Textract in Community Edition)
        endpoint = None if 'localhost' in settings.AWS_ENDPOINT_URL else settings.AWS_ENDPOINT_URL
        
        client = boto3.client(
            'textract',
            region_name=settings.AWS_REGION,
            endpoint_url=endpoint
        )
        
        with open(file_path, 'rb') as document:
            image_bytes = bytearray(document.read())

        response = client.detect_document_text(Document={'Bytes': image_bytes})
        
        extracted_text = []
        for item in response.get("Blocks", []):
            if item["BlockType"] == "LINE":
                extracted_text.append(item["Text"])
                
        result = " | ".join(extracted_text)
        logger.info(f"Textract completed successfully. Extracted length: {len(result)}")
        
        if not result.strip():
            return "Nenhum texto detectado pelo AWS Textract no diagrama de arquitetura."
            
        return result
        
    except Exception as e:
        logger.error(f"AWS Textract OCR failed (likely missing credentials or LocalStack): {str(e)}")
        logger.warning("Falling back to simulated OCR data.")
        return "MOCK_EXTRACTED_DATA: [API Gateway], [Spring Boot Service], [PostgreSQL Database], [SQS Queue], [React Frontend]"
