"""
Processing Service - SQS Poller

Responsabilidade SOAT: Orquestrar o fluxo entre upload e análise de IA.
- Consome eventos FILE_UPLOADED da fila SQS
- Valida que o arquivo existe no S3
- Encaminha para o AI Analysis Service via SNS (com s3Key e bucket)

A extração de informações e análise é responsabilidade da equipe IADT
no ai-analysis-service.
"""

import asyncio
import boto3
import json
import logging
import os
from app.core.config import settings
from app.services.dedupe_service import should_skip_duplicate
from app.services.metrics import (
    messages_deduped_total,
    messages_failed_total,
    messages_processed_total,
    messages_received_total,
)
from app.services.s3_service import check_file_exists
from app.services.sns_service import publish_diagram_processed_event, publish_processing_failed_event

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
            response = await asyncio.to_thread(
                sqs.receive_message,
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
                    _log_with_context(
                        "warning",
                        "Mensagem inválida recebida. Descartando payload malformado.",
                        queue_url=queue_url,
                    )
                    await asyncio.to_thread(sqs.delete_message, QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                diagram_id = data.get('diagramId')
                s3_key = data.get('s3Key')
                event_type = data.get('eventType')
                correlation_id = data.get('correlationId')

                # Validate required fields
                if not diagram_id or not s3_key:
                    messages_failed_total.inc()
                    _log_with_context(
                        "warning",
                        "Mensagem inválida (diagramId/s3Key ausente).",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    if diagram_id:
                        try:
                            await asyncio.to_thread(
                                publish_processing_failed_event, diagram_id,
                                "invalid_payload_missing_diagramId_or_s3Key", correlation_id,
                            )
                        except Exception as e:
                            _log_with_context("error", f"Falha ao publicar evento de erro: {e}",
                                             diagram_id=diagram_id, correlation_id=correlation_id)
                    await asyncio.to_thread(sqs.delete_message, QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                # Deduplication
                if should_skip_duplicate(diagram_id, settings.DEDUPE_WINDOW_SECONDS):
                    _log_with_context("info", "Ignorando evento duplicado.",
                                     diagram_id=diagram_id, event_type=event_type, correlation_id=correlation_id)
                    messages_deduped_total.inc()
                    await asyncio.to_thread(sqs.delete_message, QueueUrl=queue_url, ReceiptHandle=receipt_handle)
                    continue

                _log_with_context("info", "Processando diagrama.",
                                 diagram_id=diagram_id, event_type=event_type, correlation_id=correlation_id)

                try:
                    # Validate file exists in S3
                    file_exists = await asyncio.to_thread(check_file_exists, s3_key)
                    if not file_exists:
                        raise RuntimeError(f"Arquivo não encontrado no S3: {s3_key}")

                    # Forward to AI Analysis Service with S3 reference
                    await asyncio.to_thread(
                        publish_diagram_processed_event,
                        diagram_id, s3_key, correlation_id,
                    )

                    messages_processed_total.inc()
                    _log_with_context("info", "Diagrama encaminhado para análise de IA.",
                                     diagram_id=diagram_id, correlation_id=correlation_id)

                    await asyncio.to_thread(sqs.delete_message, QueueUrl=queue_url, ReceiptHandle=receipt_handle)

                except Exception as processing_error:
                    messages_failed_total.inc()
                    _log_with_context(
                        "error", f"Falha no processamento: {processing_error}",
                        diagram_id=diagram_id, event_type=event_type, correlation_id=correlation_id,
                    )
                    try:
                        await asyncio.to_thread(
                            publish_processing_failed_event, diagram_id,
                            str(processing_error), correlation_id,
                        )
                    except Exception as e:
                        _log_with_context("error", f"Falha ao publicar evento de erro: {e}",
                                         diagram_id=diagram_id, correlation_id=correlation_id)
                    await asyncio.to_thread(sqs.delete_message, QueueUrl=queue_url, ReceiptHandle=receipt_handle)

        except Exception as e:
            messages_failed_total.inc()
            _log_with_context("error", f"Erro ao consumir fila SQS: {str(e)}", queue_url=queue_url)

        await asyncio.sleep(1)
