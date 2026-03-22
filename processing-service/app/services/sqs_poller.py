import asyncio
import boto3
import json
import logging
import os
import tempfile
from app.core.config import settings
from app.services.dedupe_service import should_skip_duplicate
from app.services.metrics import (
    messages_deduped_total,
    messages_failed_total,
    messages_processed_total,
    messages_received_total,
)
from app.services.s3_service import download_file
from app.services.ocr_service import process_image
from app.services.sns_service import publish_diagram_processed_event, publish_processing_failed_event

logger = logging.getLogger(__name__)


def _log_with_context(level: str, message: str, diagram_id: str | None = None, event_type: str | None = None,
                      queue_url: str | None = None, correlation_id: str | None = None):
    log_func = getattr(logger, level, logger.info)
    log_func(
        message,
        extra={
            "diagram_id": diagram_id or "",
            "event_type": event_type or "",
            "queue_url": queue_url or "",
            "correlation_id": correlation_id or "",
        },
    )

def get_sqs_client():
    client_args = {
        "region_name": settings.AWS_REGION,
    }

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
                        "Mensagem inválida recebida na fila de processamento. Descartando payload malformado.",
                        queue_url=queue_url,
                    )
                    await asyncio.to_thread(
                        sqs.delete_message,
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                diagram_id = data.get('diagramId')
                s3_key = data.get('s3Key')
                event_type = data.get('eventType')
                correlation_id = data.get('correlationId')

                if not diagram_id or not s3_key:
                    messages_failed_total.inc()
                    _log_with_context(
                        "warning",
                        "Mensagem inválida na fila de processamento (diagramId/s3Key ausente).",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    if diagram_id:
                        try:
                            await asyncio.to_thread(
                                publish_processing_failed_event,
                                diagram_id,
                                "invalid_payload_missing_diagramId_or_s3Key",
                                correlation_id,
                            )
                        except Exception as publish_error:
                            _log_with_context(
                                "error",
                                f"Falha ao publicar evento de erro de processamento: {publish_error}",
                                diagram_id=diagram_id,
                                event_type=event_type,
                                queue_url=queue_url,
                                correlation_id=correlation_id,
                            )
                    await asyncio.to_thread(
                        sqs.delete_message,
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                if should_skip_duplicate(diagram_id, settings.DEDUPE_WINDOW_SECONDS):
                    _log_with_context(
                        "info",
                        "Ignorando evento duplicado de processamento para diagrama.",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    messages_deduped_total.inc()
                    await asyncio.to_thread(
                        sqs.delete_message,
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                _log_with_context(
                    "info",
                    "Processando diagrama.",
                    diagram_id=diagram_id,
                    event_type=event_type,
                    queue_url=queue_url,
                    correlation_id=correlation_id,
                )
                local_path = os.path.join(tempfile.gettempdir(), f"{diagram_id}.bin")

                try:
                    await asyncio.to_thread(download_file, s3_key, local_path)
                    extracted_data = await asyncio.to_thread(process_image, local_path)
                    await asyncio.to_thread(publish_diagram_processed_event, diagram_id, extracted_data, correlation_id)

                    messages_processed_total.inc()

                    await asyncio.to_thread(
                        sqs.delete_message,
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                except Exception as processing_error:
                    messages_failed_total.inc()
                    _log_with_context(
                        "error",
                        f"Falha não recuperável no processamento do diagrama: {processing_error}",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    try:
                        await asyncio.to_thread(
                            publish_processing_failed_event,
                            diagram_id,
                            str(processing_error),
                            correlation_id,
                        )
                    except Exception as publish_error:
                        _log_with_context(
                            "error",
                            f"Falha ao publicar evento de erro de processamento: {publish_error}",
                            diagram_id=diagram_id,
                            event_type=event_type,
                            queue_url=queue_url,
                            correlation_id=correlation_id,
                        )
                    await asyncio.to_thread(
                        sqs.delete_message,
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                finally:
                    if os.path.exists(local_path):
                        os.remove(local_path)
        except Exception as e:
            messages_failed_total.inc()
            _log_with_context("error", f"Erro ao consumir fila SQS: {str(e)}", queue_url=queue_url)
        
        await asyncio.sleep(1)
