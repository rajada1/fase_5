import boto3
import json
from app.core.config import settings


def get_sns_client():
    client_args = {"region_name": settings.AWS_REGION}
    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client('sns', **client_args)


def publish_diagram_processed_event(diagram_id: str, s3_key: str, correlation_id: str | None = None):
    """
    Publica evento DIAGRAM_PROCESSED com referência ao arquivo no S3.
    O ai-analysis-service consome este evento e decide como processar o arquivo.
    """
    sns = get_sns_client()

    payload = {
        "diagramId": diagram_id,
        "s3Key": s3_key,
        "s3Bucket": settings.S3_BUCKET_NAME,
        "eventType": "DIAGRAM_PROCESSED",
    }
    if correlation_id:
        payload["correlationId"] = correlation_id

    sns.publish(TopicArn=settings.SNS_TOPIC_ARN, Message=json.dumps(payload))


def publish_processing_failed_event(diagram_id: str, reason: str, correlation_id: str | None = None):
    sns = get_sns_client()

    payload = {
        "diagramId": diagram_id,
        "reason": (reason or "processing_failed")[:500],
        "eventType": "PROCESSING_FAILED",
    }
    if correlation_id:
        payload["correlationId"] = correlation_id

    sns.publish(TopicArn=settings.SNS_TOPIC_ARN, Message=json.dumps(payload))
