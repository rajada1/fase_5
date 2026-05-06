"""
AI Analysis Service - SQS Poller

Consome eventos DIAGRAM_PROCESSED da fila SQS.
Recebe referência ao arquivo no S3 (s3Key + s3Bucket).

=== CONTRATO DE INTEGRAÇÃO (para equipe IADT) ===

MENSAGEM RECEBIDA DA FILA:
{
    "diagramId": "uuid",
    "s3Key": "diagrams/uuid.png",
    "s3Bucket": "architecture-diagrams-dev-xxxxx",
    "eventType": "DIAGRAM_PROCESSED",
    "correlationId": "optional-uuid"
}

A equipe IADT deve implementar a lógica em llm_service.analyze_architecture()
para baixar o arquivo do S3, processar (OCR, visão, LLM multimodal, etc.)
e retornar um AnalysisResult.
"""

import asyncio
import boto3
import json
import logging
from app.core.config import settings
from app.services.dedupe_service import release_duplicate_lock, should_skip_duplicate
from app.services.llm_service import NonRetryableAnalysisError, analyze_architecture
from app.services.metrics import (
    messages_deduped_total,
    messages_failed_total,
    messages_processed_total,
    messages_received_total,
)
from app.services.sns_service import publish_analysis_completed_event, publish_analysis_failed_event

logger = logging.getLogger(__name__)


def _log_with_context(level: str, message: str, diagram_id: str | None = None, event_type: str | None = None,
                      queue_url: str | None = None, correlation_id: str | None = None):
    log_func = getattr(logger, level, logger.info)
    log_func(
        message,
        extra={
            "diagramId": diagram_id or "",
            "eventType": event_type or "",
            "queueUrl": queue_url or "",
            "correlationId": correlation_id or "",
            "diagram_id": diagram_id or "",
            "event_type": event_type or "",
            "queue_url": queue_url or "",
            "correlation_id": correlation_id or "",
        },
    )


def get_sqs_client():
    client_args = {"region_name": settings.AWS_REGION}
    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client('sqs', **client_args)


async def start_polling():
    sqs = get_sqs_client()
    queue_url = settings.SQS_QUEUE_URL

    _log_with_context("info", "Iniciando consumo da fila SQS", queue_url=queue_url)

    while True:
        try:
            response = sqs.receive_message(
                QueueUrl=queue_url,
                MaxNumberOfMessages=1,
                WaitTimeSeconds=10
            )

            messages = response.get('Messages', [])
            for message in messages:
                messages_received_total.inc()
                receipt_handle = message['ReceiptHandle']

                # Parse message
                try:
                    body = json.loads(message['Body'])
                    if 'Message' in body:
                        data = json.loads(body['Message'])
                    else:
                        data = body
                except json.JSONDecodeError:
                    messages_failed_total.inc()
                    _log_with_context("warning", "Mensagem inválida. Descartando.", queue_url=queue_url)
                    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                diagram_id = data.get('diagramId')
                s3_key = data.get('s3Key')
                s3_bucket = data.get('s3Bucket', settings.S3_BUCKET_NAME)
                event_type = data.get('eventType')
                correlation_id = data.get('correlationId')

                # Validate required fields
                if not diagram_id or not s3_key:
                    messages_failed_total.inc()
                    _log_with_context(
                        "warning",
                        "Mensagem inválida (diagramId/s3Key ausente).",
                        diagram_id=diagram_id, event_type=event_type,
                        queue_url=queue_url, correlation_id=correlation_id,
                    )
                    if diagram_id:
                        try:
                            publish_analysis_failed_event(
                                diagram_id, "invalid_payload_missing_diagramId_or_s3Key", correlation_id)
                        except Exception as e:
                            _log_with_context("error", f"Falha ao publicar evento de erro: {e}",
                                             diagram_id=diagram_id, correlation_id=correlation_id)
                    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                # Deduplication
                if should_skip_duplicate(diagram_id, settings.DEDUPE_WINDOW_SECONDS):
                    _log_with_context("info", "Ignorando evento duplicado.",
                                     diagram_id=diagram_id, correlation_id=correlation_id)
                    messages_deduped_total.inc()
                    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                _log_with_context("info", "Analisando diagrama.",
                                 diagram_id=diagram_id, event_type=event_type, correlation_id=correlation_id)

                try:
                    # Call analysis (IADT team implements this)
                    analysis_result = analyze_architecture(s3_key, s3_bucket)
                    publish_analysis_completed_event(diagram_id, analysis_result, correlation_id)
                    messages_processed_total.inc()
                    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)

                except NonRetryableAnalysisError as non_retryable_error:
                    messages_failed_total.inc()
                    _log_with_context("error", f"Erro não recuperável: {non_retryable_error}",
                                     diagram_id=diagram_id, correlation_id=correlation_id)
                    try:
                        publish_analysis_failed_event(diagram_id, str(non_retryable_error), correlation_id)
                    except Exception as e:
                        _log_with_context("error", f"Falha ao publicar evento de erro: {e}",
                                         diagram_id=diagram_id, correlation_id=correlation_id)
                    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)

                except Exception as transient_error:
                    messages_failed_total.inc()
                    release_duplicate_lock(diagram_id)
                    _log_with_context("error", f"Erro transitório (retry via SQS): {transient_error}",
                                     diagram_id=diagram_id, correlation_id=correlation_id)

        except Exception as e:
            messages_failed_total.inc()
            _log_with_context("error", f"Erro ao consumir fila SQS: {str(e)}", queue_url=queue_url)

        await asyncio.sleep(1)
