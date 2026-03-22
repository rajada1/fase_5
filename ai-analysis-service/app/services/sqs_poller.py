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
    diagram_id_value = diagram_id or ""
    event_type_value = event_type or ""
    queue_url_value = queue_url or ""
    correlation_id_value = correlation_id or ""
    log_func(
        message,
        extra={
            "diagramId": diagram_id_value,
            "eventType": event_type_value,
            "queueUrl": queue_url_value,
            "correlationId": correlation_id_value,
            "diagram_id": diagram_id_value,
            "event_type": event_type_value,
            "queue_url": queue_url_value,
            "correlation_id": correlation_id_value,
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
            response = sqs.receive_message(
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
                        "Mensagem inválida recebida na fila de análise. Descartando payload malformado.",
                        queue_url=queue_url,
                    )
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                diagram_id = data.get('diagramId')
                extracted_data = data.get('extractedData')
                event_type = data.get('eventType')
                correlation_id = data.get('correlationId')

                if not diagram_id or not extracted_data:
                    messages_failed_total.inc()
                    _log_with_context(
                        "warning",
                        "Mensagem inválida na fila de análise (diagramId/extractedData ausente).",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    if diagram_id:
                        try:
                            publish_analysis_failed_event(
                                diagram_id,
                                "invalid_payload_missing_diagramId_or_extractedData",
                                correlation_id,
                            )
                        except Exception as publish_error:
                            _log_with_context(
                                "error",
                                f"Falha ao publicar evento de erro de análise: {publish_error}",
                                diagram_id=diagram_id,
                                event_type=event_type,
                                queue_url=queue_url,
                                correlation_id=correlation_id,
                            )
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                if should_skip_duplicate(diagram_id, settings.DEDUPE_WINDOW_SECONDS):
                    _log_with_context(
                        "info",
                        "Ignorando evento duplicado de análise para diagrama.",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    messages_deduped_total.inc()
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                    continue

                _log_with_context(
                    "info",
                    "Analisando diagrama.",
                    diagram_id=diagram_id,
                    event_type=event_type,
                    queue_url=queue_url,
                    correlation_id=correlation_id,
                )

                try:
                    analysis_result = analyze_architecture(extracted_data)
                    publish_analysis_completed_event(diagram_id, analysis_result, correlation_id)
                    messages_processed_total.inc()
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                except NonRetryableAnalysisError as non_retryable_error:
                    messages_failed_total.inc()
                    _log_with_context(
                        "error",
                        f"Erro não recuperável na análise do diagrama: {non_retryable_error}",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
                    try:
                        publish_analysis_failed_event(diagram_id, str(non_retryable_error), correlation_id)
                    except Exception as publish_error:
                        _log_with_context(
                            "error",
                            f"Falha ao publicar evento de erro de análise: {publish_error}",
                            diagram_id=diagram_id,
                            event_type=event_type,
                            queue_url=queue_url,
                            correlation_id=correlation_id,
                        )
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
                except Exception as transient_error:
                    messages_failed_total.inc()
                    release_duplicate_lock(diagram_id)
                    _log_with_context(
                        "error",
                        f"Erro transitório na análise (retry via SQS): {transient_error}",
                        diagram_id=diagram_id,
                        event_type=event_type,
                        queue_url=queue_url,
                        correlation_id=correlation_id,
                    )
        except Exception as e:
            messages_failed_total.inc()
            _log_with_context("error", f"Erro ao consumir fila SQS: {str(e)}", queue_url=queue_url)
        
        await asyncio.sleep(1)
